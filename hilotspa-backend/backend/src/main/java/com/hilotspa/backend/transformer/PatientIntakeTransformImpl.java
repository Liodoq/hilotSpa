package com.hilotspa.backend.transformer;
import org.springframework.stereotype.Component;

import com.hilotspa.backend.entities.PatientIntake;
import com.hilotspa.backend.model.PatientIntakeModel;


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
        patientIntakeModel.setPainScoreBefore(patientIntakeEntity.getPainScoreBefore());
        patientIntakeModel.setPainScoreAfter(patientIntakeEntity.getPainScoreAfter());
        patientIntakeModel.setSide(patientIntakeEntity.getSide());
        patientIntakeModel.setBodyView(patientIntakeEntity.getBodyView());
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
        patientIntakeEntity.setPainScoreBefore(patientIntakeModel.getPainScoreBefore());
        patientIntakeEntity.setPainScoreAfter(patientIntakeModel.getPainScoreAfter());
        patientIntakeEntity.setSide(patientIntakeModel.getSide());
        patientIntakeEntity.setComplaintType(patientIntakeModel.getComplaintType());
        patientIntakeEntity.setBodyView(patientIntakeModel.getBodyView());
        return patientIntakeEntity;
    }
}