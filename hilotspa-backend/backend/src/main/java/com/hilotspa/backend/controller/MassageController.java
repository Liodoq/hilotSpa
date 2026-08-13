package com.hilotspa.backend.controller;

import java.util.List;
import java.util.UUID;

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

import com.hilotspa.backend.model.MassageModel;
import com.hilotspa.backend.services.MassageService;

@RestController
@RequestMapping("/api/v1/massages")
@CrossOrigin(origins = "http://localhost:4200")
public class MassageController {

    @Autowired
    private MassageService massageService;

    @PostMapping("/create")
    public ResponseEntity<MassageModel> createMassage(@RequestBody MassageModel massageModel) {
        return new ResponseEntity<>(massageService.createMassage(massageModel), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MassageModel> getMassageById(@PathVariable UUID id) {
        return ResponseEntity.ok(massageService.getMassageById(id));
    }

    @GetMapping
    public ResponseEntity<List<MassageModel>> getAllMassages() {
        return ResponseEntity.ok(massageService.getAllMassages());
    }

    @PutMapping("/{id}")
    public ResponseEntity<MassageModel> updateMassage(@PathVariable UUID id, @RequestBody MassageModel massageModel) {
        return ResponseEntity.ok(massageService.updateMassage(id, massageModel));
    }
}
