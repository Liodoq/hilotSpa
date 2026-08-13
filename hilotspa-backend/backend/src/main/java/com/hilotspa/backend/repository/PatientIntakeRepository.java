package com.hilotspa.backend.repository;

import java.util.UUID;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hilotspa.backend.entities.PatientIntake;
@Repository
public interface PatientIntakeRepository extends JpaRepository<PatientIntake, UUID>{
    List<PatientIntake> findPatientIntakeByAnatomicalRegionAndPainScore(String anatomicalRegion, Integer painScore);
    Optional<PatientIntake> findPatientIntakeByCoordinateXAndCoordinateY(Integer coordinateX, Integer coordinateY);
    List<PatientIntake> findByFormId(UUID formId);
}
