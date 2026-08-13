package com.hilotspa.backend.entities;

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

@Data
@Entity
@Table(name = "patient_intake")
public class PatientIntake{
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String anatomicalRegion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "form_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Forms form;

    @Column(nullable = false)
    private String bodyView;

    @Column(nullable = false)
    private Integer coordinateX;

    @Column(nullable = false)
    private Integer coordinateY;

    @Column(nullable = false)
    private Integer painScore;

    @Column
    private String complaintType;
}
