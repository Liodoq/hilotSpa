package com.hilotspa.backend.services;

import java.util.UUID;

import com.hilotspa.backend.model.DemographicsModel;
import java.util.List;

public interface DemographicsService {
    DemographicsModel createDemographics(DemographicsModel demographicsModel);
    DemographicsModel getDemographicsById(UUID id);
    List<DemographicsModel> getAllDemographics();
    DemographicsModel updateDemographics(UUID id, DemographicsModel demographicsModel);
}