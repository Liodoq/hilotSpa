package com.hilotspa.backend.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hilotspa.backend.entities.SyncLog;
import com.hilotspa.backend.model.SyncDtos.Hello;
import com.hilotspa.backend.repository.BranchRepository;
import com.hilotspa.backend.repository.SyncLogRepository;

/**
 * What this node tells a PEER (task 3.2).
 *
 * Separate from SyncController, which is the administrator's view under
 * /api/v1/admin. Keeping them apart from the start means the peer-facing route
 * can never accidentally inherit a human's permissions, and the human-facing
 * one can never be reached with the shared node token.
 *
 * Everything under /api/v1/sync is guarded by SyncTokenFilter, not by the JWT
 * chain.
 */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncPeerController {

    /** Bump when the peer contract changes, so a mismatch is visible not subtle. */
    private static final int PROTOCOL = 1;

    @Autowired private SyncLogRepository syncLogRepository;
    @Autowired private BranchRepository branchRepository;

    @Value("${hilotspa.node.id:local-dev}")            private String nodeId;
    @Value("${hilotspa.node.name:Local}")              private String nodeName;
    @Value("${hilotspa.node.branch-id:}")              private String branchIdRaw;
    @Value("${hilotspa.booking.timezone:Asia/Manila}") private String timezone;

    @GetMapping("/hello")
    public ResponseEntity<Hello> hello() {
        SyncLog latest = syncLogRepository.findFirstByOriginNodeIdOrderByIdDesc(nodeId);

        UUID branch = null;
        String branchName = null;
        if (branchIdRaw != null && !branchIdRaw.isBlank()) {
            try {
                branch = UUID.fromString(branchIdRaw.trim());
                branchName = branchRepository.findById(branch)
                        .map(b -> b.getName()).orElse(null);
            } catch (IllegalArgumentException ignored) {
                // A malformed id in configuration must not take this endpoint
                // down. The peer still needs to know we are alive, and
                // NodeController already says so at startup.
                branch = null;
            }
        }

        return ResponseEntity.ok(new Hello(
                nodeId, nodeName, branch, branchName,
                latest == null ? 0L : latest.getId(),
                LocalDateTime.now(ZoneId.of(timezone)),
                PROTOCOL));
    }
}
