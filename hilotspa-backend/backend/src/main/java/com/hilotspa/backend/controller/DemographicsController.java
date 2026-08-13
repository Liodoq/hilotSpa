package com.hilotspa.backend.controller;

import java.util.UUID;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hilotspa.backend.model.DemographicsModel;
import com.hilotspa.backend.services.DemographicsService;

@RestController
@RequestMapping("/api/v1/demographics")
@CrossOrigin(origins = "http://localhost:4200")
public class DemographicsController {

    @Autowired
    private DemographicsService demographicsService;

    @PostMapping("/create")
    public ResponseEntity<DemographicsModel> createDemographics(@RequestBody DemographicsModel model) {
        return new ResponseEntity<>(demographicsService.createDemographics(model), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DemographicsModel> getDemographicsById(@PathVariable UUID id) {
        return ResponseEntity.ok(demographicsService.getDemographicsById(id));
    }

    @GetMapping
    public ResponseEntity<List<DemographicsModel>> getAllDemographics() {
        return ResponseEntity.ok(demographicsService.getAllDemographics());
    }

    @PutMapping("/{id}")
    public ResponseEntity<DemographicsModel> updateDemographics(@PathVariable UUID id, @RequestBody DemographicsModel model) {
        return ResponseEntity.ok(demographicsService.updateDemographics(id, model));
    }
}