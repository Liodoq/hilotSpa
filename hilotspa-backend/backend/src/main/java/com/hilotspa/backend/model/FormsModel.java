package com.hilotspa.backend.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.hilotspa.backend.entities.AssessmentIntent;
import com.hilotspa.backend.entities.ComplaintType;

import lombok.Data;
@Data
public class FormsModel {
    private UUID id;
    private UUID userId;
    private UUID branchId;
    private ComplaintType mainComplaint;
    private String mainComplaintOther;
    private String mainComplaintDuration;
    private Boolean hadIllness;
    private String medicalHistory;
    private Boolean hasTherapy;
    private String therapyDetail;
    private String status;
    private String remarks;
    private AssessmentIntent intent;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private List<PatientIntakeModel> painPoints = new ArrayList<>();
}
