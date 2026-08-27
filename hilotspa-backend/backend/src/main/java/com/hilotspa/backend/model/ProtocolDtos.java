package com.hilotspa.backend.model;

import java.time.LocalDateTime;
import java.util.UUID;

/** X2 — the signed contraindication table. */
public final class ProtocolDtos {

    private ProtocolDtos() {
    }

    /**
     * One rule: this service, this complaint, indicated or contraindicated.
     *
     * `signed` is not a stored column. A rule counts as signed when a person's
     * name is on it — a seeded row carries a placeholder that says so in words,
     * and this flag is derived from that rather than from a checkbox somebody
     * could tick without reading anything.
     */
    public record ProtocolRow(
            UUID id,
            UUID serviceId,
            String serviceName,
            String condition,
            String conditionLabel,
            String rule,
            String rationale,
            String authoredBy,
            boolean signed,
            LocalDateTime createdAt) {
    }

    /**
     * Editing a rule requires a name. The signature is the point of the table —
     * an unsigned safety rule is an app making a clinical decision on its own.
     */
    public record ProtocolWrite(
            String rule,
            String rationale,
            String authoredBy) {
    }
}
