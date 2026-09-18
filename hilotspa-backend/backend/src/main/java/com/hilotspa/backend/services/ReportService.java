package com.hilotspa.backend.services;

import java.time.LocalDate;
import java.util.UUID;

import com.hilotspa.backend.model.ReportDtos.Reports;

/** A3 - the administrator's reports. Read-only, counted live, ADMIN only. */
public interface ReportService {

    /**
     * @param from      first day counted, inclusive. Null means twelve months back.
     * @param to        last day counted, inclusive. Null means today.
     * @param branchId  one branch, or null for every branch.
     */
    Reports reports(LocalDate from, LocalDate to, UUID branchId);
}
