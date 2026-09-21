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
    @Value("${hilotspa.reminders.enabled:true}")      private boolean enabled;
    @Value("${hilotspa.reminders.lead-hours:24}")     private int leadHours;
    @Value("${hilotspa.reminders.final-lead-minutes:60}") private int finalLeadMinutes;
    @Value("${spring.mail.host:}")                    private String mailHost;
    @Value("${hilotspa.reminders.from:}")             private String mailFrom;
    @Value("${hilotspa.reminders.reply-to:}")         private String replyTo;

    /**
     * The sweep (3.38). Every few minutes, send whatever has become due.
     *
     * The adviser asked for reminders driven by the appointment, not by the
     * clock, and the difference is not pedantic. The old job ran at 9 AM and
     * told everybody booked the following day that their visit was "tomorrow".
     * For a 10 PM booking that email arrived THIRTY-SEVEN hours early, and for
     * anything booked after 9 AM for the next morning it never arrived at all.
     * Measuring from each visit's own start time fixes both, and it is the only
     * reading of "one hour before" that means anything.
     *
     * Why a fixed DELAY and not a cron: this is the whole job, so two runs must
     * never overlap. fixedDelay counts from the END of the previous run, so a
     * slow mail server postpones the next sweep instead of stacking on top of
     * it. The claim row would stop a double send either way; not needing it to
     * is better.
     *
     * The granularity of the reminder is the sweep interval. At five minutes an
     * "hour before" email lands between 55 and 60 minutes ahead, which is what
     * "about an hour" means to a person and is why the copy says "about".
     */
    @Scheduled(fixedDelayString = "${hilotspa.reminders.sweep-ms:300000}",
               initialDelayString = "${hilotspa.reminders.sweep-initial-ms:60000}")
    public void sweepRun() {
        if (!enabled) {
            return;
        }
        try {
            int day = sweep(NotificationKind.REMINDER_DAY_BEFORE);
            int hour = sweep(NotificationKind.REMINDER_HOUR_BEFORE);
            if (day > 0 || hour > 0) {
                // Silent when there is nothing to do. A line every five minutes
                // saying "0 sent" is a log nobody reads, which is a log that
                // hides the line that matters.
                LOG.info("Reminder sweep: {} day-before, {} hour-before", day, hour);
            }
        } catch (Exception e) {
            // A scheduled method that throws is silently not rescheduled in
            // some configurations, and a reminder job that stops running is
            // worse than one that fails loudly once.
            LOG.error("Reminder sweep failed outright", e);
        }
    }

    /**
     * Everything of one kind that is due right now.
     *
     * "Due" is: the visit has not started, it is within this kind's lead time,
     * and this node owns its branch. The lead times are half-open windows from
     * now, so a visit cannot be due for a kind twice - and the unique
     * constraint on (appointment, kind) is the backstop if it somehow is.
     */
    @Override
    public int sweep(NotificationKind kind) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of(timezone));
        LocalDateTime until = kind == NotificationKind.REMINDER_HOUR_BEFORE
                ? now.plusMinutes(finalLeadMinutes)
                : now.plusHours(leadHours);

        Collection<UUID> own = ownBranch();
        List<UUID> scope = (own == null || own.isEmpty())
                ? branchRepository.findAll().stream().map(Branch::getId).toList()
                : List.copyOf(own);

        List<Appointment> due = scope.stream()
                .flatMap(b -> appointmentRepository
                        .findByBranchIdAndStartTimeBetween(b, now, until).stream())
                .filter(a -> LIVE.contains(a.getStatus()))
                // A visit already under way needs no reminder, and one that has
                // been and gone needs one even less.
                .filter(a -> a.getStartTime().isAfter(now))
                .toList();

        if (due.isEmpty()) {
            return 0;
        }

        Map<UUID, NotificationLog> already = new HashMap<>();
        notificationLogRepository
                .findByKindAndAppointmentIdIn(kind,
                        due.stream().map(Appointment::getId).toList())
                .forEach(r -> already.put(r.getAppointment().getId(), r));

        int sent = 0;
        for (Appointment a : due) {
            NotificationLog prior = already.get(a.getId());
            if (prior != null && !retryable(prior)) {
                continue;
            }
            // A visit booked an hour before it happens is inside BOTH windows on
            // the very first sweep. Sending both would be two emails a minute
            // apart saying different things about the same appointment. The
            // day-before one is the one that has been overtaken, so it is
            // recorded as skipped - a row, not a silence, because "did you send
            // me a reminder?" is exactly the question this log exists to answer.
            if (kind == NotificationKind.REMINDER_DAY_BEFORE
                    && !a.getStartTime().isAfter(now.plusMinutes(finalLeadMinutes))) {
                skip(a, kind, prior, "Booked less than " + finalLeadMinutes + " minutes before the "
                        + "visit, so the day-before reminder was already overtaken. The "
                        + "hour-before one covers it.");
                continue;
            }
            if (sendOne(a, kind, prior, null)) {
                sent++;
            }
        }
        return sent;
    }

    /**
     * Record a reminder we deliberately did not send, and why.
     *
     * `prior` is passed through rather than always inserting: a FAILED row that
     * has since been overtaken must be UPDATED to SKIPPED, not duplicated. A
     * fresh insert would collide with the unique constraint, be swallowed here,
     * and leave a FAILED row that the next sweep would try again forever.
     */
    private void skip(Appointment a, NotificationKind kind, NotificationLog prior, String why) {
        try {
            NotificationLog row = ledger.claim(a, kind, prior, recipientOf(a));
            ledger.finish(row, NotificationStatus.SKIPPED, why);
        } catch (DataIntegrityViolationException e) {
            LOG.debug("Skip row for {} {} already exists", a.getId(), kind);
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
            if (sendOne(a, NotificationKind.REMINDER_DAY_BEFORE, prior, null)) {
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

        return sendOne(a, NotificationKind.REMINDER_DAY_BEFORE, prior, note);
    }

    /**
     * @param note set when a human asked for this, which also FORCES the send
     *             past a prior SENT row. Null for the automatic job.
     */
    private boolean sendOne(Appointment a, NotificationKind kind,
                            NotificationLog prior, String note) {
        String to = recipientOf(a);

        NotificationLog row;
        try {
            row = ledger.claim(a, kind, prior, to);
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
            mail.setSubject(subject(a, kind));
            mail.setText(body(a, kind));
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
     * The subject line, which on a phone is often the whole message.
     *
     * The timing goes FIRST because a lock screen shows about forty characters.
     * "Your visit tomorrow - Hilotin Signature Massage" truncates to something
     * useful; the same words the other way round do not.
     */
    private String subject(Appointment a, NotificationKind kind) {
        return whenPhrase(a, kind, true) + " - " + a.getService().getName();
    }

    /**
     * How to refer to WHEN the visit is, in words.
     *
     * The first reminder fires as soon as the visit is inside 24 hours, which is
     * NOT the same as "tomorrow". A visit at 9 PM booked at 7 PM the same
     * evening is two hours away and would have been told it was tomorrow - the
     * exact inversion of the old bug, where a visit really was tomorrow and the
     * email arrived thirty-seven hours early. Measuring from the appointment
     * fixed the timing; the sentence has to follow the timing or the email is
     * still lying, just in a new direction.
     *
     * Compared as calendar DATES in the spa's zone, not as a difference in
     * hours: "tomorrow" is a day on a calendar, and 23 hours can land either
     * side of midnight.
     */
    private String whenPhrase(Appointment a, NotificationKind kind, boolean forSubject) {
        if (kind == NotificationKind.REMINDER_HOUR_BEFORE) {
            return forSubject ? "Your visit in about an hour" : "in about an hour";
        }
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        LocalDate visit = a.getStartTime().toLocalDate();
        if (visit.equals(today)) {
            return forSubject ? "Your visit later today" : "later today";
        }
        if (visit.equals(today.plusDays(1))) {
            return forSubject ? "Your visit tomorrow" : "tomorrow";
        }
        // Belt and braces: the window is 24 hours, so this is only reachable if
        // somebody widens lead-hours. Naming the day is right at any distance.
        String day = a.getStartTime().format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH));
        return forSubject ? "Your visit on " + day : "on " + day;
    }

    /**
     * Plain text, not HTML.
     *
     * It has to be readable on a cheap phone with images off, and the only
     * thing it needs to carry is when, where and with whom. An email that
     * arrives as a broken layout is worse than one that arrives as a sentence.
     *
     * EVERY place name and number here comes from the appointment's own branch.
     * This method used to end with the words "Knead Wellness Spa / Bulan,
     * Sorsogon" typed into the Java, which meant a Daraga client was told to
     * ring Bulan - and, exactly like B139 and the login kicker before it, it was
     * invisible on the one node where anybody was reading the emails, because
     * on Bulan the hardcoded words and the true ones are the same words. That
     * is now the fourth time this shape of fault has appeared in this codebase.
     * The rule it keeps teaching: if a value differs per branch, it is never a
     * literal, however obviously correct it looks on the node in front of you.
     */
    private String body(Appointment a, NotificationKind kind) {
        String name = a.getCustomer() == null ? "there" : a.getCustomer().getFirstName();
        Branch branch = a.getBranch();

        String opening = "This is a reminder of your visit "
                + whenPhrase(a, kind, false) + ".";

        String arrive = kind == NotificationKind.REMINDER_HOUR_BEFORE
                ? "Please make your way over now if you have not already - we hold the room "
                  + "for your booked time.\n\n"
                : "Please arrive about 10 minutes early. There is nothing to pay online - "
                  + "you settle at the counter.\n\n";

        StringBuilder out = new StringBuilder();
        out.append("Hello ").append(name).append(",\n\n")
           .append(opening).append("\n\n")
           .append("  ").append(a.getService().getName()).append("\n")
           .append("  ").append(a.getStartTime().format(WHEN)).append("\n")
           .append("  ").append(a.getTherapist().getFirstName()).append(" ")
           .append(a.getTherapist().getLastName())
           .append(", ").append(a.getRoom().getName()).append("\n");

        if (branch != null) {
            out.append("  ").append(branch.getName()).append("\n");
            if (branch.getAddress() != null && !branch.getAddress().isBlank()) {
                out.append("  ").append(branch.getAddress().trim()).append("\n");
            }
            if (branch.getContactNumber() != null && !branch.getContactNumber().isBlank()) {
                out.append("  ").append(branch.getContactNumber().trim()).append("\n");
            }
        }

        out.append("\n").append(arrive);

        // The cancellation sentence changes with the reminder, because by the
        // time the second one goes out the online cancellation has already
        // closed. Telling somebody to cancel from a page whose button is
        // greyed out is worse than telling them nothing.
        if (kind == NotificationKind.REMINDER_HOUR_BEFORE) {
            out.append("If something has come up, please ring the branch")
               .append(hasNumber(branch) ? " on the number above" : "")
               .append(" - online cancellation has closed for this visit.\n\n");
        } else {
            out.append("If you can no longer come, please cancel from Your visits on the "
                    + "website, or ring the branch. Cancelling online closes an hour "
                    + "before the visit.\n\n");
        }

        out.append(branch == null ? "Knead Wellness Spa" : branch.getName()).append("\n");
        if (branch != null && branch.getAddress() != null && !branch.getAddress().isBlank()) {
            out.append(branch.getAddress().trim()).append("\n");
        }
        return out.toString();
    }

    private static boolean hasNumber(Branch b) {
        return b != null && b.getContactNumber() != null && !b.getContactNumber().isBlank();
    }
}
