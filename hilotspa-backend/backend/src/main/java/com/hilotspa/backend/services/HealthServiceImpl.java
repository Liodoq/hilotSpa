package com.hilotspa.backend.services;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.hilotspa.backend.entities.AuditLog;
import com.hilotspa.backend.entities.Branch;
import com.hilotspa.backend.entities.Massage;
import com.hilotspa.backend.entities.ProtocolRule;
import com.hilotspa.backend.entities.ServiceProtocol;
import com.hilotspa.backend.model.HealthDtos.Check;
import com.hilotspa.backend.model.HealthDtos.Health;
import com.hilotspa.backend.model.HealthDtos.State;
import com.hilotspa.backend.repository.AuditLogRepository;
import com.hilotspa.backend.repository.BranchRepository;
import com.hilotspa.backend.repository.MassageRepository;
import com.hilotspa.backend.repository.RoomRepository;
import com.hilotspa.backend.repository.ServiceProtocolRepository;
import com.hilotspa.backend.repository.TherapistRepository;

/**
 * Every check here names a failure this project has actually had.
 *
 * Nothing is inferred from "the process is running" — Spring being up says
 * almost nothing about whether the spa can take a booking. What matters is
 * whether a therapist and a room exist, whether n8n answers, whether the last
 * assistant call succeeded or quietly fell back, and whether the clinical rules
 * a client is judged against were written by anybody.
 */
@Service
public class HealthServiceImpl implements HealthService {

    @Autowired private BranchRepository branchRepository;
    @Autowired private TherapistRepository therapistRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private MassageRepository massageRepository;
    @Autowired private ServiceProtocolRepository serviceProtocolRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    @Value("${hilotspa.n8n.url:http://localhost:5678}") private String n8nUrl;
    @Value("${hilotspa.n8n.auth-secret:}")              private String n8nSecret;
    @Value("${hilotspa.node.id:local-dev}")             private String nodeId;
    @Value("${hilotspa.booking.timezone:Asia/Manila}")  private String timezone;

    /** A health check must never be the slow thing. Two seconds or it failed. */
    private static final int PROBE_TIMEOUT_MS = 2000;

    /** Older than this and "the assistant works" is not something we know. */
    private static final int ASSISTANT_STALE_HOURS = 24;

    @Override
    public Health check() {
        List<Check> checks = new ArrayList<>();
        checks.add(database());
        checks.add(resources());
        checks.add(n8n());
        checks.add(assistant());
        checks.add(protocol());
        checks.add(prices());
        checks.add(webhookAuth());

        State worst = checks.stream()
                .map(Check::state)
                .max(Comparator.comparingInt(State::ordinal))
                .orElse(State.OK);

        String summary = worst == State.OK
                ? "Everything needed to take a booking is working."
                : checks.stream()
                        .filter(c -> c.state() != State.OK)
                        .map(Check::name)
                        .reduce((a, b) -> a + ", " + b)
                        .map(names -> "Needs attention: " + names)
                        .orElse("");

        return new Health(worst, LocalDateTime.now(ZoneId.of(timezone)),
                nodeId, timezone, summary, checks);
    }

    // ------------------------------------------------------------- checks

    private Check database() {
        try {
            long branches = branchRepository.count();
            return branches == 0
                    ? new Check("Database", State.DOWN,
                        "Connected, but no branches exist. Nothing can be booked anywhere.")
                    : new Check("Database", State.OK, branches + " branch(es) on file.");
        } catch (Exception e) {
            return new Check("Database", State.DOWN, "Cannot reach PostgreSQL: " + e.getMessage());
        }
    }

    /**
     * A booking needs a therapist AND a room, both free. A branch missing either
     * offers no times at all and says nothing about why — this is the check that
     * explains an empty calendar.
     */
    private Check resources() {
        try {
            List<String> broken = new ArrayList<>();
            for (Branch b : branchRepository.findAll()) {
                int t = therapistRepository.findByBranchIdAndActiveTrue(b.getId()).size();
                int r = roomRepository.findByBranchIdAndActiveTrue(b.getId()).size();
                if (t == 0 || r == 0) {
                    broken.add(b.getName() + " (" + t + " therapists, " + r + " rooms)");
                }
            }
            return broken.isEmpty()
                    ? new Check("Therapists and rooms", State.OK,
                        "Every branch has at least one of each.")
                    : new Check("Therapists and rooms", State.DEGRADED,
                        "No times can be offered at: " + String.join("; ", broken));
        } catch (Exception e) {
            return new Check("Therapists and rooms", State.DOWN, e.getMessage());
        }
    }

    /**
     * Reachable is not the same as ready.
     *
     * A published workflow answers; an imported-but-unpublished one returns 404
     * and n8n itself is perfectly healthy. That distinction is exactly what cost
     * a morning when a second developer cloned the repo, so this check reports
     * the process separately from the workflows (see assistant(), below).
     */
    private Check n8n() {
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofMillis(PROBE_TIMEOUT_MS));
            factory.setReadTimeout(Duration.ofMillis(PROBE_TIMEOUT_MS));

            RestClient.builder().requestFactory(factory).build()
                    .get().uri(n8nUrl + "/healthz").retrieve().toBodilessEntity();

