package com.hilotspa.backend.services;

import java.util.List;
import java.util.UUID;

import com.hilotspa.backend.model.ProtocolDtos.ProtocolRow;
import com.hilotspa.backend.model.ProtocolDtos.ProtocolWrite;

/** X2 — the signed contraindication table. Read by everyone, written by ADMIN. */
public interface ProtocolService {

    List<ProtocolRow> all();

    /** Changing a rule requires a signature, and writes an audit row. */
    ProtocolRow update(UUID id, ProtocolWrite body);
}
