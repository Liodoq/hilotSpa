package com.hilotspa.backend.model;

import java.util.UUID;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import lombok.Data;
@Data
public class FormsModel {
    private UUID id;
    private UUID userId;
    private UUID branchId;
    private String mainComplaint;
    private String mainComplaintDuration;
    private boolean hasTherapy;
    private String status;
    private String remarks;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private List<PatientIntakeModel> painPoints = new ArrayList<>();
}
