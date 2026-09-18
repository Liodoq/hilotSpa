package com.hilotspa.backend.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hilotspa.backend.entities.Appointment;
import com.hilotspa.backend.entities.AppointmentStatus;
import com.hilotspa.backend.entities.Branch;
import com.hilotspa.backend.entities.NotificationKind;
import com.hilotspa.backend.entities.NotificationLog;
import com.hilotspa.backend.entities.NotificationStatus;
import com.hilotspa.backend.repository.AppointmentRepository;
import com.hilotspa.backend.repository.BranchRepository;
import com.hilotspa.backend.repository.NotificationLogRepository;

/**
 * The day-before reminder.
 *
 * Three things this class is built around, in order of how badly they go wrong:
 *
 * 1. It must never mail a client twice. The notification_log row is the claim,
 *    written before the mail server is called; a unique constraint on
 *    (appointment, kind) means a second run loses the insert rather than
 *    sending again. The job therefore remembers nothing between runs and a
 *    restart mid-run cannot double-send.
 *
 * 2. It must leave evidence either way. A reminder that was skipped for want of
 *    an address is a row saying so. Silence and failure look identical in a log
 *    that only records successes, and "I never got a reminder" is a dispute
 *    that log cannot settle.
 *
 * 3. It must not take the spa down. Mail is the least reliable thing this
 *    system touches and the least important: a booking still stands if its
 *    reminder never arrives. So nothing here throws upward, the job is caught
 *    whole, and an unconfigured mail server is a logged fact rather than a
 *    stack trace at nine in the morning.
 */
@Service
public class ReminderServiceImpl implements ReminderService {

    private static final Logger LOG = LoggerFactory.getLogger(ReminderServiceImpl.class);

