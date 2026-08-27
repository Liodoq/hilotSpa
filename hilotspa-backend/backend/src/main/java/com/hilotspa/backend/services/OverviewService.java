package com.hilotspa.backend.services;

import com.hilotspa.backend.model.OverviewDtos.Overview;

public interface OverviewService {

    /** The administrator's aggregate, counted live. ADMIN only. */
    Overview overview();
}
