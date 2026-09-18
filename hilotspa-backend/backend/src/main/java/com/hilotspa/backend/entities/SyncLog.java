package com.hilotspa.backend.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * One write this node made to a replicated entity (task 3.1).
 *
 * A row says THAT something changed and nothing about what it now says. A peer
 * wanting the new state asks for it by id, at a different endpoint with its own
 * rules. That split is the privacy boundary: this table is the thing a peer is
 * allowed to read, and a payload column would put a patient's assessment inside
 * it.
 *
 * `id` is a bigserial, so it is monotonic within this node - which is all a
 * watermark needs. There is no global clock and no attempt to invent one:
 * single-writer-per-partition means two nodes never write the same row, so
 * their logs never have to be interleaved.
 */
@Data
@Entity
@Table(name = "sync_log")
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Which node made the change. Not always this one, once gossip exists. */
    @Column(nullable = false)
    private String originNodeId;

    @Column(nullable = false, length = 64)
    private String entityType;

    @Column(nullable = false)
    private UUID entityId;

    /**
     * UPSERT or DELETE.
     *
     * Insert and update collapse into UPSERT deliberately: a peer holding a
     * read-only replica does the same thing for both, and telling them apart
     * would invite it to apply an update to a row it never received.
     */
    @Column(nullable = false, length = 16)
    private String action;

    /** The partition this belongs to. Null for globally-owned rows. */
    @Column
    private UUID branchId;

    @Column(nullable = false)
    private LocalDateTime occurredAt;
}
