package com.hilotspa.backend.model;

import java.time.LocalDateTime;
import java.util.UUID;

/** Therapists, rooms and audit rows — the branch's operational resources. */
public final class ResourceDtos {

    private ResourceDtos() {
    }

    public record TherapistDto(
            UUID id, String firstName, String lastName,
            String status, String sex, boolean active, UUID branchId, String branchName) {
    }

    /** Create/update. branchId is ignored for STAFF — they get their own. */
    public record TherapistWrite(
            String firstName, String lastName, String status, String sex,
            Boolean active, UUID branchId) {
    }

    public record RoomDto(UUID id, String name, boolean active, UUID branchId, String branchName) {
    }

    public record RoomWrite(String name, Boolean active, UUID branchId) {
    }

    public record AuditRow(
            UUID id, String action, String entityType, UUID entityId,
            String actor, String branch, String details,
            String originNodeId, LocalDateTime occurredAt) {
    }
}
