package com.hilotspa.backend.model;

import java.util.UUID;

import lombok.Data;
import java.time.*;

import org.hibernate.annotations.CreationTimestamp;
@Data
public class DemographicsModel {
    private UUID id;
    private UUID usersid;
    private Integer age;
    private String sex;
    private String status;
    private Integer height;
    private Integer weight;
    private LocalDate birthDate;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
