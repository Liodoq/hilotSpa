package com.hilotspa.backend.repository;

import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.hilotspa.backend.entities.Demographics;
import java.util.*;

@Repository
public interface DemographicsRepository extends JpaRepository<Demographics, UUID>{
    Optional<Demographics> findByUserId(UUID userId);
}
