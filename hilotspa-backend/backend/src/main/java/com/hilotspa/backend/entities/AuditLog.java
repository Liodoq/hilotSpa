package com.hilotspa.backend.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "audit_log")
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "branch_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Branch branch;

    @ManyToOne
    @JoinColumn(name = "actor_id" )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User actor;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private UUID entityId;

    @Column(length = 1000)
    private String details;

    @Column(nullable = false)
    private String originNodeId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;
}
