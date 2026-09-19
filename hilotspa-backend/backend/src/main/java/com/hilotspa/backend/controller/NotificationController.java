package com.hilotspa.backend.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.hilotspa.backend.config.CurrentUser;
import com.hilotspa.backend.entities.Appointment;
import com.hilotspa.backend.entities.AppointmentStatus;
import com.hilotspa.backend.entities.Branch;
import com.hilotspa.backend.entities.NotificationKind;
import com.hilotspa.backend.entities.NotificationLog;
import com.hilotspa.backend.model.NotificationDtos.DueRow;
import com.hilotspa.backend.model.NotificationDtos.NotificationRow;
import com.hilotspa.backend.model.NotificationDtos.RunResult;
import com.hilotspa.backend.repository.AppointmentRepository;
import com.hilotspa.backend.repository.BranchRepository;
import com.hilotspa.backend.repository.NotificationLogRepository;
import com.hilotspa.backend.services.ReminderService;

/**
 * A4 / S6 - the notification log.
 *
 * Open to STAFF as well as ADMIN. A branch has to be able to answer for its own
 * reminders without an administrator standing behind it - the front desk is who
 * the client rings. Staff see only their own branch's rows and can only run
 * their own branch's reminders; both rules are applied here from the token,
 * never from a parameter.
 */