    /** Visits still standing. A cancelled visit gets no reminder, obviously. */
    private static final Set<AppointmentStatus> LIVE =
            EnumSet.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED);

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE d MMMM, h:mm a", Locale.ENGLISH);

    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private NotificationLogRepository notificationLogRepository;
    @Autowired private BranchRepository branchRepository;

    /**
     * The two log writes, deliberately on ANOTHER bean.
     *
     * @Transactional is applied by a proxy around the bean, so a call from one
     * method of this class to another would bypass it entirely and REQUIRES_NEW
     * would be silently ignored. Crossing a bean boundary is what makes the
     * claim commit before the mail server is called.
     */
    @Autowired private NotificationLedger ledger;

    /**
     * Optional on purpose.
     *
     * The starter creates a sender even with no host configured, and a thesis
     * checkout with no SMTP account must still boot and still demonstrate the
     * log. Absence is a state this class handles, not an error it reports.
     */
    @Autowired private ObjectProvider<JavaMailSender> mailSenders;

    @Value("${hilotspa.booking.timezone:Asia/Manila}") private String timezone;
    @Value("${hilotspa.node.id:local-dev}")           private String nodeId;
    @Value("${hilotspa.node.branch-id:}")              private String nodeBranchId;
    @Value("${hilotspa.reminders.max-attempts:3}")    private int maxAttempts;
    @Value("${spring.mail.host:}")                    private String mailHost;
    @Value("${hilotspa.reminders.from:}")             private String mailFrom;
    @Value("${hilotspa.reminders.reply-to:}")         private String replyTo;

    /**
     * Runs once a day, in the SPA's timezone rather than the server's.
     *
     * The zone is stated explicitly because this is the one place where getting
     * it wrong is invisible: a container running UTC would fire this at 5 PM
     * Manila time and the reminders would still go out, just in the evening,
     * and nothing would ever report a fault.
     */
    @Scheduled(cron = "${hilotspa.reminders.cron:0 0 9 * * *}",
               zone = "${hilotspa.booking.timezone:Asia/Manila}")
    public void nightlyRun() {
        LocalDate tomorrow = LocalDate.now(ZoneId.of(timezone)).plusDays(1);
        try {
            // Scoped to the branch this NODE owns, not to every branch it holds
            // - and those are different things the moment a second node exists.
            //
            // A replica holds a full copy of the business, so an unscoped run on
            // node 2 would mail every Bulan client a reminder node 1 had already
            // sent. The ledger that prevents a double send is notification_log,
            // which is deliberately NOT replicated: what this node has emailed
            // is a local fact, so node 2 cannot learn that node 1 already went.
            //
            // Null when the node declares no branch - a single-node deployment,
            // where "every branch it holds" is the right answer and always was.
            Collection<UUID> scope = ownBranch();
            int sent = remindFor(tomorrow, scope);
            LOG.info("Day-before reminders for {} ({}): {} sent", tomorrow,
                     scope == null ? "every branch" : "own branch only", sent);
        } catch (Exception e) {
            // A scheduled method that throws is silently not rescheduled in some
            // configurations, and a reminder job that stops running is worse
            // than one that fails loudly once.
            LOG.error("Day-before reminder run for {} failed outright", tomorrow, e);
        }
    }

    /**
     * This node's own branch, or null when it has not been told.
     *
     * Null is not a silent failure here: NodeController states the same fact at
     * startup, once, where somebody reads it. A reminder run that quietly
     * covered branches it does not own would be the failure, and that is the
     * case this removes.
     */
    private Collection<UUID> ownBranch() {
        if (nodeBranchId == null || nodeBranchId.isBlank()) {
            return null;
        }
        try {
            return List.of(UUID.fromString(nodeBranchId.trim()));
        } catch (IllegalArgumentException e) {
            LOG.error("NODE_BRANCH_ID is not a UUID: '{}'. Reminding for every branch "
                    + "this node holds, which on a multi-node deployment will double-send.",
                    nodeBranchId);
            return null;
        }
    }

    @Override
    public int remindFor(LocalDate visitDay, Collection<UUID> branchIds) {
        LocalDateTime from = visitDay.atStartOfDay();
        LocalDateTime to = visitDay.plusDays(1).atStartOfDay();

        List<UUID> scope = (branchIds == null || branchIds.isEmpty())
                ? branchRepository.findAll().stream().map(Branch::getId).toList()
                : List.copyOf(branchIds);

        List<Appointment> due = scope.stream()
                .flatMap(b -> appointmentRepository
                        .findByBranchIdAndStartTimeBetween(b, from, to).stream())
                .filter(a -> LIVE.contains(a.getStatus()))
                // Half-open, like every other window in this codebase: a visit
                // at midnight belongs to the following day.
                .filter(a -> a.getStartTime().isBefore(to))
                .toList();

        if (due.isEmpty()) {
            return 0;
        }

        Map<UUID, NotificationLog> already = new HashMap<>();
        notificationLogRepository
                .findByKindAndAppointmentIdIn(NotificationKind.REMINDER_DAY_BEFORE,
                        due.stream().map(Appointment::getId).toList())
                .forEach(r -> already.put(r.getAppointment().getId(), r));

        int sent = 0;
        for (Appointment a : due) {
            NotificationLog prior = already.get(a.getId());
            if (prior != null && !retryable(prior)) {
                continue;
            }
            if (sendOne(a, prior, null)) {
                sent++;
            }
        }
        return sent;
    }

    /**
     * A FAILED row may be tried again; SENT and SKIPPED may not.
     *
     * SKIPPED is permanent because its reasons are permanent - a client with no
     * address on file will not grow one before tomorrow morning, and retrying
     * would fill the log with the same sentence every run.
     */
    private boolean retryable(NotificationLog prior) {
        return prior.getStatus() == NotificationStatus.FAILED
                && prior.getAttempts() < maxAttempts;
    }

    @Override
    public boolean remindOne(UUID appointmentId, String by) {
        Appointment a = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found"));

        if (!LIVE.contains(a.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That visit is " + a.getStatus().name().toLowerCase().replace('_', ' ')
                    + ", so there is nothing to remind anyone about.");
        }

        NotificationLog prior = notificationLogRepository
                .findByKindAndAppointmentIdIn(NotificationKind.REMINDER_DAY_BEFORE,
                        List.of(a.getId()))
                .stream().findFirst().orElse(null);

        // The note only appears when this is a REPEAT. On a first send there is
        // nothing to explain, and "sent by hand" beside a row nobody had tried
        // before would read as though the automatic job had failed.
        String note = prior != null && prior.getStatus() == NotificationStatus.SENT
                ? "Re-sent by hand by " + (by == null ? "the front desk" : by) + "."
                : null;

        return sendOne(a, prior, note);
    }

    /**
     * @param note set when a human asked for this, which also FORCES the send
     *             past a prior SENT row. Null for the automatic job.
     */
    private boolean sendOne(Appointment a, NotificationLog prior, String note) {
        String to = recipientOf(a);

        NotificationLog row;
        try {
            row = ledger.claim(a, prior, to);
        } catch (DataIntegrityViolationException e) {
            // Another run got here first. Not an error - this is the lock doing
            // exactly what it exists to do.
            LOG.debug("Reminder for {} already claimed elsewhere", a.getId());
            return false;
        }
        if (row == null) {
            return false;
        }

        if (to == null || to.isBlank()) {
            ledger.finish(row, NotificationStatus.SKIPPED,
                    "No email address on file for this client. A walk-in booked at the "
                    + "counter has no account to mail.");
            return false;
        }
        if (mailHost == null || mailHost.isBlank()) {
            ledger.finish(row, NotificationStatus.SKIPPED,
                    "No mail server is configured on this node, so nothing was sent. "
                    + "Set MAIL_HOST and the reminder will go out on the next run.");
            return false;
        }

        JavaMailSender sender = mailSenders.getIfAvailable();
        if (sender == null) {
            ledger.finish(row, NotificationStatus.SKIPPED, "No mail sender is available on this node.");
            return false;
        }

        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(to);
            if (mailFrom != null && !mailFrom.isBlank()) {
                mail.setFrom(mailFrom);
            }
            if (replyTo != null && !replyTo.isBlank()) {
                mail.setReplyTo(replyTo);
            }
            mail.setSubject("Your visit tomorrow - " + a.getService().getName());
            mail.setText(body(a));
            sender.send(mail);
            ledger.finish(row, NotificationStatus.SENT, note);
            return true;
        } catch (Exception e) {
            // The message, not the stack: this string is shown to an
            // administrator on a web page, and "MailAuthenticationException" is
            // the useful half of it.
            ledger.finish(row, NotificationStatus.FAILED,
                    e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
            LOG.warn("Reminder for appointment {} failed", a.getId(), e);
            return false;
        }
    }

    /** The account holder's address. A walk-in has none, and that is a SKIPPED row. */
    private static String recipientOf(Appointment a) {
        return a.getCustomer() == null ? null : a.getCustomer().getEmail();
    }

    /**
     * Plain text, not HTML.
     *
     * It has to be readable on a cheap phone with images off, and the only
     * thing it needs to carry is when, where and with whom. An email that
     * arrives as a broken layout is worse than one that arrives as a sentence.
     */
    private String body(Appointment a) {
        String name = a.getCustomer() == null ? "there" : a.getCustomer().getFirstName();
        return "Hello " + name + ",\n\n"
             + "This is a reminder of your visit tomorrow at Knead Wellness Spa.\n\n"
             + "  " + a.getService().getName() + "\n"
             + "  " + a.getStartTime().format(WHEN) + "\n"
             + "  " + a.getTherapist().getFirstName() + " " + a.getTherapist().getLastName()
             + ", " + a.getRoom().getName() + "\n"
             + "  " + a.getBranch().getName() + "\n\n"
             + "Please arrive about 10 minutes early. There is nothing to pay online - "
             + "you settle at the counter.\n\n"
             + "If you can no longer come, please cancel from Your visits on the website, "
             + "or call the branch. Cancelling online closes an hour before the visit.\n\n"
             + "Knead Wellness Spa\n"
             + "Bulan, Sorsogon\n";
    }

}
