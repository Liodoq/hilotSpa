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

    boolean existsByTherapistIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            UUID therapistId, Collection<AppointmentStatus> statuses,
            LocalDateTime candidateEnd, LocalDateTime candidateStart);

    boolean existsByRoomIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            UUID roomId, Collection<AppointmentStatus> statuses,
            LocalDateTime candidateEnd, LocalDateTime candidateStart);
}