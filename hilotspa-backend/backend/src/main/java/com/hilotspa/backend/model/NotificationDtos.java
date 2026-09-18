package com.hilotspa.backend.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A4 - the notification log, as an administrator reads it.
 *
 * Carries the client's name, which no client-facing DTO in this system does.
 * That is deliberate and it is why this record lives here rather than being
 * bolted onto a booking: the log is the spa's evidence that it wrote to a named
 * person, and it is only ever returned on an ADMIN route.
 */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationRow(
            UUID id,
            UUID appointmentId,
            String client,
            String serviceName,
            String branchName,
            LocalDateTime visitAt,
            String kind,
            String channel,
            String status,
            String recipient,
            int attempts,
            /** Why it was skipped, or what the mail server said. Null on a clean send. */
            String detail,
            String originNodeId,
            LocalDateTime createdAt,
            /** Set on SENT and on nothing else. */
            LocalDateTime sentAt) {
    }

    /**
     * One visit due on a chosen day, and whether it has been told yet.
     *
     * This is NOT the log. The log answers "what did we send"; this answers
     * "who is booked tomorrow, and which of them still has not heard from us" -
     * which is the question somebody standing at the counter actually has, and
     * the one a list of past sends cannot answer, because the client who was
     * never attempted has no row in it at all.
     */
    public record DueRow(
            UUID appointmentId,
            String client,
            String serviceName,
            String branchName,
            LocalDateTime visitAt,
            String recipient,
            /** Null when nothing has ever been attempted for this visit. */
            String status,
            int attempts,
            String detail,
            LocalDateTime sentAt,
            /**
             * Whether pressing Send could achieve anything.
             *
             * False only when there is no address to send to. A visit already
             * SENT is still sendable - re-sending to a client who says they
             * never got it is the whole reason this list exists.
             */
            boolean sendable,
            /** Why not, when sendable is false. */
            String blockedReason) {
    }

    /** What one run of the reminder job did. */
    public record RunResult(
            java.time.LocalDate visitDay,
            int sent,
            String note) {
    }
}
