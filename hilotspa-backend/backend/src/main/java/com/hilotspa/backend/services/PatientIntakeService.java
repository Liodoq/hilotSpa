package com.hilotspa.backend.services;

import com.hilotspa.backend.model.PatientIntakeModel;
import java.util.List;

public interface PatientIntakeService {
    PatientIntakeModel createPatientIntake(PatientIntakeModel patientIntakeModel);
    PatientIntakeModel getPatientIntakeById(Integer id);
    List<PatientIntakeModel> getAllPatientIntakes();
    PatientIntakeModel updatePatientIntake(Integer id, PatientIntakeModel patientIntakeModel);
}