package com.hilotspa.backend.services;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.hilotspa.backend.model.BookingDtos.Availability;
import com.hilotspa.backend.model.BookingDtos.BookRequest;
import com.hilotspa.backend.model.BookingDtos.Booking;
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
