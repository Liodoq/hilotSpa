package com.hilotspa.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hilotspa.backend.model.PatientIntakeModel;
import com.hilotspa.backend.services.PatientIntakeService;

@RestController
@RequestMapping("/api/v1/patient-intake")
@CrossOrigin(origins = "http://localhost:4200")
public class PatientIntakeController {

    @Autowired
    private PatientIntakeService patientIntakeService; 

    @GetMapping("/{id}")
    public ResponseEntity<PatientIntakeModel> getPatientIntakeById(@PathVariable UUID id) {
        return ResponseEntity.ok(patientIntakeService.getPatientIntakeById(id));
    }

    @GetMapping
    public ResponseEntity<List<PatientIntakeModel>> getAllPatientIntakes() {
        return ResponseEntity.ok(patientIntakeService.getAllPatientIntakes());
    }

    /**
     * S4/§H3 — the AFTER-session pain score for one marked point.
     *
     * Staff only. The whole route sits behind hasAnyRole("STAFF","ADMIN"), which
     * is why there is no client-facing way to write an outcome onto one's own
     * record: a self-reported improvement is not the measure the paper claims.
     */
    @PutMapping("/{id}/after")
    public ResponseEntity<PatientIntakeModel> recordAfter(
            @PathVariable UUID id, @RequestBody AfterScore body) {
        return ResponseEntity.ok(patientIntakeService.recordAfter(id, body.painScoreAfter()));
    }

    public record AfterScore(Integer painScoreAfter) {
    }

}