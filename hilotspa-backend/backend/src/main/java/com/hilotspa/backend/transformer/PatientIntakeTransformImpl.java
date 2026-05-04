package com.hilotspa.backend.transformer;
import org.springframework.stereotype.Component;
import com.hilotspa.backend.entities.*;
import com.hilotspa.backend.model.*;


@Component
public class PatientIntakeTransformImpl implements PatientIntakeTransform {
    @Override
    public PatientIntakeModel transform(PatientIntake patientIntakeEntity) {
        if (patientIntakeEntity == null) return null;
        PatientIntakeModel patientIntakeModel = new PatientIntakeModel();
        patientIntakeModel.setId(patientIntakeEntity.getId());
        patientIntakeModel.setAnatomicalRegion(patientIntakeEntity.getAnatomicalRegion());
        patientIntakeModel.setCoordinateX(patientIntakeEntity.getCoordinateX());
        patientIntakeModel.setCoordinateY(patientIntakeEntity.getCoordinateY());
        patientIntakeModel.setPainScore(patientIntakeEntity.getPainScore());
        patientIntakeModel.setComplaintType(patientIntakeEntity.getComplaintType());
        return patientIntakeModel;
    }

    @Override
    public PatientIntake transform(PatientIntakeModel patientIntakeModel) {
        if (patientIntakeModel == null) return null;
        PatientIntake patientIntakeEntity = new PatientIntake();
        patientIntakeEntity.setId(patientIntakeModel.getId());
        patientIntakeEntity.setAnatomicalRegion(patientIntakeModel.getAnatomicalRegion());
        patientIntakeEntity.setCoordinateX(patientIntakeModel.getCoordinateX());
        patientIntakeEntity.setCoordinateY(patientIntakeModel.getCoordinateY());
        patientIntakeEntity.setPainScore(patientIntakeModel.getPainScore());
        patientIntakeEntity.setComplaintType(patientIntakeModel.getComplaintType());
        return patientIntakeEntity;
    }
}