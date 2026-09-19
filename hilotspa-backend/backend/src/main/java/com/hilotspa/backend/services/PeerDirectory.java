package com.hilotspa.backend.services;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.hilotspa.backend.entities.PeerNode;
import com.hilotspa.backend.model.SyncDtos.Hello;
import com.hilotspa.backend.repository.PeerNodeRepository;

/**
 * Who the peers are, and whether they answered (task 3.2).
 *
 * Peers are configured as URLs and their IDENTITY IS DISCOVERED. Putting the
 * node id in configuration as well means two places can disagree about a node's
 * name, and configuration is the one nobody re-reads.
 *
 * NOTHING HERE IS @Transactional, deliberately. Each save is its own
 * transaction through the repository. Wrapping the poll would hold a database
 * connection open across an HTTP call to a machine that may be switched off -
 * up to six seconds per unreachable peer, for as long as it stays down. The
 * price is that the URL-keyed row and the id-keyed row are swapped in two
 * steps rather than one, which is why ensurePeerRows() runs at the top of every
 * poll: anything half-done is put back within thirty seconds, by design.
 */
@Service
public class PeerDirectory {

    private static final Logger LOG = LoggerFactory.getLogger(PeerDirectory.class);

    @Autowired private PeerNodeRepository peerNodeRepository;

    @Value("${hilotspa.node.id:local-dev}")            private String nodeId;
    @Value("${hilotspa.node.name:Local}")              private String nodeName;
    @Value("${hilotspa.node.branch-id:}")              private String branchIdRaw;
    @Value("${hilotspa.sync.token:}")                  private String token;
    @Value("${hilotspa.sync.peers:}")                  private String peerUrls;
    @Value("${hilotspa.booking.timezone:Asia/Manila}") private String timezone;

    private RestClient client;

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of(timezone));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        // Short and fixed. One unreachable peer must not delay the others - the
        // entire value of the screen is that it stays honest while a peer is
        // down, and the 30-second default hangs the loop instead.
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(3));
        f.setReadTimeout(Duration.ofSeconds(3));
        this.client = RestClient.builder().requestFactory(f).build();

        if (token == null || token.isBlank()) {
            LOG.warn("Peer sync is OFF on this node: SYNC_TOKEN is unset, so "
                   + "/api/v1/sync/** rejects everything and no peer is polled. "
                   + "That is the correct closed default, not a fault - but if you "
                   + "expected two nodes to see each other, this is why they do not.");
        }
        poll();
    }

    /**
     * Every node in the registry, including this one.
     *
     * The self row is written on every boot rather than only when missing,
     * because NODE_NAME and NODE_BRANCH_ID can change in .env between
     * restarts and a registry that still reports the old one is worse than no
     * registry at all.
     */
    private void ensurePeerRows() {
        PeerNode self = peerNodeRepository.findById(nodeId).orElseGet(PeerNode::new);
        self.setNodeId(nodeId);
        self.setName(nodeName);
        self.setSelf(true);
        self.setState("ONLINE");
        self.setBaseUrl(null);
        self.setBranchId(ownBranchId());
        self.setLastSeenAt(now());
        self.setLastError(null);
        peerNodeRepository.save(self);

        for (String raw : peerUrls.split(",")) {
            String url = raw.trim().replaceAll("/+$", "");
            if (url.isEmpty()) {
                continue;
            }
            // Keyed by URL, not by id: the id is not known until the peer
            // answers, and a row cannot be created for a node that has never
            // been reachable without inventing a name for it.
            if (peerNodeRepository.findByBaseUrl(url).isEmpty()) {
                PeerNode p = new PeerNode();
                p.setNodeId("unknown@" + url);
                p.setName(url);
                p.setBaseUrl(url);
                p.setSelf(false);
                p.setState("UNKNOWN");
                peerNodeRepository.save(p);
            }
        }
    }

    @Scheduled(fixedDelayString = "${hilotspa.sync.poll-ms:30000}")
    public void poll() {
        if (client == null) {
            return;   // not started yet
        }
        ensurePeerRows();
        if (token == null || token.isBlank()) {
            return;   // closed by configuration; nothing to ask and nothing to say
        }
        List<PeerNode> peers = peerNodeRepository.findBySelfFalse();
        for (PeerNode p : peers) {
            askOne(p);
        }
    }

    private void askOne(PeerNode p) {
        try {
            Hello h = client.get()
                    .uri(p.getBaseUrl() + "/api/v1/sync/hello")
                    .header("X-Sync-Token", token)
                    .retrieve()
                    .body(Hello.class);

            if (h == null || h.nodeId() == null) {
                fail(p, "The peer answered with nothing useful.");
                return;
            }
            if (h.nodeId().equals(nodeId)) {
                // Pointed at ourselves. Worth saying plainly: it is an easy
                // copy-paste mistake and it would otherwise look like a healthy
                // peer that mysteriously never has anything new.
                fail(p, "That URL is this node. Check SYNC_PEERS.");
                return;
            }
            if (h.protocol() != 1) {
                fail(p, "Peer speaks sync protocol " + h.protocol() + ", this node speaks 1.");
                return;
            }

            PeerNode row = p;
            if (!h.nodeId().equals(p.getNodeId())) {
                // The row was keyed by URL until the peer told us who it is.
                // node_id is the primary key, so adopting the real id means a
                // new row - carrying the watermark across so stage 3 does not
                // re-read a log it has already consumed.
                PeerNode fresh = peerNodeRepository.findById(h.nodeId())
                        .orElseGet(PeerNode::new);
                fresh.setNodeId(h.nodeId());
                fresh.setBaseUrl(p.getBaseUrl());
                fresh.setOurWatermark(p.getOurWatermark());
                fresh.setSelf(false);
                peerNodeRepository.delete(p);
                row = fresh;
            }

            row.setName(h.nodeName());
            row.setBranchId(h.branchId());
            row.setTheirWatermark(h.watermark());
            row.setState("ONLINE");
            row.setLastSeenAt(now());
            row.setLastError(null);
            peerNodeRepository.save(row);

        } catch (Exception e) {
            // Every failure is the same to the screen: we asked, it did not
            // answer. The REASON is kept, because "unreachable" with no reason
            // sends somebody to read container logs on a machine they may not
            // have.
            fail(p, e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        }
    }

    private void fail(PeerNode p, String why) {
        p.setState("UNREACHABLE");
        p.setLastError(why.length() > 500 ? why.substring(0, 500) : why);
        // lastSeenAt is NOT touched. It means "when it was really there", and a
        // failed attempt is not a sighting.
        peerNodeRepository.save(p);
        LOG.debug("peer {} unreachable: {}", p.getBaseUrl(), why);
    }

    private java.util.UUID ownBranchId() {
        if (branchIdRaw == null || branchIdRaw.isBlank()) {
            return null;
        }
        try {
            return java.util.UUID.fromString(branchIdRaw.trim());
        } catch (IllegalArgumentException e) {
            return null;   // NodeController already says so, loudly, at startup
        }
    }
}
