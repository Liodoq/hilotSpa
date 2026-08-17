package com.hilotspa.backend.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(
    name = "service_protocol",
    uniqueConstraints = @UniqueConstraint(columnNames = {"service_id", "condition"})
)
public class ServiceProtocol {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Massage service;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProtocolRule rule;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition", nullable = false)
    private ComplaintType condition;

    @Column(length = 500)
    private String rationale;

    @Column(nullable = false)
    private String authoredBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

}

