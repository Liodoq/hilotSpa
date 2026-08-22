package com.hilotspa.backend.services;

import java.util.UUID;

import com.hilotspa.backend.model.DemographicsModel;
import java.util.List;

public interface DemographicsService {
    DemographicsModel createDemographics(DemographicsModel demographicsModel);
    DemographicsModel getDemographicsById(UUID id);
    List<DemographicsModel> getAllDemographics();
    DemographicsModel updateDemographics(UUID id, DemographicsModel demographicsModel);

    /**
     * Self-service: the caller's OWN demographics, identified by the JWT.
     * Upserts, because Demographics is unique on users_id and a client edits
     * their profile more than once.
     */
    DemographicsModel saveMine(DemographicsModel demographicsModel);

    /** The caller's own demographics, or null when the profile is not filled in yet. */
    DemographicsModel getMine();
}