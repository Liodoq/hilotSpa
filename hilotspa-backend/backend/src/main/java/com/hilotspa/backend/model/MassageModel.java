package com.hilotspa.backend.model;

import java.util.UUID;

import java.math.BigDecimal;

import lombok.Data;
@Data
public class MassageModel {
    private UUID id;
    private String name;
    private Integer durationMinute;
    private BigDecimal price;

}
