package com.hilotspa.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hilotspa.backend.model.OverviewDtos.Overview;
import com.hilotspa.backend.services.OverviewService;

/** A1 — the administrator's aggregate. Read-only, counted live, ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin")
public class OverviewController {

    @Autowired
    private OverviewService overviewService;

    @GetMapping("/overview")
    public ResponseEntity<Overview> overview() {
        return ResponseEntity.ok(overviewService.overview());
    }
}
