package com.hilotspa.backend.services;

import com.hilotspa.backend.entities.PatientIntake;
import com.hilotspa.backend.model.PatientIntakeModel;
import com.hilotspa.backend.repository.PatientIntakeRepository;
import com.hilotspa.backend.transformer.PatientIntakeTransform;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PatientIntakeServiceImpl implements PatientIntakeService {

    @Autowired
    private PatientIntakeRepository patientIntakeRepository;

    @Autowired
    private PatientIntakeTransform patientIntakeTransform;

    @Override
    public PatientIntakeModel createPatientIntake(PatientIntakeModel model) {
        PatientIntake intake = new PatientIntake();
        intake.setAnatomicalRegion(model.getAnatomicalRegion());
        intake.setCoordinateX(model.getCoordinateX());
        intake.setCoordinateY(model.getCoordinateY());
        intake.setPainScore(model.getPainScore());
        intake.setComplaintType(model.getComplaintType());
        
        return patientIntakeTransform.transform(patientIntakeRepository.save(intake));
    }

    @Override
    public PatientIntakeModel getPatientIntakeById(Integer id) {
        PatientIntake intake = patientIntakeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Patient Intake not found"));
        return patientIntakeTransform.transform(intake);
    }

    @Override
    public List<PatientIntakeModel> getAllPatientIntakes() {
        return patientIntakeRepository.findAll().stream()
                .map(patientIntakeTransform::transform)
                .collect(Collectors.toList());
    }

    @Override
    public PatientIntakeModel updatePatientIntake(Integer id, PatientIntakeModel model) {
        PatientIntake intake = patientIntakeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Patient Intake not found"));
        
        intake.setAnatomicalRegion(model.getAnatomicalRegion());
        intake.setCoordinateX(model.getCoordinateX());
        intake.setCoordinateY(model.getCoordinateY());
        intake.setPainScore(model.getPainScore());
        intake.setComplaintType(model.getComplaintType());
        
        return patientIntakeTransform.transform(patientIntakeRepository.save(intake));
    }
}