package com.hilotspa.backend.services;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.hilotspa.backend.config.ReplicationContext;
import com.hilotspa.backend.entities.PeerNode;
import com.hilotspa.backend.model.SyncDtos.AppointmentSnapshot;
import com.hilotspa.backend.model.SyncDtos.Change;
import com.hilotspa.backend.repository.AppointmentRepository;
import com.hilotspa.backend.repository.BranchRepository;
import com.hilotspa.backend.repository.MassageRepository;
import com.hilotspa.backend.repository.PeerNodeRepository;
import com.hilotspa.backend.repository.RoomRepository;
import com.hilotspa.backend.repository.TherapistRepository;

/**
 * Stage 3 - pull each peer's log and apply what it says (task 3.3).
 *
 * WHY THERE IS NO CONFLICT RESOLUTION, and why that is not a shortcut:
 * a therapist and a room belong to exactly one branch, and a node writes only
 * for the branch it owns. Two nodes therefore never write the same row, so
 * there is no pair of versions to reconcile and no clock to order them by. The
 * property is enforced in three places already - the data model, createForm,
 * and the reminder clamp - which is what earns the right to leave this simple.
 *
 * WHAT CROSSES: appointments. Therapists, rooms, the menu and the protocol
 * table are on the sync_log allow-list too, but they are seeded identically on
 * every node and change through configuration rather than through the desk;
 * their rows are skipped here, counted, and left for task 3.8. Skipping is
 * recorded rather than silent - "applied 2, skipped 1" is a sentence somebody
 * can act on, "applied 2" is not.
 *
 * WHAT NEVER CROSSES: the client's account, their assessment, their pain map,
 * their contact number. Those are not in sync_log at all, so this class could
 * not fetch them if it tried.
 */
@Service
public class PeerPuller {

    private static final Logger LOG = LoggerFactory.getLogger(PeerPuller.class);
    private static final int BATCH = 200;

    @Autowired private PeerNodeRepository peerNodeRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private MassageRepository massageRepository;
    @Autowired private TherapistRepository therapistRepository;
    @Autowired private RoomRepository roomRepository;

    @Value("${hilotspa.sync.token:}")  private String token;
    @Value("${hilotspa.sync.enabled:true}") private boolean enabled;

    private RestClient client;

    private RestClient client() {
        if (client == null) {
            SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
            f.setConnectTimeout(Duration.ofSeconds(3));
            // Longer than the registry's 3s: this one may be transferring a
            // couple of hundred rows, and a catch-up after an outage is exactly
            // when you least want the request cut off.
            f.setReadTimeout(Duration.ofSeconds(15));
            client = RestClient.builder().requestFactory(f).build();
        }
        return client;
    }

    /**
     * Deliberately offset from the registry poll rather than sharing it.
     *
     * The registry answers "is it there"; this answers "what did it do". Keeping
     * them apart means a peer that is up but slow to serve its log cannot make
     * itself look unreachable.
     */
    @Scheduled(fixedDelayString = "${hilotspa.sync.pull-ms:30000}",
               initialDelayString = "${hilotspa.sync.pull-delay-ms:20000}")
    public void pullAll() {
        if (!enabled || token == null || token.isBlank()) {
            return;
        }
        for (PeerNode p : peerNodeRepository.findBySelfFalse()) {
            if (!"ONLINE".equals(p.getState()) || p.getBaseUrl() == null) {
                continue;
            }
            if (p.getTheirWatermark() != null && p.getTheirWatermark() <= p.getOurWatermark()) {
                continue;   // nothing new; do not spend a request saying so
            }
            try {
                pullOne(p);
            } catch (Exception e) {
                // Never let one peer's failure stop the others, and never let it
                // move the watermark - an unread change must stay unread.
                LOG.warn("pull from {} failed: {}", p.getBaseUrl(), e.toString());
            }
        }
    }

    private void pullOne(PeerNode p) {
        List<Change> changes = client().get()
                .uri(p.getBaseUrl() + "/api/v1/sync/changes?since=" + p.getOurWatermark()
                     + "&limit=" + BATCH)
                .header("X-Sync-Token", token)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Change>>() { });

