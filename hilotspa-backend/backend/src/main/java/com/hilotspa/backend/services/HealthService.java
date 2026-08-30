package com.hilotspa.backend.services;

import com.hilotspa.backend.model.HealthDtos.Health;

public interface HealthService {

    /** Runs every readiness check and returns the worst state found. */
    Health check();
}
