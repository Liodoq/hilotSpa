package com.hilotspa.backend.transformer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hilotspa.backend.entities.Forms;
import com.hilotspa.backend.model.FormsModel;

@Component
public class FormsTransformImpl implements FormsTransform {
    
    @Autowired
    private PatientIntakeTransform patientIntakeTransform;

    @Override
    public FormsModel transform(Forms formsEntity) {
        if (formsEntity == null) return null;
        FormsModel formsModel = new FormsModel();
        formsModel.setId(formsEntity.getId());
        formsModel.setMainComplaint(formsEntity.getMainComplaint());
        formsModel.setMainComplaintDuration(formsEntity.getMainComplaintDuration());
        formsModel.setHasTherapy(formsEntity.isHasTherapy());
        formsModel.setStatus(formsEntity.getStatus());
        formsModel.setRemarks(formsEntity.getRemarks());
        formsModel.setCreatedAt(formsEntity.getCreatedAt());
        if (formsEntity.getBranch() != null) {
        formsModel.setBranchId(formsEntity.getBranch().getId());
        }
        if (formsEntity.getUser() != null) {
        formsModel.setUserId(formsEntity.getUser().getId());
        }
        if (formsEntity.getPainPoints() != null) {
            formsModel.setPainPoints(
                formsEntity.getPainPoints().stream()
                    .map(patientIntakeTransform::transform)
                    .collect(java.util.stream.Collectors.toList())
            );
        }
        return formsModel;
    }

    @Override
    public Forms transform(FormsModel formsModel) {
        if (formsModel == null) return null;
        Forms formsEntity = new Forms();
        formsEntity.setMainComplaint(formsModel.getMainComplaint());
        formsEntity.setMainComplaintDuration(formsModel.getMainComplaintDuration());
        formsEntity.setHasTherapy(formsModel.isHasTherapy());
        formsEntity.setStatus(formsModel.getStatus());
        formsEntity.setRemarks(formsModel.getRemarks());
        return formsEntity;
    }
}