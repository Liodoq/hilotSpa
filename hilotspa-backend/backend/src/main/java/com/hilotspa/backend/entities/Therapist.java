package com.hilotspa.backend.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "therapist")
public class Therapist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    // A therapist works at exactly one branch. This is what gives that branch
    // node sole authority to book them — no cross-node conflict is possible.
    @ManyToOne(optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TherapistStatus status = TherapistStatus.OFF_DUTY;

    /**
     * The therapist's sex, because clients are allowed to ask for a woman or a
     * man and that is a matter of dignity, not preference in the trivial sense.
     * Especially here: hilot is close, hands-on work, and a client who cannot
     * say who they are comfortable with will simply not come back.
     *
     * Nullable. Older rows predate the column, and a therapist with no sex
     * recorded is offered only to clients who expressed no preference - never
     * guessed at, and never quietly matched to a request they might not meet.
     */
    @Enumerated(EnumType.STRING)
    @Column
    private Sex sex;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}