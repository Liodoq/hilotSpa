package com.hilotspa.backend.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hilotspa.backend.entities.Appointment;
import com.hilotspa.backend.entities.AppointmentStatus;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByBranchId(UUID branchId);
    List<Appointment> findByCustomerId(UUID customerId);
    List<Appointment> findByBranchIdAndStartTimeBetween(UUID branchId, LocalDateTime from, LocalDateTime to);

    /**
     * The visits that came out of one assessment.
     *
     * A form can produce more than one - a client may book twice off a single
     * pre-assessment - so this returns a list and the caller decides which one
     * to show. B92: the FK has existed since the entity was written and nothing
     * ever read it, which is why a finished session displayed no room.
     */
    List<Appointment> findByFormId(UUID formId);

    /**
     * Has this therapist EVER been on an appointment - past, cancelled, any
     * status at all?
     *
     * The question a safe delete has to ask. A cancelled visit still happened as
     * a record, and the audit trail still points at it.
     */
    boolean existsByTherapistId(UUID therapistId);

    boolean existsByRoomId(UUID roomId);

    /**
     * Does this client already have a live booking overlapping this window?
     *
     * Rule 4 (task 2.36). The three rules the spa stated - a free therapist, a
     * free room, no clash with an existing booking - are all about the SPA's
     * resources. None of them notices that one person cannot be in two rooms at
     * once, so a client could hold a 9:00 Signature and a 9:00 Ventosa with
     * every rule satisfied.
     *
     * Half-open, matching overlaps() everywhere else: a 3 PM finish and a 3 PM
     * start do not collide.
     */

    List<Appointment> findByCustomerIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            UUID customerId, Collection<AppointmentStatus> statuses,
            LocalDateTime end, LocalDateTime start);

    /** The same, for a page of forms, so a history list is one query not N. */
    List<Appointment> findByFormIdIn(Collection<UUID> formIds);

    boolean existsByTherapistIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            UUID therapistId, Collection<AppointmentStatus> statuses,
            LocalDateTime candidateEnd, LocalDateTime candidateStart);

    boolean existsByRoomIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            UUID roomId, Collection<AppointmentStatus> statuses,
            LocalDateTime candidateEnd, LocalDateTime candidateStart);
}