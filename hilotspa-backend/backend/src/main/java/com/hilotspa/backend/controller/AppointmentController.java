package com.hilotspa.backend.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.hilotspa.backend.model.BookingDtos.Availability;
import com.hilotspa.backend.model.BookingDtos.BookRequest;
import com.hilotspa.backend.model.BookingDtos.Booking;
import com.hilotspa.backend.model.BookingDtos.ScheduleRow;
import com.hilotspa.backend.model.BookingDtos.SlotTaken;
import com.hilotspa.backend.model.BookingDtos.WalkInRequest;
import com.hilotspa.backend.services.BookingService;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    @Autowired
    private BookingService bookingService;

    /** The caller's own appointments. Never anyone else's. */
    @GetMapping("/mine")
    public ResponseEntity<List<Booking>> mine() {
        return ResponseEntity.ok(bookingService.mine());
    }

    /**
     * S5 - a client who walked in. STAFF and ADMIN only.
     *
     * The branch comes from the token, so there is no branch to pass and none to
     * tamper with. Same transaction and same therapist/room assignment as an
     * online booking, so the two channels cannot award the same therapist.
     */
    @PostMapping("/walk-in")
    public ResponseEntity<?> walkIn(@RequestBody WalkInRequest body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(bookingService.bookWalkIn(body));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() != HttpStatus.CONFLICT) {
                throw e;
            }
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new SlotTaken(
                    "No therapist or room is free for that time. Try another, or free "
                    + "someone up on the resources screen.", List.of()));
        }
    }

    /**
     * The branch day sheet. STAFF and ADMIN only - the branch comes from the
     * token, so there is no way to ask for someone else's.
     */
    @GetMapping("/schedule")
    public ResponseEntity<List<ScheduleRow>> schedule(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID branchId) {
        return ResponseEntity.ok(bookingService.schedule(date, branchId));
    }

    /** Open start times for a service, scoped to the assessment's branch. */
    @GetMapping("/availability")
    public ResponseEntity<Availability> availability(
            @RequestParam UUID formId,
            @RequestParam UUID serviceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(bookingService.availability(formId, serviceId, from, days));
    }

    /**
     * Create the appointment.
     *
     * On 409 the body carries fresh alternatives, so the assistant can say "that
     * one just went, here are the closest" without a second round trip.
     */
    @PostMapping
    public ResponseEntity<?> book(@RequestBody BookRequest body) {
        try {
            Booking booking = bookingService.book(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(booking);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() != HttpStatus.CONFLICT) {
                throw e;
            }
            Availability fresh = bookingService.availability(
                    body.formId(), body.serviceId(), body.start().toLocalDate(), 7);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new SlotTaken(
                    "That time was just taken. Here are the closest times still open.",
                    fresh.slots().stream().limit(4).toList()));
        }
    }
}
