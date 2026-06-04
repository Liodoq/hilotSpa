package com.hilotspa.backend.services;

import com.hilotspa.backend.model.DemographicsModel;
import java.util.List;

public interface DemographicsService {
    DemographicsModel createDemographics(DemographicsModel demographicsModel);
    DemographicsModel getDemographicsById(Integer id);
    List<DemographicsModel> getAllDemographics();
    DemographicsModel updateDemographics(Integer id, DemographicsModel demographicsModel);
}