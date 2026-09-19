package com.hilotspa.backend.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hilotspa.backend.entities.Appointment;
import com.hilotspa.backend.entities.AppointmentStatus;
import com.hilotspa.backend.entities.Therapist;
import com.hilotspa.backend.entities.TherapistLeave;
import com.hilotspa.backend.model.ResourceDtos.ClashRow;
import com.hilotspa.backend.model.ResourceDtos.LeaveDto;
import com.hilotspa.backend.model.ResourceDtos.LeaveWrite;
import com.hilotspa.backend.repository.AppointmentRepository;
import com.hilotspa.backend.repository.TherapistLeaveRepository;
import com.hilotspa.backend.repository.TherapistRepository;
import com.hilotspa.backend.config.CurrentUser;

/**
 * Planned time off, and what it breaks (task 3.33).
 *
 * THE SECOND HALF IS THE POINT. Stopping new bookings is easy and is only
 * half a feature: the visits already in the book do not move themselves, and a
 * system that quietly lets a therapist be off while three clients are still
 * expecting her has made the situation worse than the paper diary it replaced.
 *
 * So recording leave ALWAYS answers with the visits it now clashes with, and
 * recording it is never refused because of them. Refusing would be the wrong
 * way round - the person IS off; the bookings are what have to change - and it
 * would leave the desk with a fact they cannot write down. They are told, by
 * name and time, and the Move panel is where they act on it.
 */
@Service
public class TherapistLeaveService {

    private static final List<AppointmentStatus> LIVE = List.of(
            AppointmentStatus.PENDING,
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.IN_PROGRESS);

    @Autowired private TherapistLeaveRepository leaveRepository;
    @Autowired private TherapistRepository therapistRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    @Transactional(readOnly = true)
    public List<LeaveDto> forTherapist(UUID therapistId) {
        return leaveRepository.findByTherapistIdOrderByStartsOnAsc(therapistId).stream()
                .map(this::withClashes)
                .toList();
    }

    @Transactional
    public LeaveDto create(UUID therapistId, LeaveWrite body) {
        if (body == null || body.startsOn() == null || body.endsOn() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A day off needs a first day and a last day.");
        }
        if (body.endsOn().isBefore(body.startsOn())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The last day is before the first day.");
        }
        Therapist t = therapistRepository.findById(therapistId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Therapist not found"));

        TherapistLeave l = new TherapistLeave();
        l.setTherapist(t);
        l.setStartsOn(body.startsOn());
        l.setEndsOn(body.endsOn());
        l.setReason(body.reason() == null || body.reason().isBlank() ? null : body.reason().trim());
        l.setCreatedBy(CurrentUser.email().orElse("the front desk"));
        return withClashes(leaveRepository.save(l));
    }

    @Transactional
    public void delete(UUID leaveId) {
        // Not "restore the bookings" - there is nothing to restore. Anything
        // moved while the leave stood was moved deliberately by a person, and
        // silently moving visits back would undo their judgement.
        leaveRepository.findById(leaveId).ifPresent(leaveRepository::delete);
    }

    /**
     * The visits still standing inside a leave.
     *
     * Only LIVE statuses. A cancelled or completed visit is not a problem
     * anybody has to solve, and listing it would bury the two that are.
     */
    private LeaveDto withClashes(TherapistLeave l) {
        LocalDateTime from = l.getStartsOn().atStartOfDay();
        LocalDateTime to = l.getEndsOn().plusDays(1).atStartOfDay();

        List<ClashRow> clashes = appointmentRepository
                .findByTherapistIdAndStartTimeBetween(l.getTherapist().getId(), from, to).stream()
                .filter(a -> LIVE.contains(a.getStatus()))
                .filter(a -> a.getStartTime().isBefore(to))
                .sorted((a, b) -> a.getStartTime().compareTo(b.getStartTime()))
                .map(TherapistLeaveService::toClash)
                .toList();

        return new LeaveDto(
                l.getId(), l.getTherapist().getId(), (l.getTherapist().getFirstName() + " " + l.getTherapist().getLastName()).trim(),
                l.getStartsOn(), l.getEndsOn(), l.getReason(), clashes);
    }

    private static ClashRow toClash(Appointment a) {
        String who = a.getCustomer() != null
                ? (a.getCustomer().getFirstName() + " " + a.getCustomer().getLastName()).trim()
                : (a.getWalkInName() == null ? "Walk-in" : a.getWalkInName());
        return new ClashRow(a.getId(), a.getStartTime(), who,
                a.getService().getName(), a.getStatus().name());
    }

    /** Is this date inside any leave for this therapist? Used by the booking path. */
    @Transactional(readOnly = true)
    public boolean isOff(UUID therapistId, LocalDate date) {
        return leaveRepository
                .existsByTherapistIdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqual(
                        therapistId, date, date);
    }
}
