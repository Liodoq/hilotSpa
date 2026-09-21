package com.hilotspa.backend.entities;

import com.hilotspa.backend.config.SyncAudited;

import java.util.UUID;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EntityListeners(SyncAudited.class)
@Entity
@Table(name = "branch")
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    /**
     * The number a client rings to reach THIS branch (3.37).
     *
     * Deliberately on the branch rather than in the environment. SPA_PHONE is
     * one value per node, and a node holds every branch's data - so a reminder
     * about a Daraga visit sent from any node would print whichever number that
     * machine was configured with. Null means nobody has entered one, and every
     * reader must omit the line rather than print an empty label.
     */
    @Column(name = "contact_number", length = 40)
    private String contactNumber;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "branch")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Forms> forms;
}
