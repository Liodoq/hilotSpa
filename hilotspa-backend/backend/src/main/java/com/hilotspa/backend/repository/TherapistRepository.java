package com.hilotspa.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hilotspa.backend.entities.Therapist;

@Repository
public interface TherapistRepository extends JpaRepository<Therapist, UUID> {
    List<Therapist> findByBranchId(UUID branchId);
    List<Therapist> findByBranchIdAndActiveTrue(UUID branchId);
}
