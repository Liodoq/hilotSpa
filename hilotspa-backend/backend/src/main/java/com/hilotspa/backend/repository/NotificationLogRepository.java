package com.hilotspa.backend.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hilotspa.backend.entities.NotificationKind;
import com.hilotspa.backend.entities.NotificationLog;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    /**
     * Everything already recorded for this batch of appointments.
     *
     * One query for the whole run rather than one per appointment: tomorrow is
     * tens of rows today and hundreds when the second branch is live, and a
     * scheduled job that quietly becomes N+1 is a job nobody is watching when
     * it starts to hurt.
     */
    List<NotificationLog> findByKindAndAppointmentIdIn(
            NotificationKind kind, Collection<UUID> appointmentIds);

    /** Every branch, newest first. Administrators only. */
    List<NotificationLog> findTop200ByOrderByCreatedAtDesc();

    /** One branch's own rows, newest first. What a front desk is allowed to see. */
    List<NotificationLog> findTop200ByAppointmentBranchIdInOrderByCreatedAtDesc(
            Collection<UUID> branchIds);

    List<NotificationLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);
}
