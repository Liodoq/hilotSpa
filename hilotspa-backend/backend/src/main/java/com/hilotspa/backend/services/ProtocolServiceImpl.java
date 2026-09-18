package com.hilotspa.backend.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hilotspa.backend.config.CurrentUser;
import com.hilotspa.backend.entities.AuditLog;
import com.hilotspa.backend.entities.ComplaintType;
import com.hilotspa.backend.entities.Massage;
import com.hilotspa.backend.entities.ProtocolRule;
import com.hilotspa.backend.entities.ServiceProtocol;
import com.hilotspa.backend.model.ProtocolDtos.Coverage;
import com.hilotspa.backend.model.ProtocolDtos.ImportLine;
import com.hilotspa.backend.model.ProtocolDtos.ImportRequest;
import com.hilotspa.backend.model.ProtocolDtos.ImportResult;
import com.hilotspa.backend.model.ProtocolDtos.ProtocolCreate;
import com.hilotspa.backend.model.ProtocolDtos.ProtocolRow;
import com.hilotspa.backend.model.ProtocolDtos.SignRequest;
import com.hilotspa.backend.model.ProtocolDtos.SignResult;
import com.hilotspa.backend.model.ProtocolDtos.ProtocolWrite;
import com.hilotspa.backend.repository.AuditLogRepository;
import com.hilotspa.backend.repository.MassageRepository;
import com.hilotspa.backend.repository.ServiceProtocolRepository;
import com.hilotspa.backend.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * X2 — the signed contraindication table.
 *
 * The rows that matter clinically are the CONTRAINDICATED ones, and the spa has
 * not authored any yet. That is why `signed` is computed from authoredBy rather
 * than stored: a placeholder author reads as unsigned in the UI, so nobody can
 * mistake seeded data for a practitioner's decision (§D3, paper-deltas).
 */
@Slf4j
@Service
public class ProtocolServiceImpl implements ProtocolService {

    /** Anything containing this is a seeded placeholder, not a signature. */
    private static final String PLACEHOLDER = "AWAITING";

    @Autowired private ServiceProtocolRepository protocolRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MassageRepository massageRepository;

    @Value("${hilotspa.node.id:local-dev}") private String nodeId;

    @Override
    public List<ProtocolRow> all() {
        return protocolRepository.findAll().stream()
                .sorted(Comparator
                        .comparing((ServiceProtocol p) -> p.getService().getName(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(p -> p.getCondition().name()))
                .map(this::toRow)
                .toList();
    }

    @Override
    @Transactional
    public ProtocolRow update(UUID id, ProtocolWrite body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body is required");
        }
        if (body.authoredBy() == null || body.authoredBy().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A rule needs a name against it. Who is authorising this change?");
        }
        if (body.authoredBy().toUpperCase().contains(PLACEHOLDER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That is the placeholder text, not a signature.");
        }

        ServiceProtocol p = protocolRepository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));

        ProtocolRule was = p.getRule();
        if (body.rule() != null && !body.rule().isBlank()) {
            try {
                p.setRule(ProtocolRule.valueOf(body.rule().trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Rule must be INDICATED or CONTRAINDICATED");
            }
        }
        if (body.rationale() != null) {
            p.setRationale(body.rationale().isBlank() ? null : body.rationale().trim());
        }
        p.setAuthoredBy(body.authoredBy().trim());

        ServiceProtocol saved = protocolRepository.save(p);

        audit(saved, was);
        return toRow(saved);
    }

    @Override
    @Transactional
    public ProtocolRow create(ProtocolCreate body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body is required");
        }
        String by = signature(body.authoredBy());
        List<Massage> services = matchServices(body.serviceName());
        if (services.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No service is called \"" + body.serviceName() + "\". "
                    + "Add it to the service menu first, or correct the spelling.");
        }
        ComplaintType condition = matchCondition(body.condition());
        if (condition == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "\"" + body.condition() + "\" is not one of the Appendix A conditions.");
        }
        ProtocolRule rule = matchRule(body.rule());
        if (rule == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rule must be INDICATED or CONTRAINDICATED.");
        }

