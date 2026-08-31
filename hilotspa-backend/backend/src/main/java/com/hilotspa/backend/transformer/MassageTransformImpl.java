package com.hilotspa.backend.transformer;

import org.springframework.stereotype.Component;

import com.hilotspa.backend.entities.Massage;
import com.hilotspa.backend.model.MassageModel;

@Component
public class MassageTransformImpl implements MassageTransform {

    @Override
    public MassageModel transform(Massage massageEntity){
        if(massageEntity == null) return null;
        MassageModel massageModel = new MassageModel();
        massageModel.setId(massageEntity.getId());
        massageModel.setName(massageEntity.getName());
        massageModel.setDurationMinute(massageEntity.getDurationMinute());
        massageModel.setPrice(massageEntity.getPrice());
        massageModel.setActive(massageEntity.isOnSale());
        massageModel.setImageName(massageEntity.getImageName());
        return massageModel;
    }

    @Override
    public Massage transform(MassageModel massageModel){
        if(massageModel == null) return null;
        Massage massageEntity = new Massage();
        massageEntity.setId(massageModel.getId());
        massageEntity.setName(massageModel.getName());
        massageEntity.setDurationMinute(massageModel.getDurationMinute());
        massageEntity.setPrice(massageModel.getPrice());
        massageEntity.setActive(massageModel.getActive());
        massageEntity.setImageName(massageModel.getImageName());
        return massageEntity;
    }
}
