package com.hilotspa.backend.model;

import java.util.UUID;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

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
    private boolean hasTherapy;
    private String status;
    private String remarks;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private List<PatientIntakeModel> painPoints = new ArrayList<>();
}
