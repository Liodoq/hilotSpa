package com.hilotspa.backend.controller;

import java.util.UUID;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

}