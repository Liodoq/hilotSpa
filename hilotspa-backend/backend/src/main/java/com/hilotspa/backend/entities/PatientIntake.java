package com.hilotspa.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "patient_intake")
public class PatientIntake{
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String anatomicalRegion;

    @Column(nullable = false)
    private Integer coordinateX;

    @Column(nullable = false)
    private Integer coordinateY;

    @Column(nullable = false)
    private Integer painScore;

    @Column
    private String complaintType;
}
