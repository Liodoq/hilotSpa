package com.hilotspa.backend.entities;

import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.*;

@Data
@Entity
@Table(name = "demographics")
public class Demographics{
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "users_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(nullable = false)
    private Integer age;

    @Column(nullable = false)
    private String sex;

    /**
     * On the paper form between ADDRESS and AGE. A sari-sari store owner
     * lifting sacks and a call-centre agent sitting eight hours present
     * differently for the same complaint, and it is context the assistant
     * can use.
     */
    private String occupation;

    @Column(nullable = false)
    private String status;

    // Optional on the paper form, and optional here. A client who would rather
    // not give a weight must still be able to book (NFR#4). Making these NOT
    // NULL would have thrown a constraint violation on the first blank field.
    @Column
    private Integer height;

    @Column
    private Integer weight;

    @Column(nullable = false)
    private LocalDate birthDate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    
}