        if (changes == null || changes.isEmpty()) {
            return;
        }

        List<UUID> wanted = changes.stream()
                .filter(c -> "Appointment".equals(c.entityType()))
                .filter(c -> "UPSERT".equals(c.action()))
                .map(Change::entityId)
                .distinct()
                .toList();

        List<AppointmentSnapshot> snaps = List.of();
        if (!wanted.isEmpty()) {
            String ids = wanted.stream().map(UUID::toString).collect(Collectors.joining(","));
            List<AppointmentSnapshot> got = client().get()
                    .uri(p.getBaseUrl() + "/api/v1/sync/appointments?ids=" + ids)
                    .header("X-Sync-Token", token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<AppointmentSnapshot>>() { });
            if (got != null) {
                snaps = got;
            }
        }

        List<UUID> deletions = changes.stream()
                .filter(c -> "Appointment".equals(c.entityType()))
                .filter(c -> "DELETE".equals(c.action()))
                .map(Change::entityId)
                .toList();

        int applied = 0;
        int skipped = 0;
        List<String> reasons = new ArrayList<>();

        for (AppointmentSnapshot s : snaps) {
            String why = apply(s);
            if (why == null) {
                applied++;
            } else {
                skipped++;
                if (reasons.size() < 3) {
                    reasons.add(why);
                }
            }
        }
        for (UUID gone : deletions) {
            appointmentRepository.findById(gone).ifPresent(
                    a -> ReplicationContext.run(() -> appointmentRepository.delete(a)));
            applied++;
        }

        long high = changes.stream().mapToLong(Change::id).max().orElse(p.getOurWatermark());
        int other = (int) changes.stream()
                .filter(c -> !"Appointment".equals(c.entityType())).count();

        // The watermark advances past rows we chose not to carry as well as
        // those we did. They are recorded in the count, not lost silently - a
        // watermark that stalls on an entity type this stage does not handle
        // would block every later change behind it for ever.
        p.setOurWatermark(high);
        peerNodeRepository.save(p);

        LOG.info("pulled {} change(s) from {}: {} applied, {} skipped, {} not carried yet{}",
                changes.size(), p.getNodeId(), applied, skipped, other,
                reasons.isEmpty() ? "" : " - " + String.join("; ", reasons));
    }

    /**
     * Copy one appointment in. Returns null on success, or the reason to skip.
     *
     * A missing therapist, room or service is a real possibility rather than a
     * theoretical one: the peer may have added staff this node has not been
     * told about yet, because that is task 3.8 and it is not built. Skipping
     * with a reason is right - inventing the missing row would put a therapist
     * into this database that nobody here has ever employed.
     */
    private String apply(AppointmentSnapshot s) {
        if (!branchRepository.existsById(s.branchId()))       { return "branch unknown here"; }
        if (!massageRepository.existsById(s.serviceId()))     { return "service " + s.serviceId() + " unknown here"; }
        if (!therapistRepository.existsById(s.therapistId())) { return "therapist " + s.therapistId() + " unknown here"; }
        if (!roomRepository.existsById(s.roomId()))           { return "room " + s.roomId() + " unknown here"; }

        try {
            appointmentRepository.upsertReplicated(
                    s.id(), s.branchId(), s.serviceId(), s.therapistId(), s.roomId(),
                    s.clientName() == null || s.clientName().isBlank() ? "Client" : s.clientName(),
                    s.startTime(), s.endTime(),
                    s.status(), s.paymentStatus(), s.source(),
                    s.priceAtBooking(), s.notes(),
                    // Kept as the ORIGINATING node, never rewritten to ours. It
                    // is how a screen can say where a row came from, and how a
                    // later stage can tell a replica from something this desk
                    // booked.
                    s.originNodeId(),
                    s.createdAt() == null ? s.startTime() : s.createdAt(),
                    s.updatedAt());
            return null;
        } catch (RuntimeException e) {
            // Most likely the EXCLUDE constraint from V2 - the database
            // refusing to hold one therapist in two places at once. It is right
            // to refuse even when the second booking arrived from a peer, and
            // right that the row is skipped rather than the batch abandoned.
            return "refused by the database: " + e.getClass().getSimpleName();
        }
    }
}
