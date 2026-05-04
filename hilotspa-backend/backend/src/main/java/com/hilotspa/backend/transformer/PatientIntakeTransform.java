package com.hilotspa.backend.transformer;
import com.hilotspa.backend.entities.*;
import com.hilotspa.backend.model.*;
public interface PatientIntakeTransform {
    PatientIntakeModel transform(PatientIntake patientIntakeEntity);
    PatientIntake transform(PatientIntakeModel patientIntakeModel);
}