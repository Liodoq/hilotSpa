package com.hilotspa.backend.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // The same two questions, asked while IGNORING one appointment.
    //
    // Rescheduling needs this and nothing else does. A visit being moved is
    // still sitting in the table at its old time, so without the exclusion it
    // collides with itself and the front desk is told the therapist is busy -
    // busy with the very visit they are trying to move.

    boolean existsByTherapistIdAndIdNotAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            UUID therapistId, UUID excludeAppointmentId,
            Collection<AppointmentStatus> statuses,
            LocalDateTime candidateEnd, LocalDateTime candidateStart);

    boolean existsByRoomIdAndIdNotAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            UUID roomId, UUID excludeAppointmentId,
            Collection<AppointmentStatus> statuses,
            LocalDateTime candidateEnd, LocalDateTime candidateStart);

    // ------------------------------------------------------------- A3 reports
    //
    // Aggregates, not lists. The alternative - load every appointment in the
    // range and count them in Java - is the same answer and a table scan into
    // the heap; at a year of two branches it is merely wasteful, and it is the
    // kind of thing that stops working exactly when the spa has enough history
    // to make the report worth reading.
    //
    // Branch is a list rather than a nullable parameter because "all branches"
    // is genuinely the set of every branch, and `:branchId is null or ...`
    // needs a typed null in Postgres to behave. The list also happens to be
    // what the second node needs.
    //
    // The window is HALF-OPEN [from, to) everywhere, matching overlaps() and
    // every other time comparison in this codebase. A visit at midnight on the
    // last day belongs to the next range, and it belongs to it consistently.

    /**
     * Visits per treatment. Returns [serviceId, name, count, revenue] rows.
     *
     * Object[] rather than an interface projection on purpose: the DTO is built
     * one layer up in ReportServiceImpl, which is also where the percentage is
     * worked out, so there is exactly one place that knows the shape.
     */
    @Query("""
            select a.service.id, a.service.name, a.service.durationMinute,
                   count(a), coalesce(sum(a.priceAtBooking), 0)
            from Appointment a
            where a.status in :statuses
              and a.branch.id in :branchIds
              and a.startTime >= :from and a.startTime < :to
            group by a.service.id, a.service.name, a.service.durationMinute
            order by count(a) desc, a.service.name asc, a.service.durationMinute asc
            """)
    List<Object[]> countByService(@Param("statuses") Collection<AppointmentStatus> statuses,
                                  @Param("branchIds") Collection<UUID> branchIds,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    /** Visits per calendar month. Returns [year, month, count, revenue] rows. */
    @Query("""
            select year(a.startTime), month(a.startTime), count(a),
                   coalesce(sum(a.priceAtBooking), 0)
            from Appointment a
            where a.status in :statuses
              and a.branch.id in :branchIds
              and a.startTime >= :from and a.startTime < :to
            group by year(a.startTime), month(a.startTime)
            order by year(a.startTime) asc, month(a.startTime) asc
            """)
    List<Object[]> countByMonth(@Param("statuses") Collection<AppointmentStatus> statuses,
                                @Param("branchIds") Collection<UUID> branchIds,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);

    /**
     * Visits per BRANCH. Returns [branchId, name, count, revenue] rows.
     *
     * Only an administrator ever sees more than one of these. It is the answer
     * to "which branch is carrying the spa", which is a question that only
     * exists once there are two nodes.
     */
    @Query("""
            select a.branch.id, a.branch.name, count(a), coalesce(sum(a.priceAtBooking), 0)
            from Appointment a
            where a.status in :statuses
              and a.branch.id in :branchIds
              and a.startTime >= :from and a.startTime < :to
            group by a.branch.id, a.branch.name
            order by count(a) desc, a.branch.name asc
            """)
    List<Object[]> countByBranch(@Param("statuses") Collection<AppointmentStatus> statuses,
                                 @Param("branchIds") Collection<UUID> branchIds,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);

    /**
     * Which node actually WROTE each branch's bookings. [branchId, originNodeId].
     *
     * Read from the rows rather than assumed from configuration. A branch whose
     * bookings were all written by the Bulan node is a fact about the data; a
     * branch labelled with whichever node happens to be answering this request
     * is a guess, and the guess is wrong in exactly the situation the two-node
     * architecture exists for.
     */
    @Query("""
            select distinct a.branch.id, a.originNodeId
            from Appointment a
            where a.status in :statuses
              and a.branch.id in :branchIds
              and a.startTime >= :from and a.startTime < :to
            """)
    List<Object[]> nodesByBranch(@Param("statuses") Collection<AppointmentStatus> statuses,
                                 @Param("branchIds") Collection<UUID> branchIds,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);

    /** Does this range contain anything at all on this basis? Decides COMPLETED vs BOOKED. */
    @Query("""
            select count(a) from Appointment a
            where a.status in :statuses
              and a.branch.id in :branchIds
              and a.startTime >= :from and a.startTime < :to
            """)
    long countInRange(@Param("statuses") Collection<AppointmentStatus> statuses,
                      @Param("branchIds") Collection<UUID> branchIds,
                      @Param("from") LocalDateTime from,
                      @Param("to") LocalDateTime to);
}