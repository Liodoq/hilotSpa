package com.hilotspa.backend.entities;

import com.hilotspa.backend.config.SyncAudited;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
@EntityListeners(SyncAudited.class)
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

    /**
     * What this therapist is trained to perform.
     *
     * A SET, not one value. With a roster this size a single specialty each
     * would make most treatments show no availability at all, and an empty
     * calendar is a worse answer than a simple schema.
     *
     * EMPTY MEANS NO RESTRICTION HAS BEEN RECORDED - not that this person can
     * do nothing. Same rule as onDuty() and Massage.active: refusing to book a
     * therapist because nobody has yet filled in a column added after they were
     * hired would take the branch offline for a data gap, which is the wrong
     * way round. V6 therefore backfills every existing therapist with every
     * specialty, and the admin narrows them deliberately afterwards.
     *
     * So every restriction that exists is one a human chose. That is the
     * property worth having: the system never invents a limit, and never
     * pretends an unanswered question is a "no".
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "therapist_specialty",
            joinColumns = @JoinColumn(name = "therapist_id"))
    @Column(name = "specialty", nullable = false)
    @Enumerated(EnumType.STRING)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Specialty> specialties = new LinkedHashSet<>();

    /** Empty means nobody has said, and nobody has said is not "no". */
    public boolean canPerform(Specialty need) {
        return need == null || specialties == null || specialties.isEmpty()
                || specialties.contains(need);
    }

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}