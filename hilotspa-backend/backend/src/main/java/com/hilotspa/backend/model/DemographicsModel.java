package com.hilotspa.backend.model;

import lombok.Data;
import java.time.*;

import org.hibernate.annotations.CreationTimestamp;
@Data
public class DemographicsModel {
    private Integer id;
    private Integer usersid;
    private Integer age;
    private String sex;
    private String status;
    private Integer height;
    private Integer weight;
    private LocalDate birthDate;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
