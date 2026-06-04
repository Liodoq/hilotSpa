package com.hilotspa.backend.controller;

import com.hilotspa.backend.model.FormsModel;
import com.hilotspa.backend.services.FormsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/forms")
@CrossOrigin(origins = "http://localhost:4200")
public class FormsController {

    @Autowired
    private FormsService formsService;

    @PostMapping
    public ResponseEntity<FormsModel> createForm(@RequestBody FormsModel model) {
        return new ResponseEntity<>(formsService.createForm(model), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FormsModel> getFormById(@PathVariable Integer id) {
        return ResponseEntity.ok(formsService.getFormById(id));
    }

    @GetMapping
    public ResponseEntity<List<FormsModel>> getAllForms() {
        return ResponseEntity.ok(formsService.getAllForms());
    }

    @PutMapping("/{id}")
    public ResponseEntity<FormsModel> updateForm(@PathVariable Integer id, @RequestBody FormsModel model) {
        return ResponseEntity.ok(formsService.updateForm(id, model));
    }
}