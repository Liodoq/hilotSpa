package com.hilotspa.backend.model;

import lombok.Data;

@Data
public class PatientIntakeModel {
    private Integer id;
    private String anatomicalRegion;
    private Integer coordinateX;
    private Integer coordinateY;
    private Integer painScore;
    private String complaintType;
}
