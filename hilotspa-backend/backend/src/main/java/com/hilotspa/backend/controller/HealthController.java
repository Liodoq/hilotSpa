package com.hilotspa.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hilotspa.backend.model.HealthDtos.Health;
import com.hilotspa.backend.model.HealthDtos.State;
import com.hilotspa.backend.services.HealthService;

/**
 * Operational readiness, for whoever is running the branch.
 *
 * Sits under /admin so the existing ADMIN rule covers it: the detail names
 * missing therapists, unsigned clinical rules and an unset webhook secret, none
 * of which should be readable by an anonymous request.
 *
 * The HTTP status carries the verdict too — 200 when usable, 503 when not — so a
 * monitor or a shell script can act on it without parsing the body.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class HealthController {

    @Autowired
    private HealthService healthService;

    @GetMapping("/health")
    public ResponseEntity<Health> health() {
        Health h = healthService.check();
        return ResponseEntity
                .status(h.state() == State.DOWN ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK)
                .body(h);
    }
}
