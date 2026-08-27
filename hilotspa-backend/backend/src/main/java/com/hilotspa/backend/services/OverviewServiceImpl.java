package com.hilotspa.backend.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.hilotspa.backend.entities.Appointment;
import com.hilotspa.backend.entities.AppointmentStatus;
import com.hilotspa.backend.entities.AuditLog;
import com.hilotspa.backend.entities.Branch;
import com.hilotspa.backend.entities.Forms;
import com.hilotspa.backend.entities.Massage;
import com.hilotspa.backend.entities.ProtocolRule;
import com.hilotspa.backend.entities.ServiceProtocol;
import com.hilotspa.backend.entities.Therapist;
import com.hilotspa.backend.entities.TherapistStatus;
import com.hilotspa.backend.model.OverviewDtos.AssistantStats;
import com.hilotspa.backend.model.OverviewDtos.ComplaintCount;
import com.hilotspa.backend.model.OverviewDtos.NodeCard;
import com.hilotspa.backend.model.OverviewDtos.Overview;
import com.hilotspa.backend.model.OverviewDtos.Readiness;
import com.hilotspa.backend.repository.AppointmentRepository;
import com.hilotspa.backend.repository.AuditLogRepository;
import com.hilotspa.backend.repository.BranchRepository;
import com.hilotspa.backend.repository.FormsRepository;
import com.hilotspa.backend.repository.MassageRepository;
import com.hilotspa.backend.repository.ServiceProtocolRepository;
import com.hilotspa.backend.repository.RoomRepository;
import com.hilotspa.backend.repository.TherapistRepository;

/**
 * A1 — the aggregate behind the administrator's overview.
 *
 * Counted live, every time. Two things are deliberately NOT here.
 *
 * There is no revenue figure: every service currently seeds at ₱0.00 because the
 * spa has not handed over its rate card, and a money tile reading "₱ 0" is worse
 * than no money tile at all.
 *
 * There is no per-node online/offline state either. The node registry is Sprint
 * 3 and does not exist yet, so this reports one node — the one answering — and
 * says so. Drawing a second node as "offline" would be a picture of a feature
 * rather than the feature.
 */
@Service
public class OverviewServiceImpl implements OverviewService {

    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private FormsRepository formsRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private TherapistRepository therapistRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private MassageRepository massageRepository;
    @Autowired private ServiceProtocolRepository serviceProtocolRepository;

    @Value("${hilotspa.node.id:local-dev}")            private String nodeId;
    @Value("${hilotspa.booking.timezone:Asia/Manila}") private String timezone;

