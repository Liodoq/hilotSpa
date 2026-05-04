package com.hilotspa.backend.transformer;
import com.hilotspa.backend.entities.*;
import com.hilotspa.backend.model.*;
public interface DemographicsTransform {
    DemographicsModel transform(Demographics demographicsEntity);
    Demographics transform(DemographicsModel demographicsModel);
}