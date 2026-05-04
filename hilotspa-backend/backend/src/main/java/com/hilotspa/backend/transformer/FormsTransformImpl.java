package com.hilotspa.backend.transformer;

import org.springframework.stereotype.Component;
import com.hilotspa.backend.entities.*;
import com.hilotspa.backend.model.*;

@Component
public class FormsTransformImpl implements FormsTransform {
    @Override
    public FormsModel transform(Forms formsEntity) {
        if (formsEntity == null) return null;
        FormsModel formsModel = new FormsModel();
        formsModel.setMainComplaint(formsEntity.getMainComplaint());
        formsModel.setMainComplaintDuration(formsEntity.getMainComplaintDuration());
        formsModel.setHasTherapy(formsEntity.isHasTherapy());
        formsModel.setStatus(formsEntity.getStatus());
        formsModel.setRemarks(formsEntity.getRemarks());
        formsModel.setCreatedAt(formsEntity.getCreatedAt());
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