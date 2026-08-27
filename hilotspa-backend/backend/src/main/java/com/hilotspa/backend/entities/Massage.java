package com.hilotspa.backend.entities;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
@Data
@Entity
@Table(name = "massage")
public class Massage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer durationMinute;

    @Column(nullable = false)
    private BigDecimal price;

    /**
     * Whether the treatment is still sold.
     *
     * Nullable on purpose, and read as "true" when null. A NOT NULL column added
     * to a table that already has rows is exactly the migration ddl-auto=update
     * cannot perform, and this project has paid for that twice already (B66,
     * B77). Nullable means the column simply appears and every existing row
     * keeps behaving as it did.
     *
     * A withdrawn treatment is hidden from the assistant and from new bookings.
     * It is never deleted: appointments already made point at it, and the audit
     * log would have nothing left to name.
     */
    @Column
    private Boolean active;

    /** Null means "yes" - see the field comment. */
    public boolean isOnSale() {
        return active == null || active;
    }
}
