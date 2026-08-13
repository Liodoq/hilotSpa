package com.hilotspa.backend.repository;

import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.hilotspa.backend.entities.Branch;

@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID> {
     
}
