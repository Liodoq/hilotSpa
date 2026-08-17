package com.hilotspa.backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hilotspa.backend.entities.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByBranchIdOrderByOccurredAtDesc(UUID branchId);
    List<AuditLog> findByActorIdOrderByOccurredAtDesc(UUID actorId);
    List<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, UUID entityId);
    List<AuditLog> findByOccurredAtBetweenOrderByOccurredAtDesc(LocalDateTime from, LocalDateTime to);
}