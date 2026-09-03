package com.hilotspa.backend.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Availability and booking. See backend/n8n/BOOKING.md. */
public final class BookingDtos {

    private BookingDtos() {
    }

    /**
     * One open start time.
     *
     * Identical start times are still collapsed here: the first question is
     * WHEN. Who and where is a second, optional question answered by
     * /openings once a time is settled - see Openings below.
     *
     * `label` is rendered here, in Java, on purpose. Left to the model, times
     * get reformatted, translated, or quietly shifted by an hour.
     */
    public record Slot(
            String slotId,
            LocalDateTime start,
            String label) {
    }

    public record Availability(
            String timezone,
            LocalDateTime now,
            UUID serviceId,
            String serviceName,
            int durationMinutes,
            BigDecimal price,
            List<Slot> slots) {
    }

    /** A therapist the client may choose, at one specific start time. */
    public record OpenTherapist(
            UUID id,
            String firstName,
            /** Display text ("Female"), not the enum name - this is for a chip. */
            String sex) {
    }

    /** A room the client may choose, at one specific start time. */
    public record OpenRoom(
            UUID id,
            String name,
            String imageName) {
    }

    /**
     * Who and where is free at ONE start time - the second half of booking.
     *
     * Both lists are what is free at this instant, not what exists. An empty
     * list means the time went while the client was reading, and the honest
     * answer is to send them back to the times.
     *
     * Choosing is optional by design. A client with no opinion books exactly as
     * before and the server assigns; a client who cares picks. Paper Process
     * Rule #4 speaks of a provisional lock, and there is none here - the
     * guarantee is the database exclusion constraint at write time, and a lock
     * we did not build is not a lock we should pretend to have.
     */
    public record Openings(
            LocalDateTime start,
            String label,
            List<OpenTherapist> therapists,
            List<OpenRoom> rooms) {
    }

    public record BookRequest(
            UUID formId,
            UUID serviceId,
            LocalDateTime start,
            /**
             * The therapist the client picked, or null for "any available".
             * Null is the normal case and keeps the original one-tap flow.
             */
            UUID therapistId,
            /** The room the client picked, or null for "any available". */
            UUID roomId,
            /**
             * Agents retry on timeout. Without this, one slow network call books
             * the same client twice and nobody finds out until they arrive.
             */
            String idempotencyKey,
            /** The exact message that authorised this, stored in the audit row. */
            String consentText) {
    }

    public record Booking(
            UUID id,
            UUID serviceId,
            String serviceName,
            LocalDateTime start,
            LocalDateTime end,
            String label,
            int durationMinutes,
            BigDecimal price,
            String therapist,
            String room,
            String branch,
            String status,
            String paymentStatus,
            String source,
            /** When the booking was made - not when the visit is. */
            java.time.LocalDateTime bookedAt) {
    }

    /**
     * One line of a branch's day sheet.
     *
     * Carries the client's name, which Booking deliberately does not - a client
     * reading their own booking has no business being told who else is in.
     * This record is only ever returned on a STAFF/ADMIN route.
     */
    public record ScheduleRow(
            UUID id,
            String time,
            LocalDateTime start,
            LocalDateTime end,
            int durationMinutes,
            String client,
            String serviceName,
            String therapist,
            String room,
            String branch,
            String status,
            String paymentStatus,
            String source,
            boolean hasAssessment,
            UUID formId) {
    }

    /**
     * One square of the month grid.
     *
     * Two numbers, never the rows. Drawing thirty bars does not need thirty
     * client names, and shipping thirty full day sheets to do it would put a
     * branch's entire month on the wire to render a strip of hairlines.
     *
     * `owed` is the count of visits on that day whose time has passed and which
     * nobody has closed off. It is the only reason this endpoint is worth
     * having: those rows are otherwise unreachable from every screen we ship,
     * and until a human says whether the client came, the visit can never
     * collect a painScoreAfter.
     */
    public record DayLoad(
            LocalDate date,
            int total,
            int owed) {
    }

    /**
     * S5 — a client who arrived at the counter with no account (B77).
     *
     * Staff-entered, so the branch comes from the token and there is no branchId
     * to pass. `name` is required precisely because it is the only thing
     * identifying the visit; the schema will not accept a row without it.
     */
    public record WalkInRequest(
            UUID serviceId,
            LocalDateTime start,
            String name,
            String contact,
            String notes,
            /**
             * The walk-in's assessment, when staff took one at the counter.
             * Optional - plenty of walk-ins are booked without one - but when it
             * is present the visit and the assessment are joined, which is what
             * makes a walk-in's pain scores usable in Chapter IV. B85.
             */
            UUID formId,
            /**
             * Who and where, when the counter chose rather than leaving it to
             * the server. Both nullable, and null stays the default: most
             * walk-ins do not care, and the server picking is what has always
             * made the two booking channels unable to award the same therapist.
             *
             * A pick that is no longer free is REFUSED, not quietly swapped.
             * Staff standing in front of the client have just told them a name.
             */
            UUID therapistId,
            UUID roomId,
            /**
             * Which branch this walk-in belongs to. ADMINISTRATORS ONLY.
             *
             * Staff never get to name a branch - theirs comes from the token,
             * which is the rule every staff query follows and the reason a front
             * desk cannot write to another branch. An administrator has no
             * branch of their own, so context switching (Figure 3.3) is the only
             * way they can record one, and until now that was impossible with
             * two branches on file: the resolver threw unless exactly one
             * existed.
             */
            UUID branchId,
            /** Front desks double-tap. Same idea as the agent retry guard. */
            String idempotencyKey) {
    }

    /**
     * One pain point's score after the visit - the other half of the paper's
     * before/after.
     *
     * painScoreBefore has been collected since Sprint 1 and painScoreAfter has
     * been a column nothing wrote. A before with no after is not a measurement,
     * it is half of one, and Chapter IV cannot compute anything from it.
     */
    public record OutcomeScore(UUID painPointId, Integer score) {
    }

    /** What the client submits after a completed visit. */
    public record OutcomeRequest(List<OutcomeScore> scores) {
    }

    /** 409 body: the slot went while the client was deciding. */
    public record SlotTaken(
            String message,
            List<Slot> alternatives) {
    }
}
