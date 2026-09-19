package com.hilotspa.backend.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

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
 * A therapist's planned day off (task 3.33).
 *
 * The system had two controls and neither said "Angel is off next Tuesday":
 * `status` is a right-now flag honoured for today only, and `active` means the
 * person has left the spa. This is the missing middle - a dated range, entered
 * in advance, that ends by itself.
 *
 * NOT on the SyncAudited allow-list, and that is a judgement rather than an
 * oversight: leave belongs to the branch that rosters the person. A therapist
 * works at exactly one branch, so exactly one node needs to know, and sending
 * one branch's staffing arrangements to another serves nobody. If a therapist
 * ever covers both branches, this is the first line that has to change.
 */
@Data
@Entity
@Table(name = "therapist_leave")
public class TherapistLeave {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "therapist_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Therapist therapist;

    /** Inclusive. */
    @Column(nullable = false)
    private LocalDate startsOn;

    /** Inclusive - "off from the 20th to the 22nd" is three days, not two. */
    @Column(nullable = false)
    private LocalDate endsOn;

    @Column(length = 255)
    private String reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Who entered it, for the audit trail. */
    @Column(length = 255)
    private String createdBy;

    public boolean covers(LocalDate d) {
        return !d.isBefore(startsOn) && !d.isAfter(endsOn);
    }
}
