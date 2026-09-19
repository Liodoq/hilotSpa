package com.hilotspa.backend.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hilotspa.backend.entities.TherapistLeave;

@Repository
public interface TherapistLeaveRepository extends JpaRepository<TherapistLeave, UUID> {

    List<TherapistLeave> findByTherapistIdOrderByStartsOnAsc(UUID therapistId);

    /**
     * Every leave that touches a window, for a set of therapists.
     *
     * One query for the whole calendar rather than one per day per therapist -
     * the same reason the booked-appointment list is loaded once. Overlap is
     * inclusive at both ends: a leave starting on the window's last day still
     * matters.
     */
    List<TherapistLeave> findByTherapistIdInAndEndsOnGreaterThanEqualAndStartsOnLessThanEqual(
            Collection<UUID> therapistIds, LocalDate windowStart, LocalDate windowEnd);

    /** Does this therapist have leave covering that date? */
    boolean existsByTherapistIdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqual(
            UUID therapistId, LocalDate onDate, LocalDate sameDate);
}
