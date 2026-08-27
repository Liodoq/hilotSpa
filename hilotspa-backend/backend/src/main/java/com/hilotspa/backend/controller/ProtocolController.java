package com.hilotspa.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hilotspa.backend.model.ProtocolDtos.ProtocolRow;
import com.hilotspa.backend.model.ProtocolDtos.ProtocolWrite;
import com.hilotspa.backend.services.ProtocolService;

/** X2 — the signed contraindication table. Read by staff, written by ADMIN. */
@RestController
@RequestMapping("/api/v1/protocols")
public class ProtocolController {

    @Autowired
    private ProtocolService protocolService;

    @GetMapping
    public ResponseEntity<List<ProtocolRow>> all() {
        return ResponseEntity.ok(protocolService.all());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProtocolRow> update(@PathVariable UUID id,
                                              @RequestBody ProtocolWrite body) {
        return ResponseEntity.ok(protocolService.update(id, body));
    }
}
