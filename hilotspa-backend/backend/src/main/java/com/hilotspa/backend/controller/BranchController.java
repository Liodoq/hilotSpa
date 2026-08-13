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

import com.hilotspa.backend.model.BranchModel;
import com.hilotspa.backend.services.BranchService;

@RestController
@RequestMapping("/api/v1/branches")
@CrossOrigin(origins = "http://localhost:4200")
public class BranchController {

    @Autowired
    private BranchService branchService;

    @PostMapping("/create")
    public ResponseEntity<BranchModel> createBranch(@RequestBody BranchModel model) {
        return new ResponseEntity<>(branchService.createBranch(model), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BranchModel> getBranchById(@PathVariable UUID id) {
        return ResponseEntity.ok(branchService.getBranchById(id));
    }

    @GetMapping
    public ResponseEntity<List<BranchModel>> getAllBranches() {
        return ResponseEntity.ok(branchService.getAllBranches());
    }

    @PutMapping("/{id}")
    public ResponseEntity<BranchModel> updateBranch(@PathVariable UUID id, @RequestBody BranchModel model) {
        return ResponseEntity.ok(branchService.updateBranch(id, model));
    }
}