            return new Check("n8n", State.OK, "Reachable at " + n8nUrl
                    + ". Whether the workflows are PUBLISHED is a separate question - "
                    + "see the assistant check.");
        } catch (Exception e) {
            return new Check("n8n", State.DEGRADED,
                    "Not reachable at " + n8nUrl + ". The assistant will fall back to the "
                    + "protocol table, and clients will still be served. " + e.getMessage());
        }
    }

    private static final Pattern STATUS = Pattern.compile("\"status\":\"([^\"]*)\"");

    /**
     * The one that matters most.
     *
     * A FALLBACK reply looks completely normal to a client, so the only way to
     * know the model half is alive is to read what the last calls recorded.
     * Every assistant call writes its own status to audit_log; this reads it back.
     */
    private Check assistant() {
        try {
            List<AuditLog> calls = auditLogRepository.findAll().stream()
                    .filter(a -> "ASSISTANT_RECOMMEND".equals(a.getAction()))
                    .sorted(Comparator.comparing(AuditLog::getOccurredAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(10)
                    .toList();

            if (calls.isEmpty()) {
                return new Check("Assistant", State.DEGRADED,
                        "No assistant call has ever been recorded, so nothing here is known to "
                        + "work. Submit one assessment to find out.");
            }

            AuditLog latest = calls.get(0);
            long hours = latest.getOccurredAt() == null ? 0
                    : ChronoUnit.HOURS.between(latest.getOccurredAt(),
                        LocalDateTime.now(ZoneId.of(timezone)));

            long ok = calls.stream().filter(a -> {
                Matcher m = STATUS.matcher(a.getDetails() == null ? "" : a.getDetails());
                return m.find() && "OK".equalsIgnoreCase(m.group(1));
            }).count();

            if (ok == 0) {
                return new Check("Assistant", State.DEGRADED,
                        "The last " + calls.size() + " calls ALL fell back to the protocol table. "
                        + "Clients are still being served, but the model is not answering - check "
                        + "that both n8n workflows are published and the Vertex credential is valid.");
            }
            if (hours > ASSISTANT_STALE_HOURS) {
                return new Check("Assistant", State.DEGRADED,
                        "Last call was " + hours + " hours ago. Working then; unknown now.");
            }
            return new Check("Assistant", State.OK,
                    ok + " of the last " + calls.size() + " calls reached the model.");
        } catch (Exception e) {
            return new Check("Assistant", State.DEGRADED, e.getMessage());
        }
    }

    /** §D3. A filter with nothing in it excludes nothing, and says so. */
    private Check protocol() {
        try {
            List<ServiceProtocol> rules = serviceProtocolRepository.findAll();
            long contra = rules.stream()
                    .filter(p -> p.getRule() == ProtocolRule.CONTRAINDICATED).count();
            long unsigned = rules.stream()
                    .filter(p -> p.getAuthoredBy() == null
                            || p.getAuthoredBy().isBlank()
                            || p.getAuthoredBy().toUpperCase().contains("AWAITING"))
                    .count();

            if (contra == 0) {
                return new Check("Service protocol", State.DEGRADED,
                        "No contraindications are on file, so the safety filter currently excludes "
                        + "nothing. The mechanism works; the clinical rules have not been written "
                        + "yet (task 4.13).");
            }
            if (unsigned > 0) {
                return new Check("Service protocol", State.DEGRADED,
                        unsigned + " of " + rules.size() + " rules have no practitioner's name "
                        + "against them.");
            }
            return new Check("Service protocol", State.OK,
                    rules.size() + " rules, all signed, " + contra + " contraindications enforced.");
        } catch (Exception e) {
            return new Check("Service protocol", State.DOWN, e.getMessage());
        }
    }

    /** The assistant quotes price to clients. Zero is not a price. */
    private Check prices() {
        try {
            List<Massage> onSale = massageRepository.findAll().stream()
                    .filter(Massage::isOnSale).toList();
            long unpriced = onSale.stream()
                    .filter(m -> m.getPrice() == null || m.getPrice().signum() <= 0).count();

            if (onSale.isEmpty()) {
                return new Check("Service menu", State.DOWN,
                        "No treatments are on sale. The assistant has nothing it may offer.");
            }
            return unpriced == 0
                    ? new Check("Service menu", State.OK, onSale.size() + " treatments, all priced.")
                    : new Check("Service menu", State.DEGRADED,
                        unpriced + " of " + onSale.size() + " treatments have no price. The "
                        + "assistant will tell clients the price is not on file.");
        } catch (Exception e) {
            return new Check("Service menu", State.DOWN, e.getMessage());
        }
    }

    private Check webhookAuth() {
        return n8nSecret == null || n8nSecret.isBlank()
                ? new Check("Webhook authentication", State.DEGRADED,
                    "N8N_WEBHOOK_SECRET is not set, so anything that can reach " + n8nUrl
                    + " can drive the assistant. Fine on a closed dev laptop; not where a real "
                    + "client record exists (task 2.17).")
                : new Check("Webhook authentication", State.OK,
                    "Both webhooks require a shared secret.");
    }
}
