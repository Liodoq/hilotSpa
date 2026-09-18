package com.hilotspa.backend.services;

import java.util.List;
import java.util.UUID;

import com.hilotspa.backend.model.ProtocolDtos.Coverage;
import com.hilotspa.backend.model.ProtocolDtos.ImportRequest;
import com.hilotspa.backend.model.ProtocolDtos.ImportResult;
import com.hilotspa.backend.model.ProtocolDtos.ProtocolCreate;
import com.hilotspa.backend.model.ProtocolDtos.ProtocolRow;
import com.hilotspa.backend.model.ProtocolDtos.SignRequest;
import com.hilotspa.backend.model.ProtocolDtos.SignResult;
import com.hilotspa.backend.model.ProtocolDtos.ProtocolWrite;

/** X2 — the signed contraindication table. Read by everyone, written by ADMIN. */
public interface ProtocolService {

    List<ProtocolRow> all();

    /** Changing a rule requires a signature, and writes an audit row. */
    ProtocolRow update(UUID id, ProtocolWrite body);

    /** Author one new rule. Same signature requirement as an edit. */
    ProtocolRow create(ProtocolCreate body);

    /**
     * Record that a practitioner signed the sheet.
     *
     * Sets the author on every rule that still carries the seeded placeholder,
     * and touches nothing else - not the verdict, not the rationale, and not a
     * rule somebody has already put their name to. It is the digital half of a
     * signature on paper; the paper stays the artefact.
     */
    SignResult signAll(SignRequest body);

    /**
     * Remove a rule.
     *
     * Needs a name like every other write here, and writes the WHOLE departing
     * rule into the audit row - service, condition, verdict, rationale and who
     * had signed it. The row itself is gone afterwards, so if the audit line
     * does not carry it, nothing does.
     *
     * Deleting a CONTRAINDICATED rule makes that service offerable to those
     * clients again. That is the single most consequential write in this system
     * and the caller is expected to have said so out loud first.
     */
    void delete(UUID id, String authoredBy);

    /**
     * Load a whole file of rules.
     *
     * Columns: service, condition, rule, rationale[, authored by].
     *
     * An existing (service, condition) is UPDATED rather than duplicated - the
     * table has a unique constraint on that pair, and a second import of a
     * corrected file is the normal case, not an error.
     */
    ImportResult importCsv(ImportRequest body);

    /**
     * How much of the catalogue the table covers.
     *
     * A service with no rule is still offerable. That is a deliberate choice -
     * the alternative empties the menu the moment somebody adds a service - and
     * it is exactly why this has to be counted somewhere a person will see it.
     */
    Coverage coverage();
}
