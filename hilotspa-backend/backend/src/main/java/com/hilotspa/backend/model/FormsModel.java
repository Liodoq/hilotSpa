package com.hilotspa.backend.model;

import org.hibernate.annotations.CreationTimestamp;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FormsModel {
    private Integer id;
    private Integer userId;
    private Integer patientIntakeId;
    private Integer branchId;
    private String mainComplaint;
    private String mainComplaintDuration;
    private boolean hasTherapy;
    private String status;
    private String remarks;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
