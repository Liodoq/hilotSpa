package com.hilotspa.backend.model;

import org.hibernate.annotations.CreationTimestamp;
import java.time.*;
import lombok.Data;

@Data
public class BranchModel {
    private int id;
    private String name;
    private String address;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
}
