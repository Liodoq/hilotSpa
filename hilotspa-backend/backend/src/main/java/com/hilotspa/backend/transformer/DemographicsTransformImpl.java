package com.hilotspa.backend.transformer;

import org.springframework.stereotype.Component;
import com.hilotspa.backend.entities.*;
import com.hilotspa.backend.model.*;
@Component
public class DemographicsTransformImpl implements DemographicsTransform {
    @Override
    public DemographicsModel transform(Demographics demographicsEntity) {
        if (demographicsEntity == null) return null;
        DemographicsModel demographicsModel = new DemographicsModel();
        demographicsModel.setId(demographicsEntity.getId());
        demographicsModel.setAge(demographicsEntity.getAge());
        demographicsModel.setSex(demographicsEntity.getSex());
        demographicsModel.setStatus(demographicsEntity.getStatus());
        demographicsModel.setHeight(demographicsEntity.getHeight());
        demographicsModel.setWeight(demographicsEntity.getWeight());
        demographicsModel.setBirthDate(demographicsEntity.getBirthDate());
        demographicsModel.setCreatedAt(demographicsEntity.getCreatedAt());
        return demographicsModel;
    }

    @Override
    public Demographics transform(DemographicsModel demographicsModel) {
        if (demographicsModel == null) return null;
        Demographics demographicsEntity = new Demographics();
        demographicsEntity.setId(demographicsModel.getId());
        demographicsEntity.setAge(demographicsModel.getAge());
        demographicsEntity.setSex(demographicsModel.getSex());
        demographicsEntity.setStatus(demographicsModel.getStatus());
        demographicsEntity.setHeight(demographicsModel.getHeight());
        demographicsEntity.setWeight(demographicsModel.getWeight());
        demographicsEntity.setBirthDate(demographicsModel.getBirthDate());
        return demographicsEntity;
    }
}