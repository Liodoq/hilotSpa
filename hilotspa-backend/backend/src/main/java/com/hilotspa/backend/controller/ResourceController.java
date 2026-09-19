package com.hilotspa.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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

import com.hilotspa.backend.model.ResourceDtos.AuditRow;
import com.hilotspa.backend.model.ResourceDtos.LeaveDto;
import com.hilotspa.backend.model.ResourceDtos.LeaveWrite;
import com.hilotspa.backend.model.ResourceDtos.RoomDto;
import com.hilotspa.backend.model.ResourceDtos.RoomWrite;
import com.hilotspa.backend.model.ResourceDtos.TherapistDto;
import com.hilotspa.backend.model.ResourceDtos.TherapistWrite;
import com.hilotspa.backend.services.ResourceService;
import com.hilotspa.backend.services.TherapistLeaveService;

/**
 * Staff and admin operational data.
 *
 * Every route here is branch-scoped inside the service, from the JWT — the
 * controller deliberately does not accept a branch for STAFF callers.
 */
@RestController
@RequestMapping("/api/v1")
public class ResourceController {

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private TherapistLeaveService leaveService;

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

    // ------------------------------------------------------- planned time off

    /**
     * A therapist's days off, each with the visits it clashes with (3.33).
     *
     * Under /therapists/**, so STAFF and ADMIN, which is right: rostering is a
     * front-desk job and the person who takes the phone call asking for the day
     * off is the person who should be able to write it down.
     */
    @GetMapping("/therapists/{id}/leave")
    public ResponseEntity<List<LeaveDto>> leave(@PathVariable UUID id) {
        return ResponseEntity.ok(leaveService.forTherapist(id));
    }

    /**
     * Record a day off. Never refused because of existing bookings.
     *
     * The person IS off; the visits are what have to change. Refusing would
     * leave the desk unable to write down a fact that is already true. The
     * response names the clashes instead, so they can be moved deliberately.
     */
    @PostMapping("/therapists/{id}/leave")
    public ResponseEntity<LeaveDto> addLeave(@PathVariable UUID id,
                                             @RequestBody LeaveWrite body) {
        return new ResponseEntity<>(leaveService.create(id, body), HttpStatus.CREATED);
    }

    @DeleteMapping("/therapists/leave/{leaveId}")
    public ResponseEntity<Void> removeLeave(@PathVariable UUID leaveId) {
        leaveService.delete(leaveId);
        return ResponseEntity.noContent().build();
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
    /**
     * Delete a therapist who has never been used. 409 with a reason otherwise.
     *
     * Deliberately narrow: this is for correcting a mistake, not for retiring
     * somebody. See ResourceServiceImpl#deleteTherapist.
     */
    @DeleteMapping("/therapists/{id}")
    public ResponseEntity<Void> deleteTherapist(@PathVariable UUID id) {
        resourceService.deleteTherapist(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/rooms/{id}")
    public ResponseEntity<Void> deleteRoom(@PathVariable UUID id) {
        resourceService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit-log")
    public ResponseEntity<List<AuditRow>> auditLog(
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(resourceService.auditLog(action, limit));
    }
}
