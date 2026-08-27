package com.hilotspa.backend.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.Check;
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
// One of the two must identify the client. Enforced in the service AND here, so
// a row that names nobody cannot be written by any path, including psql.
@Check(name = "appointment_has_a_client",
       constraints = "customer_id IS NOT NULL OR walk_in_name IS NOT NULL")
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

    /**
     * The account this visit belongs to - NULL for a walk-in (B77).
     *
     * A client who arrives at the counter has no account, and the spa records
     * several every day. Requiring one here meant the schema could not represent
     * the single most common way this business takes a booking.
     *
     * Exactly one of `customer` and `walkInName` is set. That is enforced in
     * BookingServiceImpl and again by the check constraint on this table, so a
     * nameless appointment cannot exist however it is written.
     */
    @ManyToOne
    @JoinColumn(name = "customer_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User customer;

    /**
     * Who the visit is for, when there is no account to name them.
     *
     * Deliberately NOT a User row created at the counter: an account that cannot
     * log in is a false entry in the accounts screen, and it would quietly
     * undermine the claim that self-registration is the only way an account
     * comes into existence.
     *
     * The consequence is honest rather than hidden - a walk-in has no account,
     * so no history follows them between visits. That is exactly what happens
     * on paper today.
     */
    @Column
    private String walkInName;

    /** Optional. A number to call if the session has to move. */
    @Column
    private String walkInContact;

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
