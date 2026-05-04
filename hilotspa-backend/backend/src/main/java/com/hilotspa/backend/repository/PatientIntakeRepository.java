package com.hilotspa.backend.repository;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.hilotspa.backend.entities.PatientIntake;
import java.util.*;
@Repository
public interface PatientIntakeRepository extends JpaRepository<PatientIntake, Integer>{
    List<PatientIntake> findPatientIntakeByAnatomicalRegionAndPainScore(String anatomicalRegion, Integer painScore);
    Optional<PatientIntake> findPatientIntakeByCoordinateXAndCoordinateY(Integer coordinateX, Integer coordinateY);
}
