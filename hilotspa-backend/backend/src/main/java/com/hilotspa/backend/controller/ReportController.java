package com.hilotspa.backend.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hilotspa.backend.model.ReportDtos.Reports;
import com.hilotspa.backend.services.ReportService;

/**
 * A3 / S5 - reports.
 *
 * Open to STAFF as well as ADMIN, which is why it is NOT under /api/v1/admin.
 * The branch rule is in the service: a staff caller is forced to their own
 * branch and the branchId they send is ignored rather than validated. Put the
 * rule anywhere else and there are two places to forget it.
 */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    @Autowired
    private ReportService reportService;

    /**
     * @param from     yyyy-MM-dd, inclusive. Omit for twelve months back.
     * @param to       yyyy-MM-dd, inclusive. Omit for today.
     * @param branchId omit for every branch. IGNORED for a staff account.
     */
    @GetMapping("/reports")
    public ResponseEntity<Reports> reports(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId) {
        return ResponseEntity.ok(reportService.reports(from, to, branchId));
    }
}