        // The same treatment at two lengths gets the rule on both: it is one
        // clinical judgement, and a contraindication does not stop applying at
        // 90 minutes. Name a length to single one out.
        ServiceProtocol first = null;
        for (Massage service : services) {
            if (find(service.getId(), condition) != null) {
                if (services.size() == 1) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "There is already a rule for " + service.getName() + " and "
                            + label(condition.name()) + ". Edit that one instead.");
                }
                continue;   // one length already ruled; do the others
            }
            ServiceProtocol saved = write(service, condition, rule, body.rationale(), by);
            audit(saved, null);
            if (first == null) { first = saved; }
        }
        if (first == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Every length of " + services.get(0).getName() + " already has a rule for "
                    + label(condition.name()) + ". Edit those instead.");
        }
        return toRow(first);
    }

    @Override
    @Transactional
    public SignResult signAll(SignRequest body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body is required");
        }
        String by = signature(body.authoredBy());

        List<ServiceProtocol> all = protocolRepository.findAll();
        int signed = 0;
        int already = 0;

        for (ServiceProtocol p : all) {
            String author = p.getAuthoredBy() == null ? "" : p.getAuthoredBy();
            boolean unsigned = author.isBlank() || author.toUpperCase().contains(PLACEHOLDER);
            if (!unsigned) {
                // Somebody has already put their name to this one. Overwriting
                // it would quietly reassign responsibility for a clinical
                // decision to a person who never saw that particular rule.
                already++;
                continue;
            }
            p.setAuthoredBy(by);
            ServiceProtocol saved = protocolRepository.save(p);
            signAudit(saved, by, body.note());
            signed++;
        }

        String note = signed == 0
                ? (already == 0
                    ? "There are no rules to sign."
                    : "Every rule already carries a name. Nothing changed.")
                : signed + (signed == 1 ? " rule now carries" : " rules now carry") + " that name."
                  + (already > 0 ? " " + already + " already had one and were left alone." : "");
        return new SignResult(signed, already, note);
    }

    /** One audit row per rule. The question afterwards is always about ONE rule. */
    private void signAudit(ServiceProtocol p, String by, String note) {
        try {
            AuditLog row = new AuditLog();
            row.setAction("PROTOCOL_SIGNED");
            row.setEntityType("ServiceProtocol");
            row.setEntityId(p.getId());
            row.setOriginNodeId(nodeId);
            row.setDetails(p.getService().getName() + " x " + p.getCondition().name()
                    + ": " + p.getRule() + " / signed by " + by
                    + (note == null || note.isBlank() ? "" : " / " + note.trim()));
            CurrentUser.id().flatMap(userRepository::findById).ifPresent(row::setActor);
            auditLogRepository.save(row);
        } catch (RuntimeException e) {
            log.warn("audit write failed for PROTOCOL_SIGNED {} - {}", p.getId(), e.toString());
        }
    }

    @Override
    @Transactional
    public void delete(UUID id, String authoredBy) {
        String by = signature(authoredBy);
        ServiceProtocol p = protocolRepository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));

        // Everything about the rule, captured BEFORE it stops existing. An audit
        // line saying "a rule was deleted" is not an audit trail; the question
        // anyone will ask afterwards is which rule, and what did it say.
        String gone = p.getService().getName() + " x " + p.getCondition().name()
                + ": " + p.getRule()
                + (p.getRationale() == null ? "" : " / \"" + p.getRationale() + "\"")
                + " / was signed by " + p.getAuthoredBy();

        protocolRepository.delete(p);

        try {
            AuditLog row = new AuditLog();
            row.setAction("PROTOCOL_DELETED");
            row.setEntityType("ServiceProtocol");
            row.setEntityId(id);
            row.setOriginNodeId(nodeId);
            row.setDetails("REMOVED " + gone + " / removed by " + by);
            CurrentUser.id().flatMap(userRepository::findById).ifPresent(row::setActor);
            auditLogRepository.save(row);
        } catch (RuntimeException e) {
            log.warn("audit write failed for PROTOCOL_DELETED {} - {}", id, e.toString());
        }
    }

    /**
     * Load a whole file of rules.
     *
     * Parsed here rather than in the browser on purpose: the same file has to be
     * loadable from a script or a second node later, and a parser that lives in
     * a component cannot be called by either.
     */
    @Override
    @Transactional
    public ImportResult importCsv(ImportRequest body) {
        if (body == null || body.csv() == null || body.csv().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There is nothing in that file.");
        }
        String fileAuthor = body.authoredBy() == null ? "" : body.authoredBy().trim();

        List<ImportLine> lines = new ArrayList<>();
        int created = 0, updated = 0, unchanged = 0, rejected = 0;

        String[] rows = body.csv().replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (int i = 0; i < rows.length; i++) {
            String raw = rows[i];
            int lineNo = i + 1;
            if (raw == null || raw.isBlank()) {
                continue;
            }
            List<String> cells = splitCsv(raw);

            // The header, however it is spelled. Skipping it by POSITION would
            // throw away the first real row of a file saved without one.
            if (i == 0 && cells.size() > 1
                    && cells.get(0).trim().equalsIgnoreCase("service")) {
                continue;
            }
            if (cells.size() < 3) {
                lines.add(new ImportLine(lineNo, cell(cells, 0), cell(cells, 1), "REJECTED",
                        "Needs at least service, condition and rule."));
                rejected++;
                continue;
            }

            String serviceName = cell(cells, 0);
            String conditionRaw = cell(cells, 1);
            String ruleRaw = cell(cells, 2);
            String rationale = cell(cells, 3);
            String rowAuthor = cells.size() > 4 && !cell(cells, 4).isBlank()
                    ? cell(cells, 4) : fileAuthor;

            List<Massage> services = matchServices(serviceName);
            if (services.isEmpty()) {
                // The commonest failure by a distance, and the one worth naming
                // precisely: a spreadsheet says "Hilot (60 min)" and the menu
                // says "Hilot 60". A named length that matches nothing lands
                // here too, which is right - it is a typo, not a wider match.
                lines.add(new ImportLine(lineNo, serviceName, conditionRaw, "REJECTED",
                        "No service is called this, at that length. Check it against "
                        + "the service menu."));
                rejected++;
                continue;
            }
            ComplaintType condition = matchCondition(conditionRaw);
            if (condition == null) {
                lines.add(new ImportLine(lineNo, serviceName, conditionRaw, "REJECTED",
                        "Not one of the Appendix A conditions."));
                rejected++;
                continue;
            }
            ProtocolRule rule = matchRule(ruleRaw);
            if (rule == null) {
                lines.add(new ImportLine(lineNo, serviceName, conditionRaw, "REJECTED",
                        "Rule must be INDICATED or CONTRAINDICATED, not \"" + ruleRaw + "\"."));
                rejected++;
                continue;
            }
            if (rowAuthor.isBlank()) {
                lines.add(new ImportLine(lineNo, serviceName, conditionRaw, "REJECTED",
                        "No name against this rule. A safety rule nobody signed is the "
                        + "one thing this table exists to prevent."));
                rejected++;
                continue;
            }
            if (rowAuthor.toUpperCase().contains(PLACEHOLDER)) {
                // Almost always a straight re-import of the exported file with
                // the placeholder still in the column. Saying "no name" there
                // reads as a fault in the import; saying THIS tells them what
                // signing actually consists of.
                lines.add(new ImportLine(lineNo, serviceName, conditionRaw, "REJECTED",
                        "This row still carries the seeded placeholder instead of a name. "
                        + "Replace it with the practitioner's name - that IS the signature."));
                rejected++;
                continue;
            }

            // One line can touch several rows: the same treatment at two
            // lengths is one clinical judgement. The outcome reported is the
            // strongest thing that happened, so a line that created one rule
            // and left another alone reads as CREATED rather than as nothing.
            String shown = describe(services);
            boolean anyCreated = false;
            boolean anyUpdated = false;

            for (Massage service : services) {
                ServiceProtocol existing = find(service.getId(), condition);
                if (existing == null) {
                    write(service, condition, rule, rationale, rowAuthor);
                    anyCreated = true;
                    continue;
                }
                boolean same = existing.getRule() == rule
                        && java.util.Objects.equals(
                                existing.getRationale(), blankToNull(rationale))
                        && rowAuthor.equals(existing.getAuthoredBy());
                if (same) {
                    continue;
                }
                ProtocolRule was = existing.getRule();
                existing.setRule(rule);
                existing.setRationale(blankToNull(rationale));
                existing.setAuthoredBy(rowAuthor);
                audit(protocolRepository.save(existing), was);
                anyUpdated = true;
            }

            if (anyCreated) {
                lines.add(new ImportLine(lineNo, shown, label(condition.name()), "CREATED", null));
                created++;
            } else if (anyUpdated) {
                lines.add(new ImportLine(lineNo, shown, label(condition.name()), "UPDATED", null));
                updated++;
            } else {
                lines.add(new ImportLine(lineNo, shown, label(condition.name()), "UNCHANGED", null));
                unchanged++;
            }
        }

        String note = rejected == 0
                ? "Every row was read."
                : rejected + (rejected == 1 ? " row was" : " rows were")
                  + " not loaded. Nothing else was affected - the rows below say why.";
        return new ImportResult(created, updated, unchanged, rejected, note, lines);
    }

    @Override
    public Coverage coverage() {
        List<Massage> services = massageRepository.findAll();
        Set<UUID> ruled = protocolRepository.findAll().stream()
                .map(p -> p.getService().getId())
                .collect(java.util.stream.Collectors.toSet());

        List<String> uncovered = services.stream()
                .filter(m -> Boolean.TRUE.equals(m.getActive()))
                .filter(m -> !ruled.contains(m.getId()))
                .map(Massage::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        List<ServiceProtocol> all = protocolRepository.findAll();
        int unsigned = (int) all.stream()
                .filter(p -> p.getAuthoredBy() == null || p.getAuthoredBy().isBlank()
                        || p.getAuthoredBy().toUpperCase().contains(PLACEHOLDER))
                .count();
        int contra = (int) all.stream()
                .filter(p -> p.getRule() == ProtocolRule.CONTRAINDICATED).count();

        return new Coverage(services.size(), uncovered.size(), all.size(),
                unsigned, contra, uncovered);
    }

    // ------------------------------------------------------------- matching

    /**
     * A service name as a person typed it, matched to the catalogue.
     *
     * Returns EVERY match, and the caller applies the rule to all of them.
     *
     * This used to return one service and REFUSE when two shared a name, on the
     * reasoning that guessing would attach a clinical rule to the wrong
     * service. That reasoning was right and the conclusion was wrong for this
     * catalogue: the spa sells the same treatment at two lengths - Hilotin
     * Signature at 60 and at 90 - and they are the same treatment clinically.
     * A contraindication for bone setting does not stop applying at 90 minutes.
     * Refusing meant no rule could be written for most of the menu; forcing one
     * row per length would double the table for no clinical reason.
     *
     * A length CAN still be named to single one out - "Hilotin Signature
     * Massage (90 min)" or "... 90" - and then only that row is matched.
     *
     * Matching is exact, then case-insensitive, then ignoring punctuation and
     * spacing. It stops there. No fuzzy distance: a near-miss silently attached
     * to the wrong treatment is worse than a rejected row somebody has to look
     * at.
     */
    private List<Massage> matchServices(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String want = name.trim();
        List<Massage> all = massageRepository.findAll();

        // An explicit length, written any of the ways a person writes one.
        Integer wantMinutes = null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)[\\s(\\-,]+(\\d{2,3})\\s*(min|mins|minutes)?\\s*\\)?\\s*$")
                .matcher(want);
        if (m.find()) {
            wantMinutes = Integer.valueOf(m.group(1));
            want = want.substring(0, m.start()).trim();
        }

        List<Massage> hits = pick(all, want);
        if (hits.isEmpty()) {
            return hits;
        }
        if (wantMinutes != null) {
            final int mins = wantMinutes;
            List<Massage> exact = hits.stream()
                    .filter(x -> x.getDurationMinute() != null && x.getDurationMinute() == mins)
                    .toList();
            // A length that matches nothing is a typo worth reporting, not a
            // reason to silently widen back to every length.
            return exact;
        }
        return hits;
    }

    /** Exact, then case-insensitive, then punctuation-and-spacing-insensitive. */
    private static List<Massage> pick(List<Massage> all, String want) {
        List<Massage> out = all.stream().filter(x -> want.equals(x.getName())).toList();
        if (!out.isEmpty()) { return out; }
        out = all.stream().filter(x -> want.equalsIgnoreCase(x.getName())).toList();
        if (!out.isEmpty()) { return out; }
        String loose = squash(want);
        return all.stream().filter(x -> squash(x.getName()).equals(loose)).toList();
    }

    /** "Hilotin Signature Massage (60, 90 min)" - what the import reports back. */
    private static String describe(List<Massage> hits) {
        if (hits.size() == 1) {
            Massage only = hits.get(0);
            return only.getName()
                 + (only.getDurationMinute() == null ? "" : " (" + only.getDurationMinute() + " min)");
        }
        String mins = hits.stream()
                .map(x -> x.getDurationMinute() == null ? "?" : String.valueOf(x.getDurationMinute()))
                .sorted()
                .collect(java.util.stream.Collectors.joining(", "));
        return hits.get(0).getName() + " (" + mins + " min)";
    }

    private static String squash(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /** "Lower Back Pain", "LOWER_BACK_PAIN" and "lower back pain" are one thing. */
    private static ComplaintType matchCondition(String raw) {
        if (raw == null || raw.isBlank()) { return null; }
        String loose = squash(raw);
        for (ComplaintType c : ComplaintType.values()) {
            if (squash(c.name()).equals(loose) || squash(c.getDisplayName()).equals(loose)) {
                return c;
            }
        }
        return null;
    }

    private static ProtocolRule matchRule(String raw) {
        if (raw == null || raw.isBlank()) { return null; }
        String loose = squash(raw);
        if (loose.equals("indicated") || loose.equals("yes")) { return ProtocolRule.INDICATED; }
        if (loose.equals("contraindicated") || loose.equals("no")) {
            return ProtocolRule.CONTRAINDICATED;
        }
        return null;
    }

    private ServiceProtocol find(UUID serviceId, ComplaintType condition) {
        return protocolRepository.findByServiceId(serviceId).stream()
                .filter(p -> p.getCondition() == condition)
                .findFirst().orElse(null);
    }

    private ServiceProtocol write(Massage service, ComplaintType condition,
                                  ProtocolRule rule, String rationale, String by) {
        ServiceProtocol p = new ServiceProtocol();
        p.setService(service);
        p.setCondition(condition);
        p.setRule(rule);
        p.setRationale(blankToNull(rationale));
        p.setAuthoredBy(by);
        return protocolRepository.save(p);
    }

    private String signature(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A rule needs a name against it. Who is authorising this?");
        }
        if (raw.toUpperCase().contains(PLACEHOLDER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That is the placeholder text, not a signature.");
        }
        return raw.trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String cell(List<String> cells, int i) {
        return i < cells.size() ? cells.get(i).trim() : "";
    }

    /**
     * One CSV line into cells.
     *
     * Quotes matter here and a split on "," would not do: a rationale is a
     * sentence, and "Availed in 26 of 28 records, per the archive" is the exact
     * shape of thing somebody will write in that column.
     */
    private static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private ProtocolRow toRow(ServiceProtocol p) {
        String author = p.getAuthoredBy() == null ? "" : p.getAuthoredBy();
        boolean signed = !author.isBlank() && !author.toUpperCase().contains(PLACEHOLDER);
        return new ProtocolRow(
                p.getId(),
                p.getService().getId(),
                p.getService().getName(),
                p.getCondition().name(),
                label(p.getCondition().name()),
                p.getRule().name(),
                p.getRationale(),
                author,
                signed,
                p.getCreatedAt());
    }

    /** LOWER_BACK_PAIN -> Lower Back Pain. Enum constants are not words. */
    private static String label(String raw) {
        StringBuilder out = new StringBuilder();
        for (String w : raw.split("_")) {
            if (w.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase());
        }
        return out.toString();
    }

    /**
     * A change to the safety table is the single most consequential write in the
     * system, so it is logged with both the old and the new rule. Never silently.
     */
    private void audit(ServiceProtocol p, ProtocolRule was) {
        try {
            AuditLog row = new AuditLog();
            // A brand-new rule is not an edit, and the audit trail is the one
            // place that distinction has to survive: "null -> INDICATED" reads
            // as a corrupted row rather than as an authorship.
            row.setAction(was == null ? "PROTOCOL_CREATED" : "PROTOCOL_EDITED");
            row.setEntityType("ServiceProtocol");
            row.setEntityId(p.getId());
            row.setOriginNodeId(nodeId);
            row.setDetails(p.getService().getName() + " x " + p.getCondition().name()
                    + ": " + (was == null ? "authored as " + p.getRule()
                                          : was + " -> " + p.getRule())
                    + " / signed by " + p.getAuthoredBy());
            CurrentUser.id().flatMap(userRepository::findById).ifPresent(row::setActor);
            auditLogRepository.save(row);
        } catch (RuntimeException e) {
            log.warn("audit write failed for PROTOCOL_EDITED {} - {}", p.getId(), e.toString());
        }
    }
}
