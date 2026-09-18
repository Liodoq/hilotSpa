package com.hilotspa.backend.controller;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hilotspa.backend.entities.Branch;
import com.hilotspa.backend.repository.BranchRepository;

import jakarta.annotation.PostConstruct;

/**
 * Which node am I, and which branch do I write for? (task 3.31)
 *
 * Unauthenticated, and under /api/v1/public because a visitor who has not
 * signed in still needs the answer: the page they are looking at has to name
 * the branch they are booking at, and until this existed it named whichever
 * branch was first in the alphabet.
 *
 * That was not cosmetic. A customer has no token and therefore no branch, so
 * the booking screen fell back to branches[0] - which meant a client standing
 * in Daraga, on the Daraga node, filed an appointment against Bulan. Every
 * other guarantee in this architecture rests on single-writer-per-partition,
 * and it was being broken at the point of entry by a default.
 *
 * Note what is NOT published here: no counts, no addresses, no peer list, no
 * sync state. A node's name and the branch it serves are already visible to
 * anyone who reads the page. Everything else about an operational deployment
 * is not a stranger's business, and the peer-facing endpoints (3.2) carry
 * their own secret rather than inheriting this one's openness.
 */
@RestController
@RequestMapping("/api/v1/public")
public class NodeController {

    private static final Logger LOG = LoggerFactory.getLogger(NodeController.class);

    @Autowired private BranchRepository branchRepository;

    @Value("${hilotspa.node.id:local-dev}")        private String nodeId;
    @Value("${hilotspa.node.name:Local Development}") private String nodeName;
    @Value("${hilotspa.node.branch-id:}")          private String branchIdRaw;

    /**
     * branchId is null when this node has not been told which branch it owns.
     * The client then behaves exactly as it did before this endpoint existed.
     */
    public record NodeIdentity(String nodeId, String nodeName,
                               String branchId, String branchName) {
    }

    /**
     * Said once, loudly, at startup rather than on every request.
     *
     * A node with no branch of its own is not broken - a single-node
     * deployment is a legitimate configuration and this is how it looks. But a
     * node that is one of TWO and has not been configured will quietly write
     * another branch's appointments, and that is precisely the failure this
     * class exists to stop. So it is stated at boot, where it is read, instead
     * of being discovered from the data three weeks later.
     */
    @PostConstruct
    void announce() {
        UUID id = ownBranchId();
        if (id == null) {
            LOG.warn("Node '{}' has NO branch of its own (NODE_BRANCH_ID is unset). "
                   + "Customer bookings on this node will fall back to the first branch "
                   + "in the list, which is correct only for a single-branch deployment.",
                   nodeId);
            return;
        }
        Branch b = branchRepository.findById(id).orElse(null);
        if (b == null) {
            LOG.error("Node '{}' declares NODE_BRANCH_ID={} but no such branch exists. "
                    + "Treating this node as having no branch of its own.", nodeId, id);
            return;
        }
        LOG.info("Node '{}' ({}) writes for branch '{}'", nodeId, nodeName, b.getName());
    }

    @GetMapping("/node")
    public ResponseEntity<NodeIdentity> node() {
        UUID id = ownBranchId();
        Branch b = (id == null) ? null : branchRepository.findById(id).orElse(null);
        return ResponseEntity.ok(new NodeIdentity(
                nodeId,
                nodeName,
                b == null ? null : String.valueOf(b.getId()),
                b == null ? null : b.getName()));
    }

    /**
     * A malformed UUID in the environment is a configuration mistake, not a
     * reason to refuse to boot. A node that will not start is worse than one
     * that starts and says what is wrong: the first cannot serve the branch
     * standing in front of it, the second can still take walk-ins at the desk.
     */
    private UUID ownBranchId() {
        if (branchIdRaw == null || branchIdRaw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(branchIdRaw.trim());
        } catch (IllegalArgumentException e) {
            LOG.error("NODE_BRANCH_ID is not a UUID: '{}'. Ignoring it.", branchIdRaw);
            return null;
        }
    }
}
