package com.hilotspa.backend.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hilotspa.backend.entities.SyncLog;
import com.hilotspa.backend.repository.SyncLogRepository;

/**
 * The sync log, for an administrator to look at (task 3.1).
 *
 * Under /api/v1/admin, so ADMIN only. This is NOT the endpoint a peer will
 * read - that one comes with the node registry (3.2) and authenticates with a
 * shared node secret rather than a user's token. Keeping them separate from
 * the start means the peer-facing route can never accidentally inherit a
 * human's permissions.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class SyncController {

    @Autowired private SyncLogRepository syncLogRepository;

    @Value("${hilotspa.node.id:local-dev}") private String nodeId;

    public record SyncRow(Long id, String originNodeId, String entityType,
                          String entityId, String action, String branchId,
                          String occurredAt) {
    }

    /** This node's identity and how far its own log has got. */
    public record SyncStatus(String nodeId, long watermark, int shown, List<SyncRow> recent) {
    }

    @GetMapping("/sync-log")
    public ResponseEntity<SyncStatus> log() {
        SyncLog latest = syncLogRepository.findFirstByOriginNodeIdOrderByIdDesc(nodeId);
        List<SyncRow> rows = syncLogRepository.findTop200ByOrderByIdDesc().stream()
                .map(r -> new SyncRow(
                        r.getId(), r.getOriginNodeId(), r.getEntityType(),
                        String.valueOf(r.getEntityId()), r.getAction(),
                        r.getBranchId() == null ? null : String.valueOf(r.getBranchId()),
                        String.valueOf(r.getOccurredAt())))
                .toList();
        return ResponseEntity.ok(new SyncStatus(
                nodeId, latest == null ? 0L : latest.getId(), rows.size(), rows));
    }
}
