package com.hilotspa.backend.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.CascadeType;
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
