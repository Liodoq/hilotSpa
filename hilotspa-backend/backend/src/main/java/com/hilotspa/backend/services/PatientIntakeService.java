package com.hilotspa.backend.services;

import java.util.UUID;

import java.util.List;

import com.hilotspa.backend.model.PatientIntakeModel;

public interface PatientIntakeService {
    PatientIntakeModel getPatientIntakeById(UUID id);
    List<PatientIntakeModel> getAllPatientIntakes();

    /**
     * S4/§H3 — record the AFTER-session pain score for one marked point.
     *
     * Written by staff at the end of the session, never by the client, and never
     * before. BEFORE minus AFTER is the only quantitative outcome measure the spa
     * already collects, so it is the one number the whole evaluation rests on.
     */
    PatientIntakeModel recordAfter(UUID id, Integer painScoreAfter);
}