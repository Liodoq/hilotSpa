package com.hilotspa.backend.entities;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
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
@Table(name = "patient_intake")
public class PatientIntake{
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnatomicalRegion anatomicalRegion;

    /**
     * The client's own left/right. Nullable: midline regions such as Lumbar and
     * Cervical have no side, and forcing CENTRE on them would be a claim the
     * paper form does not make.
     */
    @Enumerated(EnumType.STRING)
    @Column
    private Side side;

    @ManyToOne(optional = false)
    @JoinColumn(name = "form_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Forms form;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BodyView bodyView;

    @Column(nullable = false)
    private Integer coordinateX;

    @Column(nullable = false)
    private Integer coordinateY;

    /**
     * Pain at the START of the session, 1-10, recorded by the client.
     *
     * The paper form's pain scale has BEFORE and AFTER columns. Storing one
     * number threw away half of the only outcome measure the spa already
     * collects — see paper-deltas §H3.
     */
    @Column(nullable = false)
    private Integer painScoreBefore;

    /**
     * Pain at the END of the session. Written by staff on S4, never by the
     * client, and null until the session is finished.
     */
    @Column
    private Integer painScoreAfter;

    @Column
    @Enumerated(EnumType.STRING)
    private ComplaintType complaintType;
}
