package com.hilotspa.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hilotspa.backend.model.ProtocolDtos.Coverage;
import com.hilotspa.backend.model.ProtocolDtos.ImportRequest;
import com.hilotspa.backend.model.ProtocolDtos.ImportResult;
import com.hilotspa.backend.model.ProtocolDtos.ProtocolCreate;
import com.hilotspa.backend.model.ProtocolDtos.ProtocolRow;
import com.hilotspa.backend.model.ProtocolDtos.SignRequest;
import com.hilotspa.backend.model.ProtocolDtos.SignResult;
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

    /**
     * How much of the catalogue the table covers.
     *
     * Open to STAFF as well, like the GET above: a front desk that can read the
     * rules should be able to see that half the menu has none.
     */
    @GetMapping("/coverage")
    public ResponseEntity<Coverage> coverage() {
        return ResponseEntity.ok(protocolService.coverage());
    }

    /** Author one rule. ADMIN, by SecurityConfig's non-GET rule on this path. */
    @PostMapping
    public ResponseEntity<ProtocolRow> create(@RequestBody ProtocolCreate body) {
        return ResponseEntity.ok(protocolService.create(body));
    }

    /**
     * Load a file of rules.
     *
     * The CSV arrives as a string in a JSON body rather than as multipart. It is
     * a few kilobytes of text, the browser has already read it, and multipart
     * would add an upload pipeline - with its own size limits and failure modes
     * - to carry something smaller than this file.
     */
    @PostMapping("/import")
    public ResponseEntity<ImportResult> importCsv(@RequestBody ImportRequest body) {
        return ResponseEntity.ok(protocolService.importCsv(body));
    }

    /**
     * Record that a practitioner signed the printed sheet.
     *
     * Not an import. One person signed one sheet covering every unsigned rule,
     * and making somebody round-trip a spreadsheet to write one name into a
     * column is how that step does not get done.
     */
    @PostMapping("/sign")
    public ResponseEntity<SignResult> sign(@RequestBody SignRequest body) {
        return ResponseEntity.ok(protocolService.signAll(body));
    }

    /**
     * Remove a rule.
     *
     * The signature travels as a query parameter because DELETE bodies are
     * inconsistently handled across the stack, and it is a person's name rather
     * than a secret. Same rule as every other write here: no name, no change.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @RequestParam(required = false) String authoredBy) {
        protocolService.delete(id, authoredBy);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProtocolRow> update(@PathVariable UUID id,
                                              @RequestBody ProtocolWrite body) {
        return ResponseEntity.ok(protocolService.update(id, body));
    }
}
