package com.hilotspa.backend.controller;

import com.hilotspa.backend.model.PatientIntakeModel;
import com.hilotspa.backend.services.PatientIntakeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/patient-intake")
@CrossOrigin(origins = "http://localhost:4200")
public class PatientIntakeController {

    @Autowired
    private PatientIntakeService patientIntakeService;

    @PostMapping
    public ResponseEntity<PatientIntakeModel> createPatientIntake(@RequestBody PatientIntakeModel model) {
        return new ResponseEntity<>(patientIntakeService.createPatientIntake(model), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PatientIntakeModel> getPatientIntakeById(@PathVariable Integer id) {
        return ResponseEntity.ok(patientIntakeService.getPatientIntakeById(id));
    }

    @GetMapping
    public ResponseEntity<List<PatientIntakeModel>> getAllPatientIntakes() {
        return ResponseEntity.ok(patientIntakeService.getAllPatientIntakes());
    }

    @PutMapping("/{id}")
    public ResponseEntity<PatientIntakeModel> updatePatientIntake(@PathVariable Integer id, @RequestBody PatientIntakeModel model) {
        return ResponseEntity.ok(patientIntakeService.updatePatientIntake(id, model));
    }
}