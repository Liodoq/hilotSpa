package com.hilotspa.backend.controller;

import com.hilotspa.backend.model.DemographicsModel;
import com.hilotspa.backend.services.DemographicsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/demographics")
@CrossOrigin(origins = "http://localhost:4200")
public class DemographicsController {

    @Autowired
    private DemographicsService demographicsService;

    @PostMapping
    public ResponseEntity<DemographicsModel> createDemographics(@RequestBody DemographicsModel model) {
        return new ResponseEntity<>(demographicsService.createDemographics(model), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DemographicsModel> getDemographicsById(@PathVariable Integer id) {
        return ResponseEntity.ok(demographicsService.getDemographicsById(id));
    }

    @GetMapping
    public ResponseEntity<List<DemographicsModel>> getAllDemographics() {
        return ResponseEntity.ok(demographicsService.getAllDemographics());
    }

    @PutMapping("/{id}")
    public ResponseEntity<DemographicsModel> updateDemographics(@PathVariable Integer id, @RequestBody DemographicsModel model) {
        return ResponseEntity.ok(demographicsService.updateDemographics(id, model));
    }
}