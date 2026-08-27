package com.hilotspa.backend.model;

import java.math.BigDecimal;
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
     * The client is not choosing a therapist, so identical start times are
     * collapsed - which therapist takes the session is assigned at booking.
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

    public record BookRequest(
            UUID formId,
            UUID serviceId,
            LocalDateTime start,
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
            /** Front desks double-tap. Same idea as the agent retry guard. */
            String idempotencyKey) {
    }

    /** 409 body: the slot went while the client was deciding. */
    public record SlotTaken(
            String message,
            List<Slot> alternatives) {
    }
}
