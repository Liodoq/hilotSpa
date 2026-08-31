package com.hilotspa.backend.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The contract between Spring and n8n. See backend/n8n/README.md.
 *
 * Spring sends everything the assistant is allowed to know in one request.
 * n8n never connects to the database, so this class IS the assistant's entire
 * view of the world.
 */
public final class AssistantDtos {

    private AssistantDtos() {
    }

    /**
     * A service the client may be offered.
     *
     * The list is computed in Java from the spa-authored ServiceProtocol table
     * BEFORE the model is called, so anything CONTRAINDICATED for this client is
     * already gone. The model ranks and explains; it never decides what is safe.
     */
    public record AllowedService(
            UUID serviceId,
            String name,
            Integer durationMinutes,
            BigDecimal price,
            String rule,
            String rationale) {
    }

    /**
     * One row of the service menu, judged against a specific client.
     *
     * Excluded services are RETURNED, not filtered out, so C8 can show them
     * greyed with the reason. The client sees that a decision was made and why -
     * which is the difference between enforcing the spa's own protocol and
     * quietly giving medical advice (paper-deltas D3).
     */
    public record CatalogueEntry(
            UUID serviceId,
            String name,
            Integer durationMinutes,
            BigDecimal price,
            boolean suitable,
            String rule,
            String reason,
            /** Photo filename in public/services/, or null. Never the id - see
             *  Massage.imageName for why. */
            String imageName) {
    }

    public record PainPointView(
            String anatomicalRegion,
            String side,
            String bodyView,
            Integer painScoreBefore,
            String complaintType) {
    }

    /** POST body sent to the n8n webhook. */
    public record RecommendRequest(
            UUID formId,
            String intent,
            String chiefComplaint,
            String chiefComplaintDuration,
            String pressurePreference,
            List<PainPointView> painPoints,
            Map<String, Boolean> flags,
            List<AllowedService> allowedServices) {
    }

    /**
     * One ranked service coming back.
     *
     * durationMinutes and price are carried even though the model does not set
     * them: the spa sells the same treatment at two lengths, so "Signature
     * Massage" twice in a list is unreadable without them. They come from the
     * database row, never from the model.
     */
    public record Recommendation(
            UUID serviceId,
            String name,
            Integer rank,
            String reason,
            Integer durationMinutes,
            BigDecimal price) {
    }

    /**
     * What Angular posts to /api/v1/assistant/chat/{formId}.
     *
     * `focusServiceId` is the treatment the conversation has narrowed to, if
     * any. It buys DEPTH: that service's whole calendar is sent, so "is there
     * 3 PM on Thursday?" has a truthful answer, while every other service stays
     * sampled so the request does not grow with the length of the menu. Null
     * before the client has chosen, which is when breadth is what they need.
     */
    public record ChatRequest(String message, UUID focusServiceId) {
    }

    /** Sent to the n8n chat workflow. Same principle as the recommendation
     *  request: the agent's entire view of the world is in this object. */
    public record ChatToN8n(
            String sessionKey,
            String message,
            /** The model has no clock. Without these, "tomorrow" is a guess. */
            String now,
            String timezone,
            String intent,
            String chiefComplaint,
            String chiefComplaintDuration,
            List<PainPointView> painPoints,
            Map<String, Boolean> flags,
            List<AllowedService> allowedServices,
            List<ChatSlot> availableSlots,
            /**
             * The one treatment whose calendar in availableSlots is COMPLETE,
             * by name, or null. Everything else is a two-a-day sample. The
             * agent needs to know the difference: "not in my list" means "taken"
             * only for this one, and means "I did not look" for the rest.
             */
            String completeFor,
            /** The client's OWN bookings only. Never anyone else's - the agent
             *  can see that a slot is taken without learning who has it. */
            List<String> myBookings,
            List<Recommendation> recommendations) {
    }

    /**
     * A bookable time, as the agent sees it. It may only ever name one of these.
     *
     * The same list is handed to the client so it can be rendered as tappable
     * times. That matters for more than tidiness: a tapped slot is booked by id,
     * so the confirmation never depends on a language model reproducing a long
     * opaque string correctly.
     */
    public record ChatSlot(
            String slotId,
            UUID serviceId,
            String serviceName,
            String label,
            Integer durationMinutes,
            java.math.BigDecimal price,
            /** ISO start, so the UI can group by day without parsing slotId. */
            String start,
            /** "Today", "Tomorrow", or "Thu 27 Aug" - rendered in Java on purpose. */
            String dayLabel,
            /** "9:00 AM" - the time on its own, for a chip. */
            String timeLabel) {
    }

    /**
     * The agent's structured reply.
     *
     * It decides INTENT - did the client agree to this time? That is a language
     * question. Spring decides LEGALITY and performs the write, because only a
     * transaction can settle who gets a slot two people asked for.
     */
    public record N8nChatResponse(
            String sessionKey,
            String reply,
            String status,
            boolean book,
            String slotId,
            UUID serviceId,
            String generatedAt) {
    }

    /**
     * What Angular receives back.
     *
     * `debug` is populated ONLY under the dev profile. A fallback that cannot
     * say why it fell back is the bug that keeps costing us afternoons, and
     * asking for container logs mid-test costs more.
     */
    public record ChatResponse(
            String reply,
            String status,
            Object booking,
            String debug,
            /**
             * Every time the agent was allowed to name, handed to the client so
             * it can be tapped. Tapping books by id through
             * POST /assistant/confirm/{formId} - a path the model is not on at
             * all, which is why "yes please" not parsing can no longer strand
             * a client mid-booking.
             */
            List<ChatSlot> slots) {
    }

    /** Body of POST /assistant/confirm/{formId} - the tap path. */
    public record ConfirmRequest(String slotId) {
    }

    /** Raw shape returned by the n8n workflow. */
    public record N8nResponse(
            UUID formId,
            String status,
            List<N8nRecommendation> recommendations,
            String modelUsed,
            Integer rejectedCount,
            String parseError,
            String generatedAt) {
    }

    public record N8nRecommendation(UUID serviceId, Integer rank, String reason) {
    }

    /**
     * What the Angular client receives.
     *
     * status is one of:
     *   OK       - the model answered and at least one id survived validation
     *   FALLBACK - the protocol table answered; the model was absent or rejected
     *   REFER    - nothing the spa offers is indicated; see the practitioner
     */
    public record RecommendResponse(
            UUID formId,
            String status,
            List<Recommendation> recommendations,
            String modelUsed,
            int rejectedCount,
            /** How many services survived the ServiceProtocol filter. */
            int allowedCount,
            /** How many were removed as CONTRAINDICATED before the model was asked.
             *  Shown to the client verbatim - it is the safety claim, made visible. */
            int excludedCount,
            long latencyMs,
            String note) {
    }
}
