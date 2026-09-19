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
