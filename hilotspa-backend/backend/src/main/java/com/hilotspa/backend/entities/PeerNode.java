package com.hilotspa.backend.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * One node in the cluster, including this one (task 3.2).
 *
 * Named PeerNode, not Node: `Node` collides with org.w3c.dom.Node, which
 * several IDEs auto-import, and the resulting error names neither file.
 *
 * This node has a row like any other. A self row special-cased in three places
 * is a self row somebody forgets in the fourth, and the admin screen wants them
 * side by side anyway.
 *
 * NOT annotated with @EntityListeners(SyncAudited.class): the registry is local
 * knowledge ABOUT the cluster, not shared state. Replicating it would mean two
 * nodes arguing over whether a third was reachable - a question only answerable
 * from where you are standing.
 */
@Data
@Entity
@Table(name = "node")
public class PeerNode {

    @Id
    @Column(name = "node_id")
    private String nodeId;

    @Column
    private String name;

    /** Null for this node - it does not call itself. */
    @Column(length = 500)
    private String baseUrl;

    /** The branch this node owns. Null until the peer has said. */
    @Column
    private UUID branchId;

    /**
     * Deliberately named `self` and not `isSelf`.
     *
     * Lombok would generate isSelf() for either, but Spring Data derives query
     * method names from the PROPERTY, and `findByIsSelfFalse` against a field
     * called isSelf is the ambiguity that throws PropertyReferenceException at
     * startup - invisible to javac, and the exact failure mode logged in
     * Sessions 3 and 5. One name, no guessing.
     */
    @Column(name = "is_self", nullable = false)
    private boolean self = false;

    /** UNKNOWN | ONLINE | UNREACHABLE */
    @Column(nullable = false, length = 16)
    private String state = "UNKNOWN";

    /** When it last ANSWERED. Not when it was last tried. */
    @Column
    private LocalDateTime lastSeenAt;

    @Column
    private Long theirWatermark;

    /** How far we have consumed of its log. Stage 3 advances this. */
    @Column(nullable = false)
    private long ourWatermark = 0L;

    @Column(length = 500)
    private String lastError;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
