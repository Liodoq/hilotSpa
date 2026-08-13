package com.hilotspa.backend.model;

import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import java.time.*;
import lombok.Data;

@Data
public class BranchModel {
    private UUID id;
    private String name;
    private String address;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
}
