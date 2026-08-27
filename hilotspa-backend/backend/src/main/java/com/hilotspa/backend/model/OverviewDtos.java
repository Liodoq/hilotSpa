package com.hilotspa.backend.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A1 — the administrator's aggregate.
 *
 * Every number here is counted from the database at request time. Nothing is
 * stored, cached or estimated, so a panelist can put the same figure on screen
 * and in psql and see them agree (backend/verify.sql).
 */
public final class OverviewDtos {

    private OverviewDtos() {
    }

    public record NodeCard(
            UUID branchId,
            String branchName,
            String nodeId,
            /** True for the node serving this request. */
            boolean thisNode,
            int bookingsToday,
            int therapists,
            int therapistsAvailable,
            int rooms,
            int assessmentsThisWeek,
            LocalDateTime lastWrite) {
    }

    public record ComplaintCount(String label, long count, int pct) {
    }

    /**
     * The reliability figure, and how it was arrived at.
     *
     * `rejected` is the count the server itself threw away after the model had
     * already proposed it. That is the number that makes "the assistant never
     * offered a contraindicated service" a measurement rather than a claim —
     * and it is only countable because the third guard re-checks every reply.
     */
    public record AssistantStats(
            long calls,
            long ok,
            long failed,
            long rejectedSuggestions,
            long returnedSuggestions,
            /** Rejected as a percentage of everything the model proposed. */
            double rejectionRatePct,
            String note) {
    }

    /**
     * What is stopping the system being evaluated for real.
     *
     * Counted on the server like everything else here, so the administrator's
     * first screen states its own readiness rather than leaving it to be
     * discovered on the day.
     */
    public record Readiness(
            int protocolRules,
            int unsignedRules,
            int contraindications,
            int servicesOnSale,
            int servicesWithoutPrice) {
    }

    public record Overview(
            LocalDateTime generatedAt,
            int bookingsToday,
            int bookingsThisWeek,
            int assessmentsThisWeek,
            int assessmentsTotal,
            int nodesOnline,
            int nodesTotal,
            List<NodeCard> nodes,
            List<ComplaintCount> topComplaints,
            AssistantStats assistant,
            Readiness readiness) {
    }
}
