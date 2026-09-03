package com.hilotspa.backend.services;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.hilotspa.backend.model.BookingDtos.Availability;
import com.hilotspa.backend.model.BookingDtos.BookRequest;
import com.hilotspa.backend.model.BookingDtos.Booking;
import com.hilotspa.backend.model.BookingDtos.Openings;
import com.hilotspa.backend.model.BookingDtos.OutcomeRequest;
import com.hilotspa.backend.model.BookingDtos.DayLoad;
import com.hilotspa.backend.model.BookingDtos.ScheduleRow;
import com.hilotspa.backend.model.BookingDtos.WalkInRequest;

public interface BookingService {

    /**
     * Open start times for a service, in the branch this assessment belongs to.
     *
     * A slot is open when a therapist AND a room are both free for the whole
     * duration. Identical start times are collapsed: the client picks a time,
     * not a person.
     */
    Availability availability(UUID formId, UUID serviceId, LocalDate from, int days);

    /**
     * Who and where is free at ONE start time.
     *
     * The second half of booking: availability() answers WHEN, this answers WHO
     * and WHERE. Deliberately a separate call - it is only asked once a time is
     * settled, and asking it for every slot in a week would be a query per slot
     * to answer a question most clients never ask.
     */
    Openings openings(UUID formId, UUID serviceId, LocalDateTime start);

    /**
     * The same question from the counter, where there is no client and no form.
     *
     * openings() is keyed to an assessment: it authorises against the form's
     * owner and narrows by the sex the client asked for. A walk-in has neither,
     * so it cannot borrow that method - and the front desk still needs to know
     * who is free at half past two before it promises anybody.
     *
     * Branch comes from the token, never the request. That is the same rule
     * every staff query follows and the reason a front desk cannot read another
     * branch's roster.
     */
    Openings counterOpenings(UUID serviceId, LocalDateTime start, UUID branchId);

    /**
     * Create the appointment.
     *
     * Re-checks availability INSIDE the transaction. Two clients can ask for the
     * same 3 PM four seconds apart, and only a database transaction can make one
     * of them lose - which is why the model proposes and this method decides.
     */
    Booking book(BookRequest request);

    /**
     * S5 - record a client who walked in, at the caller's own branch.
     *
     * Goes through exactly the same therapist-and-room assignment and the same
     * transaction as an online booking, so a walk-in cannot be given a therapist
     * the assistant has already committed. Written with source = STAFF_MANUAL and
     * no form: Process Rule #2 gates the ASSISTANT behind a completed
     * assessment, not the front desk.
     */
    Booking bookWalkIn(WalkInRequest request);

    /**
     * One day's sheet for the caller's branch, earliest first.
     *
     * STAFF only ever get their own branch - the branch is read from the token,
     * never from the query string, so there is no parameter to tamper with.
     */
    List<ScheduleRow> schedule(LocalDate date, UUID branchId);

    /**
     * How loaded each day of one month is, for the day-sheet calendar.
     *
     * Same role and branch rules as schedule() - STAFF are pinned to the branch
     * on their token and ADMIN may name one - because it answers the same
     * question over a wider window and must not be a way around them.
     *
     * Days with nothing booked are returned as zero rows rather than omitted,
     * so the caller never has to decide whether a missing date means "quiet" or
     * "we did not look".
     */
    List<DayLoad> month(YearMonth month, UUID branchId);

    /**
     * Staff record what actually happened - 4.x outcome loop.
     *
     * COMPLETED and NO_SHOW have existed on AppointmentStatus since Sprint 0 and
     * nothing has ever set either. A human who was in the room says which it
     * was; guessing from the clock would mark every no-show as a completed
     * visit and quietly corrupt the one dataset the paper's outcome claim rests
     * on.
     */
    Booking complete(UUID appointmentId, boolean attended);

    /**
     * The client records how they feel afterwards.
     *
     * Only their own visit, and only one the spa has marked COMPLETED - a score
     * "after" a session that did not happen is not data, it is noise with a
     * timestamp.
     */
    Booking recordOutcome(UUID appointmentId, OutcomeRequest request);

    /** The caller's own appointments, newest first. Branch-scoped for staff. */
    List<Booking> mine();

    /**
     * Cancel a booking - 2.32.
     *
     * The row is marked CANCELLED, never deleted. A deleted appointment takes
     * its audit trail, its price at booking and the fact that it ever existed
     * with it; a cancelled one releases the therapist and the room (CANCELLED
     * is not in BLOCKING) while staying on the record, which is what a clinic
     * needs and what the paper's audit-log claim requires.
     *
     * A client may only cancel their own, and only before it starts. Staff may
     * cancel anything at their own branch, including a visit already under way,
     * because a client who leaves halfway is a real thing that happens at a
     * counter and pretending otherwise just puts a wrong row on the day sheet.
     */
    Booking cancel(UUID appointmentId, String reason);
}
