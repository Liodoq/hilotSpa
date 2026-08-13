package com.hilotspa.backend.repository;

import java.util.UUID;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hilotspa.backend.entities.Forms;

@Repository
public interface FormsRepository extends JpaRepository<Forms, UUID>{
    List<Forms> findByUserId(UUID userId);
    List<Forms> findByBranchId(UUID branchId);
}
