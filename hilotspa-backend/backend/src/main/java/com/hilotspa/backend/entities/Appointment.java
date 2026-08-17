package com.hilotspa.backend.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "appointment")
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Branch branch;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User customer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Room room;

    @ManyToOne(optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Massage service;

    @Column(nullable = false)
    private LocalDateTime startTime;

    
    @Column(nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    /** Nullable: a staff-recorded walk-in has no online pre-assessment. */
    @ManyToOne
    @JoinColumn(name = "form_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Forms form;

    @ManyToOne(optional = false)
    @JoinColumn(name = "therapist_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Therapist therapist;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingSource source;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtBooking;

    
    @Column(nullable = false)
    private String originNodeId;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

}
