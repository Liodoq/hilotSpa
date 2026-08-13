package com.hilotspa.backend.model;

import java.util.UUID;

import lombok.Data;

@Data
public class PatientIntakeModel {
    private UUID id;
    private String anatomicalRegion;
    private String bodyView;
    private Integer coordinateX;
    private Integer coordinateY;
    private Integer painScore;
    private String complaintType;
}
