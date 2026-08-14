package com.hilotspa.backend.entities;

import java.util.UUID;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "forms")
public class Forms {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
  
    @ManyToOne  
    @JoinColumn(name = "users_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @ManyToOne
    @JoinColumn(name = "branch_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintType mainComplaint;

    // Free text. Only meaningful when mainComplaint == ComplaintType.OTHER.
    @Column
    private String mainComplaintOther;

    private String mainComplaintDuration;

    private boolean hasTherapy;

    private String status;

    private String remarks;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "form", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<PatientIntake> painPoints = new ArrayList<>();



}
