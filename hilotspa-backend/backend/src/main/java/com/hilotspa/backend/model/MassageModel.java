package com.hilotspa.backend.model;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Data;
@Data
public class MassageModel {
    private UUID id;
    private String name;
    private Integer durationMinute;
    private BigDecimal price;

    /** Null is treated as true by the server; it always answers with a value. */
    private Boolean active;

}
