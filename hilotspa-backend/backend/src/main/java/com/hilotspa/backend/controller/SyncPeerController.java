package com.hilotspa.backend.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hilotspa.backend.entities.Appointment;
import com.hilotspa.backend.entities.SyncLog;
import com.hilotspa.backend.model.SyncDtos.AppointmentSnapshot;
import com.hilotspa.backend.model.SyncDtos.Change;
import com.hilotspa.backend.model.SyncDtos.Hello;
import com.hilotspa.backend.repository.AppointmentRepository;
import com.hilotspa.backend.repository.BranchRepository;
import com.hilotspa.backend.repository.SyncLogRepository;

/**
 * What this node tells a PEER (task 3.2).
 *
 * Separate from SyncController, which is the administrator's view under
 * /api/v1/admin. Keeping them apart from the start means the peer-facing route
 * can never accidentally inherit a human's permissions, and the human-facing
 * one can never be reached with the shared node token.
 *
 * Everything under /api/v1/sync is guarded by SyncTokenFilter, not by the JWT
 * chain.
 */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncPeerController {

    /** Bump when the peer contract changes, so a mismatch is visible not subtle. */
    private static final int PROTOCOL = 1;

    @Autowired private SyncLogRepository syncLogRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    @Value("${hilotspa.node.id:local-dev}")            private String nodeId;
    @Value("${hilotspa.node.name:Local}")              private String nodeName;
    @Value("${hilotspa.node.branch-id:}")              private String branchIdRaw;
    @Value("${hilotspa.booking.timezone:Asia/Manila}") private String timezone;

    @GetMapping("/hello")
    public ResponseEntity<Hello> hello() {
        SyncLog latest = syncLogRepository.findFirstByOriginNodeIdOrderByIdDesc(nodeId);

        UUID branch = null;
        String branchName = null;
        if (branchIdRaw != null && !branchIdRaw.isBlank()) {
            try {
                branch = UUID.fromString(branchIdRaw.trim());
                branchName = branchRepository.findById(branch)
                        .map(b -> b.getName()).orElse(null);
            } catch (IllegalArgumentException ignored) {
                // A malformed id in configuration must not take this endpoint
                // down. The peer still needs to know we are alive, and
                // NodeController already says so at startup.
                branch = null;
            }
        }

        return ResponseEntity.ok(new Hello(
                nodeId, nodeName, branch, branchName,
                latest == null ? 0L : latest.getId(),
                LocalDateTime.now(ZoneId.of(timezone)),
                PROTOCOL));
    }

    /**
     * Everything THIS node wrote after the caller's watermark (task 3.3).
     *
     * Filtered to rows this node originated. A peer asking us for changes wants
     * ours, not ones we copied from it - returning those would hand it back its
     * own writes and each side would climb the other's watermark for ever.
     *
     * Ordered by id, capped. The cap is what makes a node that has been offline
     * for a week catch up in several small steps instead of one request that
     * times out and never makes progress.
     */
    @GetMapping("/changes")
    public ResponseEntity<List<Change>> changes(
            @RequestParam(defaultValue = "0") long since,
            @RequestParam(defaultValue = "200") int limit) {

        int capped = Math.max(1, Math.min(limit, 500));
        List<Change> rows = syncLogRepository
                .findByOriginNodeIdAndIdGreaterThanOrderByIdAsc(nodeId, since, Limit.of(capped))
                .stream()
                .map(r -> new Change(
                        r.getId(), r.getOriginNodeId(), r.getEntityType(),
                        r.getEntityId(), r.getAction(), r.getBranchId(), r.getOccurredAt()))
                .toList();
        return ResponseEntity.ok(rows);
    }

    /**
     * The current state of named appointments.
     *
     * By id, and only ids the caller already learned from /changes. There is no
     * "give me everything" here on purpose: an endpoint that lists a branch's
     * bookings to anyone holding the node token is a much larger thing to
     * defend than one that answers about rows the caller was already told
     * changed.
     */
    @GetMapping("/appointments")
    public ResponseEntity<List<AppointmentSnapshot>> appointments(
            @RequestParam List<UUID> ids) {

        if (ids.size() > 500) {
            ids = ids.subList(0, 500);
        }
        List<AppointmentSnapshot> out = appointmentRepository.findAllById(ids).stream()
                .map(SyncPeerController::snapshot)
                .toList();
        return ResponseEntity.ok(out);
    }

    /** No account, no form, no contact number - see AppointmentSnapshot. */
    private static AppointmentSnapshot snapshot(Appointment a) {
        String who = a.getCustomer() != null
                ? (a.getCustomer().getFirstName() + " " + a.getCustomer().getLastName()).trim()
                : (a.getWalkInName() == null ? "Walk-in" : a.getWalkInName());
        return new AppointmentSnapshot(
                a.getId(),
                a.getBranch().getId(),
                a.getService().getId(),
                a.getTherapist().getId(),
                a.getRoom().getId(),
                who,
                a.getStartTime(),
                a.getEndTime(),
                a.getStatus().name(),
                a.getPaymentStatus().name(),
                a.getSource().name(),
                a.getPriceAtBooking(),
                a.getNotes(),
                a.getOriginNodeId(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }
}
