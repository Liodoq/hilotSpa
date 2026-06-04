package com.hilotspa.backend.controller;

import com.hilotspa.backend.model.BranchModel;
import com.hilotspa.backend.services.BranchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/branches")
@CrossOrigin(origins = "http://localhost:4200")
public class BranchController {

    @Autowired
    private BranchService branchService;

    @PostMapping
    public ResponseEntity<BranchModel> createBranch(@RequestBody BranchModel model) {
        return new ResponseEntity<>(branchService.createBranch(model), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BranchModel> getBranchById(@PathVariable Integer id) {
        return ResponseEntity.ok(branchService.getBranchById(id));
    }

    @GetMapping
    public ResponseEntity<List<BranchModel>> getAllBranches() {
        return ResponseEntity.ok(branchService.getAllBranches());
    }

    @PutMapping("/{id}")
    public ResponseEntity<BranchModel> updateBranch(@PathVariable Integer id, @RequestBody BranchModel model) {
        return ResponseEntity.ok(branchService.updateBranch(id, model));
    }
}