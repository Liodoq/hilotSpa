package com.hilotspa.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hilotspa.backend.entities.PatientIntake;

@Repository
public interface PatientIntakeRepository extends JpaRepository<PatientIntake, UUID> {

    List<PatientIntake> findByFormId(UUID formId);
}
