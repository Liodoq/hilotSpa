package com.hilotspa.backend.model;

import java.time.LocalDateTime;
import java.util.UUID;

/** The peer-facing contract (tasks 3.2 and 3.3). */
public final class SyncDtos {

    private SyncDtos() {
    }

    /**
     * What a node says about itself when asked.
     *
     * Carries no client data of any kind, deliberately. This is the one
     * endpoint reachable from outside with something other than a user's token,
     * so it answers only "who am I and how far has my log got".
     */
    public record Hello(
            String nodeId,
            String nodeName,
            UUID branchId,
            String branchName,
            /** The highest sync_log id this node has written itself. */
            long watermark,
            LocalDateTime serverTime,
            /** Bumped when the peer contract changes, so a mismatch is visible. */
            int protocol) {
    }

    /**
     * One line of a peer's log: THAT something changed, never what it says.
     *
     * The payload is fetched separately, by id, from an endpoint with its own
     * rules. That split is the privacy boundary - a payload column here would
     * put a client's assessment inside the one table a peer is allowed to read.
     */
    public record Change(
            long id,
            String originNodeId,
            String entityType,
            UUID entityId,
            String action,
            UUID branchId,
            LocalDateTime occurredAt) {
    }

    /**
     * An appointment as a PEER receives it (task 3.3).
     *
     * Note what is not here: no customer id, no form id, no contact number, no
     * assessment. The account and the pain map stay on the node that recorded
     * them. What crosses is the fact of a visit - who, what, when, where - which
     * is what an administrator looking at both branches actually needs.
     *
     * clientName is DENORMALISED on purpose. Sending a customer id would be
     * useless to the receiver, whose users table has never heard of them, and
     * replicating the account to fix that would undo the boundary above.
     */
    public record AppointmentSnapshot(
            UUID id,
            UUID branchId,
            UUID serviceId,
            UUID therapistId,
            UUID roomId,
            String clientName,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String status,
            String paymentStatus,
            String source,
            java.math.BigDecimal priceAtBooking,
            String notes,
            String originNodeId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    /** One node, as the administrator's screen reads it. */
    public record NodeView(
            String nodeId,
            String name,
            String baseUrl,
            UUID branchId,
            boolean self,
            String state,
            LocalDateTime lastSeenAt,
            Long theirWatermark,
            long ourWatermark,
            String lastError) {
    }
}
