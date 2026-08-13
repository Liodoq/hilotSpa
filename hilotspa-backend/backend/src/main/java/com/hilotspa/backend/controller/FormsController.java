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

import com.hilotspa.backend.model.FormsModel;
import com.hilotspa.backend.services.FormsService;

@RestController
@RequestMapping("/api/v1/forms")
@CrossOrigin(origins = "http://localhost:4200")
public class FormsController {

    @Autowired
    private FormsService formsService;

    @PostMapping("/create")
    public ResponseEntity<FormsModel> createForm(@RequestBody FormsModel model) {
        return new ResponseEntity<>(formsService.createForm(model), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FormsModel> getFormById(@PathVariable UUID id) {
        return ResponseEntity.ok(formsService.getFormById(id));
    }

    @GetMapping
    public ResponseEntity<List<FormsModel>> getAllForms() {
        return ResponseEntity.ok(formsService.getAllForms());
    }

    @PutMapping("/{id}")
    public ResponseEntity<FormsModel> updateForm(@PathVariable UUID id, @RequestBody FormsModel model) {
        return ResponseEntity.ok(formsService.updateForm(id, model));
    }
}