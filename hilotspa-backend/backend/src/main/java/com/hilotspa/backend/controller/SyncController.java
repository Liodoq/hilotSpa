package com.hilotspa.backend.controller;

import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hilotspa.backend.entities.PeerNode;
import com.hilotspa.backend.entities.SyncLog;
import com.hilotspa.backend.model.SyncDtos.NodeView;
import com.hilotspa.backend.repository.PeerNodeRepository;
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
    @Autowired private PeerNodeRepository peerNodeRepository;

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

    /**
     * The cluster as this node sees it (task 3.2).
     *
     * "As this node sees it" is the honest framing and the reason the registry
     * is not replicated: whether a peer is reachable is a question only
     * answerable from where you are standing, and two nodes are entitled to
     * different answers at the same moment.
     */
    @GetMapping("/nodes")
    public ResponseEntity<List<NodeView>> nodes() {
        return ResponseEntity.ok(peerNodeRepository.findAll().stream()
                // Explicit parameter type, and !isSelf() rather than
                // .reversed(): chaining thenComparing after reversed() on an
                // inferred comparator is a well-known javac inference trap, and
                // the error it produces names neither the field nor the method.
                .sorted(Comparator.comparing((PeerNode n) -> !n.isSelf())
                        .thenComparing(n -> n.getName() == null ? "" : n.getName(),
                                String.CASE_INSENSITIVE_ORDER))
                .map(n -> new NodeView(
                        n.getNodeId(), n.getName(), n.getBaseUrl(), n.getBranchId(),
                        n.isSelf(), n.getState(), n.getLastSeenAt(),
                        n.getTheirWatermark(), n.getOurWatermark(), n.getLastError()))
                .toList());
    }
}
