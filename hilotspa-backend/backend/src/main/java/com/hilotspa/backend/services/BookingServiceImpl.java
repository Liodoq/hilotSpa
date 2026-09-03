package com.hilotspa.backend.services;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hilotspa.backend.config.CurrentUser;
import com.hilotspa.backend.entities.Appointment;
import com.hilotspa.backend.entities.AppointmentStatus;
import com.hilotspa.backend.entities.AuditLog;
import com.hilotspa.backend.entities.Branch;
import com.hilotspa.backend.entities.BookingSource;
import com.hilotspa.backend.entities.Forms;
import com.hilotspa.backend.entities.Massage;
import com.hilotspa.backend.entities.PaymentStatus;
import com.hilotspa.backend.entities.PatientIntake;
import com.hilotspa.backend.entities.Role;
import com.hilotspa.backend.entities.Room;
import com.hilotspa.backend.entities.Sex;
import com.hilotspa.backend.entities.Therapist;
import com.hilotspa.backend.entities.TherapistStatus;
import com.hilotspa.backend.model.BookingDtos.Availability;
import com.hilotspa.backend.model.BookingDtos.BookRequest;
import com.hilotspa.backend.model.BookingDtos.Booking;
import com.hilotspa.backend.model.BookingDtos.DayLoad;
import com.hilotspa.backend.model.BookingDtos.Openings;
import com.hilotspa.backend.model.BookingDtos.OpenTherapist;
import com.hilotspa.backend.model.BookingDtos.OutcomeRequest;
import com.hilotspa.backend.model.BookingDtos.OutcomeScore;
import com.hilotspa.backend.model.BookingDtos.OpenRoom;
import com.hilotspa.backend.model.BookingDtos.ScheduleRow;
import com.hilotspa.backend.model.BookingDtos.Slot;
import com.hilotspa.backend.model.BookingDtos.WalkInRequest;
import com.hilotspa.backend.repository.AppointmentRepository;
import com.hilotspa.backend.repository.AuditLogRepository;
import com.hilotspa.backend.repository.BranchRepository;
import com.hilotspa.backend.repository.FormsRepository;
import com.hilotspa.backend.repository.MassageRepository;
import com.hilotspa.backend.repository.RoomRepository;
import com.hilotspa.backend.repository.TherapistRepository;

/**
 * Availability and booking.
 *
 * The rule this class exists to protect: SPRING performs every write, inside a
 * transaction that re-checks the slot. The assistant may propose; it may even
 * call the endpoint on the client's behalf. It can never be the thing that
 * decides the slot was free, because a model cannot hold a database lock.
 */
@Service
public class BookingServiceImpl implements BookingService {

    /**
     * Named LOG, not `log`, on purpose: audit() builds a local AuditLog called
     * `log`, and a field it silently shadows is a trap for whoever edits this
     * next.
     */
    private static final Logger LOG = LoggerFactory.getLogger(BookingServiceImpl.class);

    /** An appointment in any of these states occupies its therapist and room. */
    private static final List<AppointmentStatus> BLOCKING = List.of(
            AppointmentStatus.PENDING,
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.IN_PROGRESS);

