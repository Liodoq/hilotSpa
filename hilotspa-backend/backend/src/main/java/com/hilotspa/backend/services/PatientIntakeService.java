package com.hilotspa.backend.services;

import java.util.UUID;

import java.util.List;

import com.hilotspa.backend.model.PatientIntakeModel;

public interface PatientIntakeService {
    PatientIntakeModel getPatientIntakeById(UUID id);
    List<PatientIntakeModel> getAllPatientIntakes();
}