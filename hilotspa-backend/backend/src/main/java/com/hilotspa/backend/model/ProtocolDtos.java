package com.hilotspa.backend.model;

import java.time.LocalDateTime;
import java.util.List;
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
     * Create a rule.
     *
     * The service is named rather than identified by id, because the file this
     * comes from is filled in by a person reading paper records, and nobody is
     * going to copy a UUID out of a database into a spreadsheet. The name is
     * matched case-insensitively against the catalogue and an unknown one is
     * REPORTED, never created - a typo must not quietly invent a service.
     */
    public record ProtocolCreate(
            String serviceName,
            String condition,
            String rule,
            String rationale,
            String authoredBy) {
    }

    /**
     * Record that a practitioner signed the printed sheet.
     *
     * One person, one sheet, N rules - which is what actually happens, and why
     * this is not an import. Nothing about any RULE changes: only who is
     * answerable for it.
     */
    public record SignRequest(
            String authoredBy,
            /** Free text kept in the audit row, e.g. the date on the paper sheet. */
            String note) {
    }

    public record SignResult(int signed, int alreadySigned, String note) {
    }

    /** The import, as a whole. */
    public record ImportRequest(
            String csv,
            /**
             * Who is authorising the lot, used for any row that names nobody.
             *
             * A file is one act of authorship. Requiring the name in every row
             * of a spreadsheet gets it pasted down the column once and then
             * forgotten on the row that mattered.
             */
            String authoredBy) {
    }

    /** What happened to one line of the file. */
    public record ImportLine(
            int line,
            String serviceName,
            String condition,
            /** CREATED | UPDATED | UNCHANGED | REJECTED */
            String outcome,
            /** Set on REJECTED, and the only thing that explains the count. */
            String problem) {
    }

    /**
     * The result of an import.
     *
     * Rejected rows are returned in full, not counted. "7 rows failed" is a
     * message that sends somebody back to a spreadsheet with no idea which
     * seven, and the commonest cause is a service name that does not match the
     * catalogue by one character.
     */
    public record ImportResult(
            int created,
            int updated,
            int unchanged,
            int rejected,
            String note,
            List<ImportLine> lines) {
    }

    /**
     * How much of the catalogue the table actually covers.
     *
     * A service with no rule at all is still offerable - that is deliberate,
     * and it is why this count exists. The gap is invisible otherwise: nothing
     * on screen distinguishes "vetted and permitted" from "nobody has looked".
     */
    public record Coverage(
            int services,
            int servicesWithNoRule,
            int rules,
            int unsigned,
            int contraindications,
            List<String> uncovered) {
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