    private static final DateTimeFormatter LABEL =
            DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH);

    @Autowired private FormsRepository formsRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private MassageRepository massageRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private TherapistRepository therapistRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    @Value("${hilotspa.booking.timezone:Asia/Manila}") private String timezone;
    @Value("${hilotspa.booking.open-hour:9}")          private int openHour;
    @Value("${hilotspa.booking.close-hour:18}")        private int closeHour;
    @Value("${hilotspa.booking.slot-minutes:30}")      private int slotMinutes;
    @Value("${hilotspa.booking.max-days:7}")           private int maxDays;
    @Value("${hilotspa.node.id:local-dev}")            private String nodeId;

    // ---------------------------------------------------------- availability

    @Override
    @Transactional(readOnly = true)
    public Openings openings(UUID formId, UUID serviceId, LocalDateTime start) {
        Forms form = loadAndAuthorise(formId);
        Massage service = massageRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Service not found"));

        UUID branchId = form.getBranch().getId();
        LocalDateTime end = start.plusMinutes(service.getDurationMinute());

        // A time in the past is not a time. The client left the tab open.
        if (start.isBefore(LocalDateTime.now(ZoneId.of(timezone)))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That time has already passed. Please choose another.");
        }

        // The assessment's sex preference still narrows the list. It is not a
        // filter the client is overriding here - it is the reason this shorter
        // list is the right one to show them.
        List<OpenTherapist> therapists = freeTherapists(
                branchId, start, end, form.getTherapistPreference()).stream()
                .map(t -> new OpenTherapist(
                        t.getId(),
                        t.getFirstName(),
                        // First name and sex only, exactly as the public
                        // meet-the-team section shows. Surnames and photos are a
                        // consent question about real employees that nobody has
                        // asked them.
                        t.getSex() == null ? null : t.getSex().getDisplayName()))
                .toList();

        List<OpenRoom> rooms = freeRooms(branchId, start, end).stream()
                .map(r -> new OpenRoom(r.getId(), r.getName(), r.getImageName()))
                .toList();

        return new Openings(start, start.format(LABEL), therapists, rooms);
    }

    @Override
    @Transactional(readOnly = true)
    public Availability availability(UUID formId, UUID serviceId, LocalDate from, int days) {
        Forms form = loadAndAuthorise(formId);
        Massage service = massageRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Service not found"));

        UUID branchId = form.getBranch().getId();
        ZoneId zone = ZoneId.of(timezone);
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDate start = from != null ? from : now.toLocalDate();
        int window = days > 0 ? Math.min(days, maxDays) : maxDays;

        List<Therapist> therapists = therapistRepository.findByBranchIdAndActiveTrue(branchId);
        List<Room> rooms = roomRepository.findByBranchIdAndActiveTrue(branchId);

        // The client may have asked for a woman or a man. Applied HERE as well as
        // at assignment: offering a time only a male therapist could cover, to a
        // client who asked for a woman, is a promise the write path then breaks.
        //
        // A therapist whose sex is not recorded matches NO preference. Guessing
        // would be the one mistake this feature exists to prevent.
        Sex want = form.getTherapistPreference();
        if (want != null) {
            therapists = therapists.stream().filter(t -> want == t.getSex()).toList();
        }

        // Task 2.38 - TherapistStatus is honoured, and only for TODAY.
        //
        // active=false means the therapist has left the spa: gone from every day.
        // status is a different thing - a right-now flag with no end time, set at
        // the front desk when someone goes on break or off shift. Applying it to
        // the whole week would mean marking one person ON_BREAK also emptied next
        // Tuesday, which is nonsense and would teach staff never to touch the
        // control. So it removes them from today's remaining slots and nothing
        // else: nobody has said they will still be off tomorrow.
        List<Therapist> onDutyNow = therapists.stream().filter(BookingServiceImpl::onDuty).toList();
        LocalDate todayHere = now.toLocalDate();

        List<Slot> slots = new ArrayList<>();
        if (therapists.isEmpty() || rooms.isEmpty()) {
            // No staff, no room, or nobody matching the client's preference. An
            // empty list is the honest answer; the SCREEN is what has to explain
            // which of those it was, because "no female therapist works at this
            // branch" and "no times left this week" are very different messages.
            return new Availability(timezone, now, serviceId, service.getName(),
                    service.getDurationMinute(), service.getPrice(), slots);
        }

        LocalDateTime windowStart = start.atStartOfDay();
        LocalDateTime windowEnd = start.plusDays(window).atStartOfDay();

        // One query for the whole window, then overlap arithmetic in memory.
        // Asking the database per slot per therapist would be several hundred
        // round trips to draw one week of a calendar.
        List<Appointment> booked = appointmentRepository
                .findByBranchIdAndStartTimeBetween(branchId, windowStart, windowEnd)
                .stream()
                .filter(a -> BLOCKING.contains(a.getStatus()))
                .toList();

        int duration = service.getDurationMinute();

        for (int day = 0; day < window; day++) {
            LocalDate d = start.plusDays(day);
            LocalDateTime cursor = d.atTime(openHour, 0);
            LocalDateTime lastStart = d.atTime(closeHour, 0).minusMinutes(duration);

            // Today is judged against who is actually on the floor; later days
            // against everyone who works here.
            List<Therapist> pool = d.equals(todayHere) ? onDutyNow : therapists;

            while (!cursor.isAfter(lastStart)) {
                LocalDateTime slotEnd = cursor.plusMinutes(duration);

                // Never offer a time that has already passed today.
                if (cursor.isAfter(now) && anyFree(pool, rooms, booked, cursor, slotEnd)) {
                    slots.add(new Slot(cursor.toString(), cursor, cursor.format(LABEL)));
                }
                cursor = cursor.plusMinutes(slotMinutes);
            }
        }

        return new Availability(timezone, now, serviceId, service.getName(),
                duration, service.getPrice(), slots);
    }

    /**
     * Is this therapist on the floor right now?
     *
     * Null counts as yes. The column was added late and older rows may not have
     * one; refusing to book a therapist because nobody has ever touched their
     * status would take the branch offline for a data gap, which is the wrong
     * way round.
     */
    private static boolean onDuty(Therapist t) {
        return t.getStatus() == null || t.getStatus() == TherapistStatus.AVAILABLE;
    }

    /** A slot is open only when BOTH a therapist and a room are free for it. */
    private boolean anyFree(List<Therapist> therapists, List<Room> rooms,
                            List<Appointment> booked,
                            LocalDateTime start, LocalDateTime end) {
        boolean therapistFree = therapists.stream().anyMatch(t ->
                booked.stream().noneMatch(a ->
                        a.getTherapist() != null
                        && a.getTherapist().getId().equals(t.getId())
                        && overlaps(a, start, end)));
        if (!therapistFree) {
            return false;
        }
        return rooms.stream().anyMatch(r ->
                booked.stream().noneMatch(a ->
                        a.getRoom() != null
                        && a.getRoom().getId().equals(r.getId())
                        && overlaps(a, start, end)));
    }

    /** Half-open intervals: a 3 PM finish and a 3 PM start do not collide. */
    private static boolean overlaps(Appointment a, LocalDateTime start, LocalDateTime end) {
        return a.getStartTime().isBefore(end) && a.getEndTime().isAfter(start);
    }

    // ----------------------------------------------------------------- book

    @Override
    @Transactional
    public Booking book(BookRequest req) {
        if (req.serviceId() == null || req.start() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "serviceId and start are required");
        }

        Forms form = loadAndAuthorise(req.formId());
        Massage service = massageRepository.findById(req.serviceId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Service not found"));

        UUID branchId = form.getBranch().getId();
        if (form.getUser() == null) {
            // A walk-in assessment (B85). It has no account to bill, notify or
            // scope "my bookings" by, so it cannot go down the online path -
            // staff record the visit at the counter instead.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That assessment belongs to a walk-in. Record the visit from the "
                    + "counter instead.");
        }
        UUID customerId = form.getUser().getId();
        LocalDateTime start = req.start();
        LocalDateTime end = start.plusMinutes(service.getDurationMinute());

        if (start.isBefore(LocalDateTime.now(ZoneId.of(timezone)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That time has already passed");
        }

        // Idempotency. An agent that retried a timed-out call must not create a
        // second appointment. Same client, same service, same minute, still live.
        for (Appointment existing : appointmentRepository.findByCustomerId(customerId)) {
            if (BLOCKING.contains(existing.getStatus())
                    && existing.getService().getId().equals(service.getId())
                    && existing.getStartTime().equals(start)) {
                return toDto(existing);
            }
        }

        // Rule 4 - a client cannot be in two rooms at once (task 2.36).
        //
        // The spa's three rules are all about the SPA's resources: a free
        // therapist, a free room, no clash with an existing booking. None of
        // them notices the CLIENT. The idempotency guard above only catches an
        // identical repeat, so nothing stopped one person holding a 9:00
        // Signature and a 9:00 Ventosa - different therapist, different room,
        // every stated rule satisfied, and two therapists standing idle at 9:00.
        List<Appointment> clash = appointmentRepository
                .findByCustomerIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                        customerId, BLOCKING, end, start);
        if (!clash.isEmpty()) {
            Appointment other = clash.get(0);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already have " + other.getService().getName() + " booked at "
                    + other.getStartTime().format(LABEL)
                    + ". Cancel that first, or choose a different time.");
        }

        Assignment assignment = assign(branchId, start, end, form.getTherapistPreference(),
                req.therapistId(), req.roomId());
        Therapist therapist = assignment.therapist();
        Room room = assignment.room();

        Appointment a = new Appointment();
        a.setBranch(form.getBranch());
        a.setCustomer(form.getUser());
        a.setForm(form);
        a.setService(service);
        a.setTherapist(therapist);
        a.setRoom(room);
        a.setStartTime(start);
        a.setEndTime(end);
        a.setStatus(AppointmentStatus.CONFIRMED);
        a.setPaymentStatus(PaymentStatus.UNPAID);
        // The assistant is the booking channel; the paper's BookingSource enum
        // anticipated exactly this.
        a.setSource(BookingSource.CHATBOT);
        // Copied now, so a later price change cannot alter a booked visit.
        a.setPriceAtBooking(service.getPrice());
        a.setOriginNodeId(nodeId);

        Appointment saved = writeOrConflict(a);
        audit(saved, req.consentText());
        return toDto(saved);
    }

    /**
     * The CALLER'S OWN appointments. Never anyone else's, whatever their role.
     *
     * This used to branch: an ADMIN got findAll() and STAFF got their whole
     * branch. That contradicted the method's own name and its controller's own
     * comment, and it had a visible consequence - /booking is a CLIENT screen
     * and renders this list as "You have 2 visits booked", so an administrator
     * signing in was shown a named client's visit, therapist and room labelled
     * as their own. (B101, reported by Liodoq with both windows side by side.)
     *
     * Staff and administrators have schedule() and schedule/month for the
     * branch view; those are the routes with the branch rules on them. Role
     * must never widen a query whose name says "mine".
     */
    @Override
    public List<Booking> mine() {
        UUID me = CurrentUser.id().orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Not authenticated"));

        List<Appointment> found = appointmentRepository.findByCustomerId(me);

        return found.stream()
                .sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
                .map(this::toDto)
                .toList();
    }

    // --------------------------------------------------------------- cancel

    @Override
    @Transactional
    public Booking complete(UUID appointmentId, boolean attended) {
        Appointment a = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found"));

        // Staff and admin only. A client marking their own visit complete would
        // make the outcome data self-reported on both ends.
        if (!CurrentUser.isAdmin()) {
            boolean sameBranch = CurrentUser.hasRole(Role.STAFF)
                    && a.getBranch() != null
                    && CurrentUser.branchId()
                        .map(b -> b.equals(a.getBranch().getId()))
                        .orElse(false);
            if (!sameBranch) {
                // 404 rather than 403: confirming the id exists is itself a leak.
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
            }
        }

        if (a.getStatus() == AppointmentStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That visit was cancelled. It cannot be marked as attended.");
        }
        if (a.getStartTime().isAfter(LocalDateTime.now(ZoneId.of(timezone)))) {
            // The clock is not the authority here - staff are - but a visit that
            // has not begun cannot yet have happened, and allowing it would let
            // a mis-tap create outcome data for next Tuesday.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That visit has not started yet.");
        }

        AppointmentStatus was = a.getStatus();
        a.setStatus(attended ? AppointmentStatus.COMPLETED : AppointmentStatus.NO_SHOW);
        Appointment saved = appointmentRepository.save(a);

        auditAction(saved, attended ? "APPOINTMENT_COMPLETED" : "APPOINTMENT_NO_SHOW",
                "was=" + was + " by=" + CurrentUser.email().orElse("unknown"));
        return toDto(saved);
    }

    @Override
    @Transactional
    public Booking recordOutcome(UUID appointmentId, OutcomeRequest request) {
        Appointment a = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found"));

        boolean owns = a.getCustomer() != null
                && CurrentUser.id().map(u -> u.equals(a.getCustomer().getId())).orElse(false);
        if (!owns && !CurrentUser.isAdmin() && !CurrentUser.hasRole(Role.STAFF)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        if (a.getStatus() != AppointmentStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "We can only record how a visit went once the spa has marked it "
                    + "completed.");
        }

        Forms form = a.getForm();
        if (form == null || form.getPainPoints().isEmpty()) {
            // A leisure booking, or a walk-in with no assessment. There is
            // nothing to score, and inventing a pain point to hold a number
            // would put a complaint on a record that never had one.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This visit has no pain map to score.");
        }

        Map<UUID, PatientIntake> mine = form.getPainPoints().stream()
                .collect(Collectors.toMap(PatientIntake::getId, x -> x));

        List<OutcomeScore> scores = request == null || request.scores() == null
                ? List.of() : request.scores();
        int written = 0;
        for (OutcomeScore s : scores) {
            if (s == null || s.painPointId() == null || s.score() == null) {
                continue;
            }
            if (s.score() < 0 || s.score() > 10) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A pain score must be between 0 and 10.");
            }
            PatientIntake point = mine.get(s.painPointId());
            if (point == null) {
                // The id belongs to somebody else's assessment. Refuse the whole
                // submission rather than silently writing the ones that matched.
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "That pain point is not part of this visit.");
            }
            point.setPainScoreAfter(s.score());
            written++;
        }
        if (written == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No scores were submitted.");
        }

        // Forms is the aggregate root - the pain points are cascaded from it and
        // are never saved on their own. Session 1's rule, still holding.
        formsRepository.save(form);
        auditAction(a, "OUTCOME_RECORDED", "points=" + written + "/" + mine.size());
        return toDto(a);
    }

    @Override
    @Transactional
    public Booking cancel(UUID appointmentId, String reason) {
        Appointment a = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found"));

        assertCanCancel(a);

        if (a.getStatus() == AppointmentStatus.CANCELLED) {
            // Idempotent on purpose. A double-tap on Cancel is not an error, and
            // returning 409 here would make the UI show a failure for something
            // that is already true.
            return toDto(a);
        }
        if (a.getStatus() == AppointmentStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That visit has already been completed. The front desk can correct "
                    + "the record if it is wrong.");
        }

        a.setStatus(AppointmentStatus.CANCELLED);
        Appointment saved = appointmentRepository.save(a);

        // A separate action, not a second APPOINTMENT_CREATED row. The audit log
        // has to answer "who released this therapist, and when" - that is the
        // question a double-booking dispute actually turns on.
        try {
            AuditLog row = new AuditLog();
            row.setAction("APPOINTMENT_CANCELLED");
            row.setEntityType("Appointment");
            row.setEntityId(saved.getId());
            row.setBranch(saved.getBranch());
            row.setActor(saved.getCustomer());
            row.setOriginNodeId(nodeId);
            row.setDetails(clip("cancelledBy=" + CurrentUser.email().orElse("unknown")
                    + " start=" + saved.getStartTime()
                    + " service=" + saved.getService().getName()
                    + " therapist=" + saved.getTherapist().getId()
                    + " reason=\"" + (reason == null ? "" : reason.replace('"', '\''))
                    + "\"", 1000));
            auditLogRepository.save(row);
        } catch (Exception ignored) {
            // An audit failure must not strand a client with a booking they have
            // already been told is cancelled.
        }
        return toDto(saved);
    }

    /**
     * Who may cancel what.
     *
     * A customer gets their own, and only before it starts - once the hour has
     * arrived the therapist is standing there and it is the counter's call, not
     * an app's. Staff get their whole branch with no time limit, because a
     * client walking out mid-session is a real event and the day sheet has to be
     * able to say so.
     */
    private void assertCanCancel(Appointment a) {
        if (CurrentUser.isAdmin()) {
            return;
        }

        if (CurrentUser.hasRole(Role.STAFF)) {
            boolean sameBranch = a.getBranch() != null
                    && CurrentUser.branchId()
                        .map(b -> b.equals(a.getBranch().getId()))
                        .orElse(false);
            if (!sameBranch) {
                // 404, not 403 - telling a stranger the id exists is itself a leak.
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
            }
            return;
        }

        boolean owns = a.getCustomer() != null
                && CurrentUser.id().map(u -> u.equals(a.getCustomer().getId())).orElse(false);
        if (!owns) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        if (!a.getStartTime().isAfter(LocalDateTime.now(ZoneId.of(timezone)))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That visit has already started. Please speak to the front desk.");
        }
    }

    // ---------------------------------------------------------------- shared

    private Forms loadAndAuthorise(UUID formId) {
        if (formId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "formId is required");
        }
        Forms form = formsRepository.findById(formId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Form not found"));

        if (CurrentUser.isAdmin()) {
            return form;
        }
        if (CurrentUser.hasRole(Role.STAFF)) {
            UUID own = CurrentUser.branchId().orElse(null);
            if (own != null && form.getBranch() != null && own.equals(form.getBranch().getId())) {
                return form;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your branch");
        }
        UUID me = CurrentUser.id().orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Not authenticated"));
        if (form.getUser() == null || !me.equals(form.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your assessment");
        }
        return form;
    }

    /**
     * The consent record.
     *
     * There is no Confirm button in the conversational flow, so the client's own
     * words are what authorised the booking. Storing them is what makes this
     * auditable rather than merely convenient.
     */
    /**
     * Write the appointment, and translate the database's veto into an answer.
     *
     * The EXCLUDE constraints added in V2 are the backstop for the one case
     * assign() cannot cover: two transactions passing the same check four
     * milliseconds apart. When that happens the loser's INSERT is rejected by
     * Postgres, and the client must be told "that time just went" - not handed a
     * 500 for a race the system handled correctly.
     *
     * saveAndFlush, not save: JPA is free to defer the INSERT to commit, by
     * which point this catch block is long out of scope and the violation
     * surfaces as an unhandled 500 from somewhere unrelated.
     */
    private Appointment writeOrConflict(Appointment a) {
        try {
            return appointmentRepository.saveAndFlush(a);
        } catch (DataIntegrityViolationException e) {
            LOG.warn("Appointment rejected by the database at {} for therapist {}: {}",
                    a.getStartTime(),
                    a.getTherapist() == null ? null : a.getTherapist().getId(),
                    e.getMostSpecificCause().getMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That time was just taken. Please choose another.");
        }
    }

    /**
     * An audit row for something OTHER than creation.
     *
     * A separate helper rather than a parameter on audit(): that one hard-codes
     * APPOINTMENT_CREATED and is called from the two write paths, and widening
     * it would touch working code to serve a new caller.
     */
    private void auditAction(Appointment a, String action, String details) {
        try {
            AuditLog row = new AuditLog();
            row.setAction(action);
            row.setEntityType("Appointment");
            row.setEntityId(a.getId());
            row.setBranch(a.getBranch());
            row.setActor(a.getCustomer());
            row.setOriginNodeId(nodeId);
            row.setDetails(clip(details + " start=" + a.getStartTime(), 1000));
            auditLogRepository.save(row);
        } catch (Exception ignored) {
            // An audit failure must not undo something the client has been told
            // already happened.
        }
    }

    private void audit(Appointment a, String consentText) {
        try {
            AuditLog row = new AuditLog();
            row.setAction("APPOINTMENT_CREATED");
            row.setEntityType("Appointment");
            row.setEntityId(a.getId());
            row.setBranch(a.getBranch());
            // Null for a walk-in - there is no account to attribute it to. The
            // details line carries who recorded it instead.
            row.setActor(a.getCustomer());
            row.setOriginNodeId(nodeId);
            row.setDetails(clip("source=CHATBOT start=" + a.getStartTime()
                    + " service=" + a.getService().getName()
                    + " therapist=" + a.getTherapist().getId()
                    + " consent=\"" + (consentText == null ? "" : consentText.replace('"', '\''))
                    + "\"", 1000));
            auditLogRepository.save(row);
        } catch (Exception ignored) {
            // An audit failure must not undo a booking the client already has.
        }
    }

    @Override
    public List<ScheduleRow> schedule(LocalDate date, UUID branchId) {
        LocalDate day = date == null ? LocalDate.now(ZoneId.of(timezone)) : date;

        List<Appointment> found;
        if (CurrentUser.isAdmin()) {
            // An administrator may name a branch (Figure 3.3 context switching)
            // or omit it for the whole business. Staff never get the choice.
            found = (branchId == null
                    ? appointmentRepository.findAll()
                    : appointmentRepository.findByBranchId(branchId)).stream()
                    .filter(a -> a.getStartTime().toLocalDate().equals(day))
                    .toList();
        } else if (CurrentUser.hasRole(Role.STAFF)) {
            UUID branch = CurrentUser.branchId().orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Staff account has no branch assigned"));
            found = appointmentRepository.findByBranchIdAndStartTimeBetween(
                    branch, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff only");
        }

        return found.stream()
                .sorted(Comparator.comparing(Appointment::getStartTime))
                .map(a -> new ScheduleRow(
                        a.getId(),
                        a.getStartTime().format(TIME_ONLY),
                        a.getStartTime(),
                        a.getEndTime(),
                        (int) Duration.between(a.getStartTime(), a.getEndTime()).toMinutes(),
                        clientName(a),
                        a.getService().getName(),
                        a.getTherapist().getFirstName() + " " + a.getTherapist().getLastName(),
                        a.getRoom().getName(),
                        a.getBranch().getName(),
                        a.getStatus().name(),
                        a.getPaymentStatus().name(),
                        a.getSource().name(),
                        a.getForm() != null,
                        a.getForm() == null ? null : a.getForm().getId()))
                .toList();
    }

    /**
     * Visits that have not been answered for yet. A day still "owes" us one of
     * these for every row whose time has passed and whose status is still open.
     */
    private static final Set<AppointmentStatus> UNANSWERED = EnumSet.of(
            AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED, AppointmentStatus.IN_PROGRESS);

    @Override
    public List<DayLoad> month(YearMonth month, UUID branchId) {
        ZoneId zone = ZoneId.of(timezone);
        YearMonth ym = month == null ? YearMonth.from(LocalDate.now(zone)) : month;
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime until = ym.plusMonths(1).atDay(1).atStartOfDay();

        List<Appointment> found;
        if (CurrentUser.isAdmin()) {
            found = (branchId == null
                    ? appointmentRepository.findAll()
                    : appointmentRepository.findByBranchId(branchId)).stream()
                    .filter(a -> inWindow(a, from, until))
                    .toList();
        } else if (CurrentUser.hasRole(Role.STAFF)) {
            // Same rule as schedule(): the branch is read from the token and
            // never from the query string, so a wider window is not a wider
            // permission.
            UUID branch = CurrentUser.branchId().orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Staff account has no branch assigned"));
            found = appointmentRepository
                    .findByBranchIdAndStartTimeBetween(branch, from, until).stream()
                    .filter(a -> inWindow(a, from, until))
                    .toList();
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff only");
        }

        LocalDateTime now = LocalDateTime.now(zone);
        Map<LocalDate, List<Appointment>> byDay = found.stream()
                .collect(Collectors.groupingBy(a -> a.getStartTime().toLocalDate()));

        // Every day of the month is returned, including the empty ones. A caller
        // drawing a grid must never have to guess whether a missing date means
        // "nothing booked" or "we did not look that far".
        List<DayLoad> out = new ArrayList<>();
        for (LocalDate d = ym.atDay(1); d.isBefore(ym.plusMonths(1).atDay(1)); d = d.plusDays(1)) {
            List<Appointment> rows = byDay.getOrDefault(d, List.of());
            int owed = (int) rows.stream()
                    .filter(a -> a.getStartTime().isBefore(now))
                    .filter(a -> UNANSWERED.contains(a.getStatus()))
                    .count();
            out.add(new DayLoad(d, rows.size(), owed));
        }
        return out;
    }

    /** Half-open: the first instant of the month counts, the first instant of
     *  the next one does not. `Between` is inclusive at both ends, which would
     *  otherwise let a midnight row on the 1st be counted in two months. */
    private static boolean inWindow(Appointment a, LocalDateTime from, LocalDateTime until) {
        LocalDateTime t = a.getStartTime();
        return !t.isBefore(from) && t.isBefore(until);
    }

    private static final DateTimeFormatter TIME_ONLY =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    /** Who is free for this window. Both booking paths go through it. */
    private record Assignment(Therapist therapist, Room room) {
    }

    /**
     * Pick a free therapist and a free room, or refuse.
     *
     * Called INSIDE the transaction by both paths. This is what makes Process
     * Rule #4 hold across channels: the assistant and the front desk cannot
     * award the same therapist, because whichever transaction commits second
     * re-reads these rows and finds them taken.
     */
    /**
     * Pick a free therapist and room, applying every rule availability applied.
     *
     * `want` is the client's preference about their therapist's sex, or null for
     * none. It is checked here as well as when times were offered, because those
     * are two moments and the roster can change between them - and because a
     * preference honoured only by the screen is worse than none at all: the
     * client is told they will be treated by a woman, and then is not.
     */
    /**
     * Every therapist who could take this visit, in this branch, at this time.
     *
     * ONE definition of "free", used by both openings() and assign(). Two
     * definitions would eventually disagree, and the shape of that bug is a
     * screen offering a therapist the booking then refuses - which is worse
     * than not offering the choice at all.
     */
    private List<Therapist> freeTherapists(UUID branchId, LocalDateTime start,
                                           LocalDateTime end, Sex want) {
        // Status is only consulted for a visit starting TODAY. Without this a
        // walk-in could be handed to somebody the front desk had just marked
        // off duty, thirty seconds after the screen stopped offering them.
        boolean today = start.toLocalDate().equals(LocalDate.now(ZoneId.of(timezone)));

        return therapistRepository.findByBranchIdAndActiveTrue(branchId).stream()
                .filter(t -> want == null || want == t.getSex())
                .filter(t -> !today || onDuty(t))
                .filter(t -> !appointmentRepository
                        .existsByTherapistIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                                t.getId(), BLOCKING, end, start))
                .toList();
    }

    /** Every room free for the whole visit. Same contract as freeTherapists. */
    private List<Room> freeRooms(UUID branchId, LocalDateTime start, LocalDateTime end) {
        return roomRepository.findByBranchIdAndActiveTrue(branchId).stream()
                .filter(r -> !appointmentRepository
                        .existsByRoomIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                                r.getId(), BLOCKING, end, start))
                .toList();
    }

    /**
     * Pick the therapist and room, honouring an explicit choice when there is
     * one.
     *
     * wantTherapist / wantRoom are the client's pick and are usually null. When
     * one is set it is a FILTER, not a hint: if that person or that room is not
     * free, this fails rather than quietly substituting somebody else. A client
     * who chose a female therapist for a reason must never be handed a man
     * because the system decided it knew better.
     *
     * An explicit pick also OVERRIDES the assessment's sex preference - the
     * client is looking at the actual list and choosing from it, which is a
     * later and better-informed statement of the same wish.
     */
    private Assignment assign(UUID branchId, LocalDateTime start, LocalDateTime end,
                              Sex want, UUID wantTherapist, UUID wantRoom) {
        Sex effective = wantTherapist == null ? want : null;

        Therapist therapist = freeTherapists(branchId, start, end, effective).stream()
                .filter(t -> wantTherapist == null || wantTherapist.equals(t.getId()))
                .findFirst()
                // Say WHICH constraint bit. "That time was just taken", "no
                // female therapist is free then" and "the therapist you chose
                // has just been booked" send the client to do three different
                // things, and only one of them is trying another time.
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        wantTherapist != null
                                ? "The therapist you chose has just been booked for that time. "
                                  + "Please choose someone else, or another time."
                                : want == null
                                        ? "That time was just taken"
                                        : "No " + want.getDisplayName().toLowerCase()
                                          + " therapist is free at that time. Please choose "
                                          + "another time, or change your preference on your "
                                          + "assessment."));

        Room room = freeRooms(branchId, start, end).stream()
                .filter(r -> wantRoom == null || wantRoom.equals(r.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        wantRoom != null
                                ? "The room you chose has just been taken for that time. "
                                  + "Please choose another room, or another time."
                                : "That time was just taken"));

        return new Assignment(therapist, room);
    }

    /**
     * S5 - the client who arrived at the counter (B77).
     *
     * No account, so no `customer`; the schema's check constraint requires a
     * name instead. No form either: Process Rule #2 gates the ASSISTANT behind a
     * completed assessment, not the front desk, and refusing to record a paying
     * client because they have not filled in a questionnaire would be the app
     * obstructing the business it is meant to serve.
     */
    @Override
    @Transactional
    public Booking bookWalkIn(WalkInRequest req) {
        if (req == null || req.serviceId() == null || req.start() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "serviceId and start are required");
        }
        String name = req.name() == null ? "" : req.name().trim();
        if (name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A name is required - it is the only thing identifying this visit");
        }

        UUID branchId = CurrentUser.isAdmin()
                ? branchOfSingleOr(req)
                : CurrentUser.branchId().orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Staff account has no branch assigned"));

        Branch branch = branchRepository.findById(branchId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Branch not found"));
        Massage service = massageRepository.findById(req.serviceId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));

        LocalDateTime start = req.start();
        LocalDateTime end = start.plusMinutes(service.getDurationMinute());

        // Deliberately NOT refused if it is in the past. A walk-in is often
        // typed in after the session has begun; the online path forbids it
        // because a client cannot book yesterday.

        // Idempotency: the front desk double-taps as readily as an agent retries.
        for (Appointment existing : appointmentRepository.findByBranchIdAndStartTimeBetween(
                branchId, start, start.plusMinutes(1))) {
            if (BLOCKING.contains(existing.getStatus())
                    && existing.getService().getId().equals(service.getId())
                    && name.equalsIgnoreCase(existing.getWalkInName())) {
                return toDto(existing);
            }
        }

        // Join the counter assessment to the visit it produced, when there is
        // one. Without this a walk-in's pain scores sit in a form nothing points
        // at, which is the same read-path gap as B92.
        Forms walkInForm = null;
        if (req.formId() != null) {
            walkInForm = formsRepository.findById(req.formId()).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Assessment not found"));
            if (walkInForm.getBranch() == null
                    || !branchId.equals(walkInForm.getBranch().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "That assessment belongs to another branch");
            }
        }

        // A walk-in assessed at the counter may have stated a preference too.
        // Staff assign at the counter by talking to the client, so the walk-in
        // path passes no explicit pick - it keeps the original behaviour.
        Assignment assignment = assign(branchId, start, end,
                walkInForm == null ? null : walkInForm.getTherapistPreference(),
                null, null);

        Appointment a = new Appointment();
        a.setBranch(branch);
        a.setCustomer(walkInForm == null ? null : walkInForm.getUser());
        a.setWalkInName(name);
        a.setWalkInContact(blankToNull(req.contact()));
        a.setForm(walkInForm);
        a.setService(service);
        a.setTherapist(assignment.therapist());
        a.setRoom(assignment.room());
        a.setStartTime(start);
        a.setEndTime(end);
        a.setStatus(AppointmentStatus.CONFIRMED);
        a.setPaymentStatus(PaymentStatus.UNPAID);
        a.setSource(BookingSource.STAFF_MANUAL);
        a.setPriceAtBooking(service.getPrice());
        a.setOriginNodeId(nodeId);
        a.setNotes(blankToNull(req.notes()));

        Appointment saved = writeOrConflict(a);
        audit(saved, "Walk-in recorded at the counter by "
                + CurrentUser.email().orElse("staff"));
        return toDto(saved);
    }

    /** An administrator has no branch of their own, so one branch or say which. */
    private UUID branchOfSingleOr(WalkInRequest req) {
        List<Branch> all = branchRepository.findAll();
        if (all.size() == 1) {
            return all.get(0).getId();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "An administrator must record a walk-in from the branch's own account");
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private Booking toDto(Appointment a) {
        return new Booking(
                a.getId(),
                a.getService().getId(),
                a.getService().getName(),
                a.getStartTime(),
                a.getEndTime(),
                a.getStartTime().format(LABEL),
                (int) Duration.between(a.getStartTime(), a.getEndTime()).toMinutes(),
                a.getPriceAtBooking(),
                a.getTherapist().getFirstName() + " " + a.getTherapist().getLastName(),
                a.getRoom().getName(),
                a.getBranch().getName(),
                a.getStatus().name(),
                a.getPaymentStatus().name(),
                a.getSource().name(),
                a.getCreatedAt());
    }

    /** The account holder's name, or the walk-in's. One of the two always exists. */
    private static String clientName(Appointment a) {
        if (a.getCustomer() != null) {
            return a.getCustomer().getFirstName() + " " + a.getCustomer().getLastName();
        }
        return a.getWalkInName() == null ? "Walk-in" : a.getWalkInName();
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
