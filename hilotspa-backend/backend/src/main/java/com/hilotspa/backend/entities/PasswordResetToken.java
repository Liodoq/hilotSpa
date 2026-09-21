package com.hilotspa.backend.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * One outstanding "I forgot my password" request.
 *
 * Deliberately NOT annotated with {@code @EntityListeners(SyncAudited.class)}.
 * Every other table that matters is offered to the peer node; this one must not
 * be. A reset link points at the hostname of the node that sent the email, so a
 * copy on the other node could never be redeemed there - and a token in flight
 * across the network is a token in one more place than it needs to be.
 */
@Data
@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    /**
     * SHA-256 of the token, hex, lower case. The token itself is in the email
     * and nowhere else - see the note in V12.
     */
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Null while the token is still live. Set once, never cleared. */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /**
     * SELF when the account holder asked, ADMIN when the front desk did it for
     * them. Kept because the two are different events on an audit trail even
     * though the token behaves identically.
     */
    @Column(name = "issued_by", nullable = false, length = 40)
    private String issuedBy;

    @Column(name = "origin_node_id", nullable = false, length = 64)
    private String originNodeId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Live means: issued, not yet spent, not yet stale. */
    public boolean isRedeemable(LocalDateTime now) {
        return usedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }
}
