package com.hilotspa.backend.services;

import java.util.UUID;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hilotspa.backend.entities.PatientIntake;
import com.hilotspa.backend.model.PatientIntakeModel;
import com.hilotspa.backend.repository.PatientIntakeRepository;
import com.hilotspa.backend.transformer.PatientIntakeTransform;

@Service
public class PatientIntakeServiceImpl implements PatientIntakeService {

    @Autowired
    private PatientIntakeRepository patientIntakeRepository;

    @Autowired
    private PatientIntakeTransform patientIntakeTransform;


    @Override
    public PatientIntakeModel getPatientIntakeById(UUID id) {
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

}