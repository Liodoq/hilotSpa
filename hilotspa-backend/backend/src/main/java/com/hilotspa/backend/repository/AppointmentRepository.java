package com.hilotspa.backend.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Everything booked for one therapist inside a window (task 3.33).
     *
     * Used to answer "what does this day off break", which is the half of the
     * feature that matters: blocking new bookings is easy, and the visits
     * already in the book are the ones somebody has to ring about.
     */
    List<Appointment> findByTherapistIdAndStartTimeBetween(
            UUID therapistId, LocalDateTime from, LocalDateTime to);

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

    /**
     * Write a replicated appointment in, keeping the peer's id (task 3.3).
     *
     * Native, and not JpaRepository.save(), for one specific reason: the id is
     * @GeneratedValue(GenerationType.UUID). Saving a detached row whose id is
     * not yet in this database goes through merge, and merge on a row that does
     * not exist persists a copy - at which point the generator runs and assigns
     * a NEW id. The replica would land under an id the peer has never heard of,
     * the next pull would not find it, and every pull would insert another one.
     * Silent, unbounded duplication, invisible until somebody counts.
     *
     * ON CONFLICT (id) DO UPDATE is exactly the "upsert keyed by the origin's
     * id" this needs, and it is one statement, so a pull cannot half-apply.
     *
     * It also bypasses the JPA listener, which means no sync_log row and no
     * echo back to the peer - the same thing ReplicationContext does for the
     * delete path, achieved here by construction rather than by a flag.
     *
     * customer_id and form_id are always NULL: the account and the assessment
     * stay on the node that recorded them. walk_in_name carries the client's
     * name, which is what satisfies appointment_has_a_client.
     */
    @Modifying
    // On the repository method, not on the caller. Spring Data's proxy IS the
    // caller here, so the annotation is honoured - whereas a @Transactional
    // private method invoked from inside its own class is silently ignored
    // (B134). A @Modifying query with no transaction fails outright.
    @Transactional
    @Query(value = """
            INSERT INTO appointment (
                id, branch_id, service_id, therapist_id, room_id,
                customer_id, form_id, walk_in_name, walk_in_contact,
                start_time, end_time, status, payment_status, source,
                price_at_booking, notes, origin_node_id, created_at, updated_at)
            VALUES (
                :id, :branchId, :serviceId, :therapistId, :roomId,
                NULL, NULL, :clientName, NULL,
                :startTime, :endTime, :status, :paymentStatus, :source,
                :price, :notes, :originNodeId, :createdAt, :updatedAt)
            ON CONFLICT (id) DO UPDATE SET
                branch_id      = EXCLUDED.branch_id,
                service_id     = EXCLUDED.service_id,
                therapist_id   = EXCLUDED.therapist_id,
                room_id        = EXCLUDED.room_id,
                walk_in_name   = EXCLUDED.walk_in_name,
                start_time     = EXCLUDED.start_time,
                end_time       = EXCLUDED.end_time,
                status         = EXCLUDED.status,
                payment_status = EXCLUDED.payment_status,
                source         = EXCLUDED.source,
                price_at_booking = EXCLUDED.price_at_booking,
                notes          = EXCLUDED.notes,
                origin_node_id = EXCLUDED.origin_node_id,
                updated_at     = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsertReplicated(@Param("id") java.util.UUID id,
                         @Param("branchId") java.util.UUID branchId,
                         @Param("serviceId") java.util.UUID serviceId,
                         @Param("therapistId") java.util.UUID therapistId,
                         @Param("roomId") java.util.UUID roomId,
                         @Param("clientName") String clientName,
                         @Param("startTime") java.time.LocalDateTime startTime,
                         @Param("endTime") java.time.LocalDateTime endTime,
                         @Param("status") String status,
                         @Param("paymentStatus") String paymentStatus,
                         @Param("source") String source,
                         @Param("price") java.math.BigDecimal price,
                         @Param("notes") String notes,
                         @Param("originNodeId") String originNodeId,
                         @Param("createdAt") java.time.LocalDateTime createdAt,
                         @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
