package com.hilotspa.backend.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
// Same rule as Appointment: one of the two must identify whose assessment this
// is. Enforced here as well as in the service, so a row naming nobody cannot be
// written by any path, psql included. B85.
@Check(name = "forms_has_a_client",
       constraints = "users_id IS NOT NULL OR walk_in_name IS NOT NULL")
@Table(name = "forms")
public class Forms {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
  
    /**
     * The account this assessment belongs to - null for a walk-in.
     *
     * It was NOT NULL, which meant the one group of clients the Delimitation
     * says staff handle at the counter were the one group who could not be
     * assessed. Exactly the B77 defect, one table over. B85.
     */
    @ManyToOne  
    @JoinColumn(name = "users_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    /** Who this is, when there is no account. The counterpart of
     *  Appointment.walkInName, and the only thing identifying the record. */
    @Column
    private String walkInName;

    /**
     * The safety checklist - paper-deltas H9, bug B44.
     *
     * A real table rather than a string, so the practitioner's ServiceProtocol
     * rules can eventually key on a condition and not only on a complaint, and
     * so "how many clients present on blood thinners" is a query rather than a
     * grep. A set, because ticking a box twice is not more true.
     */
    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "forms_safety_flag",
                     joinColumns = @JoinColumn(name = "forms_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "flag")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<SafetyFlag> safetyFlags = new LinkedHashSet<>();

    /** A preference, not a finding. It was sharing a text field with the safety
     *  checklist, which is how the two got confused. */
    @Enumerated(EnumType.STRING)
    @Column
    private PressurePreference pressurePreference;

    /**
     * "I would rather be treated by a woman" - or a man, or it does not matter.
     *
     * NULL MEANS NO PREFERENCE, and that is deliberate: a client who has not
     * expressed one has not expressed one, and inventing a third enum value for
     * it would let "no preference" be stored as though it were a choice.
     *
     * Honoured in availability AND at the moment of assignment, because a
     * preference the screen respects and the write path ignores is worse than
     * none: the client is told they will get a woman and then does not.
     */
    @Enumerated(EnumType.STRING)
    @Column
    private Sex therapistPreference;

    @ManyToOne
    @JoinColumn(name = "branch_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Branch branch;

    @Enumerated(EnumType.STRING)
    private ComplaintType mainComplaint;

    // Free text. Only meaningful when mainComplaint == ComplaintType.OTHER.
    @Column
    private String mainComplaintOther;

    private String mainComplaintDuration;

    /**
     * "Have you had any serious or chronic illness, operations, traumatic
     * accidents, injury? When?" — Appendix A, verbatim.
     *
     * Boolean, not boolean: a primitive cannot hold "not answered". It defaults
     * to false, which is a claim the client never made.
     */
    private Boolean hadIllness;

    @Column(columnDefinition = "TEXT")
    private String medicalHistory;

    /** "Have you had any therapy before? When?" — Appendix A, verbatim. */
    private Boolean hasTherapy;

    @Column(columnDefinition = "TEXT")
    private String therapyDetail;

    private String status;

    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssessmentIntent intent;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "form", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<PatientIntake> painPoints = new ArrayList<>();



}
