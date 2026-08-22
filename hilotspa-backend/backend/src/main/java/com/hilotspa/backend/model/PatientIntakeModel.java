package com.hilotspa.backend.model;

import java.util.UUID;

import com.hilotspa.backend.entities.AnatomicalRegion;
import com.hilotspa.backend.entities.BodyView;
import com.hilotspa.backend.entities.ComplaintType;
import com.hilotspa.backend.entities.Side;

import lombok.Data;

@Data
public class PatientIntakeModel {
    private UUID id;
    private AnatomicalRegion anatomicalRegion;
    private Side side;
    private BodyView bodyView;
    private Integer coordinateX;
    private Integer coordinateY;
    private Integer painScoreBefore;
    private Integer painScoreAfter;
    private ComplaintType complaintType;
}
