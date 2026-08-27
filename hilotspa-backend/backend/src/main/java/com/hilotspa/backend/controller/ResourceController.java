package com.hilotspa.backend.controller;

import java.util.List;
import java.util.UUID;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hilotspa.backend.model.ResourceDtos.AuditRow;
import com.hilotspa.backend.model.ResourceDtos.RoomDto;
import com.hilotspa.backend.model.ResourceDtos.RoomWrite;
import com.hilotspa.backend.model.ResourceDtos.TherapistDto;
import com.hilotspa.backend.model.ResourceDtos.TherapistWrite;
import com.hilotspa.backend.services.ResourceService;

/**
 * Staff and admin operational data.
 *
 * Every route here is branch-scoped inside the service, from the JWT — the
 * controller deliberately does not accept a branch for STAFF callers.
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "http://localhost:4200")
public class ResourceController {

    @Autowired
    private ResourceService resourceService;

    // ------------------------------------------------------------ therapists

    /** branchId is honoured only for an administrator — see ResourceServiceImpl. */
    @GetMapping("/therapists")
    public ResponseEntity<List<TherapistDto>> therapists(
            @RequestParam(required = false) UUID branchId) {
        return ResponseEntity.ok(resourceService.therapists(branchId));
    }

    @PostMapping("/therapists")
    public ResponseEntity<TherapistDto> createTherapist(@RequestBody TherapistWrite body) {
        return new ResponseEntity<>(resourceService.saveTherapist(null, body), HttpStatus.CREATED);
    }

    @PutMapping("/therapists/{id}")
    public ResponseEntity<TherapistDto> updateTherapist(
            @PathVariable UUID id, @RequestBody TherapistWrite body) {
        return ResponseEntity.ok(resourceService.saveTherapist(id, body));
    }

    // ----------------------------------------------------------------- rooms

    @GetMapping("/rooms")
    public ResponseEntity<List<RoomDto>> rooms(
            @RequestParam(required = false) UUID branchId) {
        return ResponseEntity.ok(resourceService.rooms(branchId));
    }

    @PostMapping("/rooms")
    public ResponseEntity<RoomDto> createRoom(@RequestBody RoomWrite body) {
        return new ResponseEntity<>(resourceService.saveRoom(null, body), HttpStatus.CREATED);
    }

    @PutMapping("/rooms/{id}")
    public ResponseEntity<RoomDto> updateRoom(
            @PathVariable UUID id, @RequestBody RoomWrite body) {
        return ResponseEntity.ok(resourceService.saveRoom(id, body));
    }

    // ------------------------------------------------------------- audit log

    /**
     * Read-only. This is the evidence trail §D3 and the reliability metric are
     * both read out of, so there is no write route and no delete route at all.
     */
    @GetMapping("/audit-log")
    public ResponseEntity<List<AuditRow>> auditLog(
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(resourceService.auditLog(action, limit));
    }
}
