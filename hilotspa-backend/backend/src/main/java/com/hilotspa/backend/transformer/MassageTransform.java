package com.hilotspa.backend.transformer;

import com.hilotspa.backend.entities.Massage;
import com.hilotspa.backend.model.MassageModel;

public interface MassageTransform {
    MassageModel transform(Massage massageEntity);
    Massage transform(MassageModel massageModel);
}