    @Override
    public Overview overview() {
        ZoneId zone = ZoneId.of(timezone);
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDate today = now.toLocalDate();
        LocalDateTime weekAgo = now.minusDays(7);
        LocalDateTime monthAgo = now.minusDays(30);

        List<Appointment> appointments = appointmentRepository.findAll();
        List<Forms> forms = formsRepository.findAll();
        List<Branch> branches = branchRepository.findAll();
        List<Therapist> therapists = therapistRepository.findAll();

        int bookingsToday = (int) appointments.stream()
                .filter(a -> a.getStartTime().toLocalDate().equals(today))
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .count();
        int bookingsWeek = (int) appointments.stream()
                .filter(a -> a.getStartTime().isAfter(weekAgo))
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .count();
        int assessmentsWeek = (int) forms.stream()
                .filter(f -> f.getCreatedAt() != null && f.getCreatedAt().isAfter(weekAgo))
                .count();

        List<NodeCard> nodes = new ArrayList<>();
        for (Branch b : branches) {
            List<Therapist> mine = therapists.stream()
                    .filter(t -> t.getBranch().getId().equals(b.getId()))
                    .toList();
            LocalDateTime lastWrite = appointments.stream()
                    .filter(a -> a.getBranch().getId().equals(b.getId()))
                    .map(Appointment::getCreatedAt)
                    .filter(java.util.Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            nodes.add(new NodeCard(
                    b.getId(),
                    b.getName(),
                    nodeId,
                    true,   // one node exists; see the class comment
                    (int) appointments.stream()
                            .filter(a -> a.getBranch().getId().equals(b.getId()))
                            .filter(a -> a.getStartTime().toLocalDate().equals(today))
                            .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                            .count(),
                    mine.size(),
                    (int) mine.stream().filter(t -> t.getStatus() == TherapistStatus.AVAILABLE).count(),
                    roomRepository.findByBranchId(b.getId()).size(),
                    (int) forms.stream()
                            .filter(f -> f.getBranch() != null
                                    && f.getBranch().getId().equals(b.getId()))
                            .filter(f -> f.getCreatedAt() != null && f.getCreatedAt().isAfter(weekAgo))
                            .count(),
                    lastWrite));
        }

        return new Overview(
                now, bookingsToday, bookingsWeek, assessmentsWeek, forms.size(),
                1, 1,
                nodes,
                topComplaints(forms, monthAgo),
                assistantStats(),
                readiness());
    }

    /**
     * A rule counts as signed when a person's name is on it. The seeded rows
     * carry a placeholder that says so in words, which is why this is derived
     * rather than stored — nobody can tick a box they did not read.
     */
    private Readiness readiness() {
        List<ServiceProtocol> rules = serviceProtocolRepository.findAll();
        int unsigned = (int) rules.stream()
                .filter(p -> p.getAuthoredBy() == null
                        || p.getAuthoredBy().isBlank()
                        || p.getAuthoredBy().toUpperCase().contains("AWAITING"))
                .count();
        int contra = (int) rules.stream()
                .filter(p -> p.getRule() == ProtocolRule.CONTRAINDICATED)
                .count();
        List<Massage> onSale = massageRepository.findAll().stream()
                .filter(Massage::isOnSale)
                .toList();
        int unpriced = (int) onSale.stream()
                .filter(m -> m.getPrice() == null || m.getPrice().signum() <= 0)
                .count();
        return new Readiness(rules.size(), unsigned, contra, onSale.size(), unpriced);
    }

    // ------------------------------------------------------------ complaints

    /** LOWER_BACK_PAIN -> Lower Back Pain. Enum constants are not words. */
    private static String label(String raw) {
        StringBuilder out = new StringBuilder();
        for (String w : raw.split("_")) {
            if (w.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(w.charAt(0)))
               .append(w.substring(1).toLowerCase());
        }
        return out.toString();
    }

    private List<ComplaintCount> topComplaints(List<Forms> forms, LocalDateTime since) {
        Map<String, Long> tally = new LinkedHashMap<>();
        for (Forms f : forms) {
            if (f.getCreatedAt() == null || f.getCreatedAt().isBefore(since)) continue;
            String key = f.getMainComplaint() != null
                    ? label(f.getMainComplaint().name())
                    : (f.getMainComplaintOther() != null && !f.getMainComplaintOther().isBlank()
                        ? f.getMainComplaintOther().trim()
                        : "Not stated");
            tally.merge(key, 1L, Long::sum);
        }
        long max = tally.values().stream().mapToLong(Long::longValue).max().orElse(1L);
        return tally.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(6)
                .map(e -> new ComplaintCount(e.getKey(), e.getValue(),
                        (int) Math.round(100.0 * e.getValue() / max)))
                .toList();
    }

    // ------------------------------------------------------------- assistant

    // The details column is written by hand in AssistantServiceImpl.audit() and
    // is a flat object of scalars. Reading it back with two small regexes beats
    // adding a JSON dependency to a screen that shows six numbers.
    private static final Pattern REJECTED = Pattern.compile("\"rejectedCount\":(\\d+)");
    private static final Pattern RETURNED = Pattern.compile("\"returned\":(\\d+)");
    private static final Pattern STATUS   = Pattern.compile("\"status\":\"([^\"]*)\"");

    private AssistantStats assistantStats() {
        long calls = 0, ok = 0, failed = 0, rejected = 0, returned = 0;

        for (AuditLog row : auditLogRepository.findAll()) {
            if (!"ASSISTANT_RECOMMEND".equals(row.getAction())) continue;
            String d = row.getDetails() == null ? "" : row.getDetails();
            calls++;
            Matcher s = STATUS.matcher(d);
            if (s.find() && "OK".equalsIgnoreCase(s.group(1))) ok++; else failed++;
            rejected += num(REJECTED, d);
            returned += num(RETURNED, d);
        }

        long proposed = rejected + returned;
        double rate = proposed == 0 ? 0.0 : (100.0 * rejected) / proposed;

        String note = calls == 0
                ? "No assistant calls recorded yet. Run the adversarial probes and this fills in."
                : rejected + " of " + proposed + " suggestions were thrown away by the server after "
                  + "the model had already proposed them. None reached a client.";

        return new AssistantStats(calls, ok, failed, rejected, returned,
                Math.round(rate * 10.0) / 10.0, note);
    }

    private static long num(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? Long.parseLong(m.group(1)) : 0L;
    }
}