@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    @Autowired private NotificationLogRepository notificationLogRepository;
    @Autowired private ReminderService reminderService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private BranchRepository branchRepository;

    @Value("${hilotspa.booking.timezone:Asia/Manila}") private String timezone;
    @Value("${hilotspa.node.name:Local}")              private String nodeName;
    @Value("${hilotspa.node.branch-id:}")              private String nodeBranchIdRaw;

    /**
     * @param branchId honoured for an ADMINISTRATOR only, and only because an
     *                 administrator inside a branch context is ALSO scoped - the
     *                 screen must not show them the other branch's clients while
     *                 its own badge says which branch they are in. A staff
     *                 caller's branch comes from the token and this is ignored.
     */
    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationRow>> log(
            @RequestParam(required = false) UUID branchId) {
        List<UUID> scope = scopeOf(branchId);
        List<NotificationLog> rows = scope == null
                ? notificationLogRepository.findTop200ByOrderByCreatedAtDesc()
                : notificationLogRepository
                    .findTop200ByAppointmentBranchIdInOrderByCreatedAtDesc(scope);
        return ResponseEntity.ok(rows.stream().map(NotificationController::toRow).toList());
    }

    /**
     * Who is booked on a day, and which of them has been told.
     *
     * The log cannot answer this. A client nobody has attempted yet has no row
     * in it, so they are invisible in exactly the list you would use to notice
     * them.
     */
    @GetMapping("/notifications/due")
    public ResponseEntity<List<DueRow>> due(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
            @RequestParam(required = false) UUID branchId) {

        LocalDate target = day == null ? LocalDate.now(ZoneId.of(timezone)).plusDays(1) : day;
        List<UUID> scope = scopeOf(branchId);
        List<UUID> branches = scope == null
                ? branchRepository.findAll().stream().map(Branch::getId).toList()
                : scope;

        LocalDateTime from = target.atStartOfDay();
        LocalDateTime to = target.plusDays(1).atStartOfDay();

        List<Appointment> due = branches.stream()
                .flatMap(b -> appointmentRepository
                        .findByBranchIdAndStartTimeBetween(b, from, to).stream())
                .filter(a -> a.getStatus() == AppointmentStatus.PENDING
                          || a.getStatus() == AppointmentStatus.CONFIRMED)
                .filter(a -> a.getStartTime().isBefore(to))
                .sorted(java.util.Comparator.comparing(Appointment::getStartTime))
                .toList();

        java.util.Map<UUID, NotificationLog> seen = new java.util.HashMap<>();
        if (!due.isEmpty()) {
            notificationLogRepository.findByKindAndAppointmentIdIn(
                    NotificationKind.REMINDER_DAY_BEFORE,
                    due.stream().map(Appointment::getId).toList())
                .forEach(r -> seen.put(r.getAppointment().getId(), r));
        }

        return ResponseEntity.ok(due.stream()
                .map(a -> toDue(a, seen.get(a.getId())))
                .toList());
    }

    /**
     * Send ONE reminder now.
     *
     * Safe to press on a visit already reminded - that is the point. The
     * whole-day run refuses a repeat, because it must; a person pressing a
     * button beside one named client is asking for something else entirely.
     */
    @PostMapping("/notifications/due/{appointmentId}")
    public ResponseEntity<RunResult> sendOne(@PathVariable UUID appointmentId,
                                             @RequestParam(required = false) UUID branchId) {
        Appointment a = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found"));

        // 404 rather than 403 - telling a stranger the id exists is itself a
        // leak, and it is the rule cancel() and reschedule() already follow.
        //
        // sendScope, not scopeOf: this is a SEND. Reading another branch's log
        // on this node is fine, because the row is already in this database.
        // Mailing its client is not, because the node that owns that branch has
        // its own ledger and may have sent it already.
        List<UUID> scope = sendScope(branchId);
        if (scope != null && !scope.contains(a.getBranch().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }

        boolean sent = reminderService.remindOne(
                appointmentId, CurrentUser.email().orElse("the front desk"));
        return ResponseEntity.ok(new RunResult(
                a.getStartTime().toLocalDate(), sent ? 1 : 0,
                sent
                    ? "Sent."
                    : "Nothing was sent - the row beside this visit says why."));
    }

    /**
     * The branches this node may SEND for (task 3.32).
     *
     * Reading and sending are different questions, and this is the one that
     * leaves the building.
     *
     * A replica holds the whole business, so an administrator on the Daraga
     * node pressing "Run reminders" with no branch selected would have mailed
     * every Bulan client a reminder the Bulan node had already sent. The guard
     * against a double send is notification_log, and it is deliberately NOT
     * replicated - what this node has emailed is a local fact, so node 2 cannot
     * learn that node 1 already went. There is nothing to consult; the only
     * safe answer is not to send.
     *
     * So: this node sends for the branch it OWNS, and for no other. Same
     * single-writer-per-partition rule the booking path follows, applied to the
     * one other action that reaches a real person.
     *
     * A node that declares no branch is unchanged - that is a single-node
     * deployment, where "every branch it holds" was always the right answer.
     */
    private List<UUID> sendScope(UUID requested) {
        List<UUID> asked = scopeOf(requested);        // null = every branch
        UUID mine = nodeBranchId();
        if (mine == null) {
            return asked;
        }
        if (asked == null) {
            return List.of(mine);
        }
        List<UUID> both = asked.stream().filter(mine::equals).toList();
        if (both.isEmpty()) {
            // 409 rather than 403: nothing is wrong with the caller or their
            // permissions. They asked the wrong machine.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This node (" + nodeName + ") sends reminders only for the branch it "
                  + "serves. Another node is responsible for the branch you asked for - "
                  + "send it from there, or it may go out twice.");
        }
        return both;
    }

    /** The branch this node owns, or null when it has not been told. */
    private UUID nodeBranchId() {
        if (nodeBranchIdRaw == null || nodeBranchIdRaw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(nodeBranchIdRaw.trim());
        } catch (IllegalArgumentException e) {
            return null;   // NodeController already says so, loudly, at startup
        }
    }

    /**
     * The branches this caller may act on. NULL means every branch.
     *
     * Staff are pinned to their token's branch whatever they send. An
     * administrator gets what they asked for, which is null when they asked for
     * nothing - and a branch when they are INSIDE one, because a screen whose
     * badge says BULAN must not quietly be counting Sorsogon.
     */
    private List<UUID> scopeOf(UUID branchId) {
        if (!CurrentUser.isAdmin()) {
            return List.of(ownBranch());
        }
        return branchId == null ? null : List.of(branchId);
    }

    private static DueRow toDue(Appointment a, NotificationLog n) {
        String to = a.getCustomer() == null ? null : a.getCustomer().getEmail();
        boolean sendable = to != null && !to.isBlank();
        return new DueRow(
                a.getId(),
                a.getCustomer() != null
                    ? a.getCustomer().getFirstName() + " " + a.getCustomer().getLastName()
                    : (a.getWalkInName() == null ? "Walk-in" : a.getWalkInName()),
                a.getService().getName(),
                a.getBranch().getName(),
                a.getStartTime(),
                to,
                n == null ? null : n.getStatus().name(),
                n == null ? 0 : n.getAttempts(),
                n == null ? null : n.getDetail(),
                n == null ? null : n.getSentAt(),
                sendable,
                sendable ? null
                    : "No email address on file. A walk-in booked at the counter has no "
                      + "account to write to.");
    }

    /**
     * The staff caller's branch, from the token.
     *
     * Never from a parameter. A client that can name its own branch is a client
     * that can name someone else's, and this route returns client names and
     * email addresses.
     */
    private UUID ownBranch() {
        return CurrentUser.branchId().orElseThrow(() -> new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Your account is not attached to a branch, so there are no reminders to show."));
    }

    /**
     * Run the day-before reminder now, for a named day.
     *
     * This exists so the feature can be shown working without waiting until
     * 9 AM, and it is safe to press twice: the unique constraint on
     * (appointment, kind) means a second run sends nothing. "Sent 0" on the
     * second press is the evidence that the de-duplication works, not a fault.
     *
     * @param day the day the VISITS fall on. Omit for tomorrow.
     */
    @PostMapping("/notifications/run")
    public ResponseEntity<RunResult> run(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
            @RequestParam(required = false) UUID branchId) {

        LocalDate target = day == null
                ? LocalDate.now(ZoneId.of(timezone)).plusDays(1)
                : day;
        // A front desk runs its own branch and cannot reach across to the other
        // one, even by pressing the same button - the scope comes from the
        // token, not the request. An administrator is additionally clamped to
        // the branch this NODE owns; see sendScope.
        int sent = reminderService.remindFor(target, sendScope(branchId));
        return ResponseEntity.ok(new RunResult(target, sent,
                sent == 0
                    ? "Nothing was sent. Either every visit that day has already been "
                      + "reminded, there are none, or no mail server is configured - "
                      + "the log rows say which."
                    : sent + (sent == 1 ? " reminder was sent." : " reminders were sent.")));
    }

    private static NotificationRow toRow(NotificationLog n) {
        var a = n.getAppointment();
        String client = a.getCustomer() != null
                ? a.getCustomer().getFirstName() + " " + a.getCustomer().getLastName()
                : (a.getWalkInName() == null ? "Walk-in" : a.getWalkInName());
        return new NotificationRow(
                n.getId(), a.getId(), client,
                a.getService().getName(), a.getBranch().getName(), a.getStartTime(),
                n.getKind().name(), n.getChannel().name(), n.getStatus().name(),
                n.getRecipient(), n.getAttempts(), n.getDetail(),
                n.getOriginNodeId(), n.getCreatedAt(), n.getSentAt());
    }
}
