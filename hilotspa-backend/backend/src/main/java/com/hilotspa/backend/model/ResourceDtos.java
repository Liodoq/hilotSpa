package com.hilotspa.backend.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Therapists, rooms and audit rows — the branch's operational resources. */
public final class ResourceDtos {

    private ResourceDtos() {
    }

    public record TherapistDto(
            UUID id, String firstName, String lastName,
            String status, String sex, boolean active, UUID branchId, String branchName,
            /** Enum names. EMPTY means no restriction has been recorded - never "can do
             *  nothing". See Therapist.specialties. */
            List<String> specialties) {
    }

    /** Create/update. branchId is ignored for STAFF — they get their own. */
    public record TherapistWrite(
            String firstName, String lastName, String status, String sex,
            Boolean active, UUID branchId,
            /** Null leaves them untouched; a list - even an empty one - REPLACES them.
             *  Clearing the list is a legitimate answer: it says nobody has restricted
             *  this therapist, which is how "can do everything" is expressed. */
            List<String> specialties) {
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
