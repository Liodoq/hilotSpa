package com.hilotspa.backend.services;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hilotspa.backend.config.CurrentUser;
import com.hilotspa.backend.entities.AuditLog;
import com.hilotspa.backend.entities.ProtocolRule;
import com.hilotspa.backend.entities.ServiceProtocol;
import com.hilotspa.backend.model.ProtocolDtos.ProtocolRow;
import com.hilotspa.backend.model.ProtocolDtos.ProtocolWrite;
import com.hilotspa.backend.repository.AuditLogRepository;
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
            row.setAction("PROTOCOL_EDITED");
            row.setEntityType("ServiceProtocol");
            row.setEntityId(p.getId());
            row.setOriginNodeId(nodeId);
            row.setDetails(p.getService().getName() + " x " + p.getCondition().name()
                    + ": " + was + " -> " + p.getRule()
                    + " / signed by " + p.getAuthoredBy());
            CurrentUser.id().flatMap(userRepository::findById).ifPresent(row::setActor);
            auditLogRepository.save(row);
        } catch (RuntimeException e) {
            log.warn("audit write failed for PROTOCOL_EDITED {} - {}", p.getId(), e.toString());
        }
    }
}
