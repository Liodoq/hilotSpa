package com.hilotspa.backend.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Operational readiness — the answer to "is it actually working?"
 *
 * This system is designed to degrade quietly. When Vertex or n8n dies the client
 * still receives a sensible service list from the protocol table, which is right
 * for the client and dangerous for whoever is running the spa: the assistant can
 * be dead for a day and nothing on screen says so.
 *
 * These checks exist to make silent degradation loud. Each one names a real
 * failure this project has actually had.
 */
public final class HealthDtos {

    private HealthDtos() {
    }

    /** OK = working · DEGRADED = running with something broken · DOWN = not usable. */
    public enum State { OK, DEGRADED, DOWN }

    public record Check(
            String name,
            State state,
            /** One sentence a person can act on, not a stack trace. */
            String detail) {
    }

    public record Health(
            State state,
            LocalDateTime checkedAt,
            String nodeId,
            String timezone,
            /** Worst state wins; the summary says which checks caused it. */
            String summary,
            List<Check> checks) {
    }
}
