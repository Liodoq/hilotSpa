package com.hilotspa.backend.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.hilotspa.backend.model.AssistantDtos.CatalogueEntry;
import com.hilotspa.backend.model.AssistantDtos.ChatRequest;
import com.hilotspa.backend.model.AssistantDtos.ChatResponse;
import com.hilotspa.backend.model.AssistantDtos.TranscribeRequest;
import com.hilotspa.backend.model.AssistantDtos.TranscribeResponse;
import com.hilotspa.backend.model.AssistantDtos.ConfirmRequest;
import com.hilotspa.backend.model.BookingDtos.Openings;
import com.hilotspa.backend.model.AssistantDtos.RecommendResponse;
import com.hilotspa.backend.services.AssistantService;

/**
 * The only door to the assistant.
 *
 * Angular never calls n8n on port 5678 directly: n8n cannot validate a JWT, has
 * no notion of branches, and writes nothing to the audit log. Every request goes
 * through here so authorisation stays in exactly one place.
 *
 * No @CrossOrigin - the CorsConfigurationSource bean already covers it. Adding
 * one here would be the second source of truth that B31 is about.
 */
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    @Autowired
    private AssistantService assistantService;

    /** POST, not GET: it calls a paid model and writes an audit row. */
    @PostMapping("/recommend/{formId}")
    public ResponseEntity<RecommendResponse> recommend(@PathVariable UUID formId) {
        return ResponseEntity.ok(assistantService.recommend(formId));
    }

    /**
     * One turn of conversation. Angular posts here, never to n8n on 5678:
     * n8n cannot validate a JWT or scope a branch.
     */
    @PostMapping("/chat/{formId}")
    public ResponseEntity<ChatResponse> chat(@PathVariable UUID formId,
                                             @RequestBody ChatRequest body) {
        return ResponseEntity.ok(
                assistantService.chat(formId, body.message(), body.focusServiceId(),
                                      body.language()));
    }

    /**
     * Exchange a recording for its text. Nothing is booked and nothing is saved.
     *
     * Separate from /chat on purpose. The transcript goes back to the browser
     * and the client sends it themselves - so a misheard sentence is corrected
     * before it is acted on, and voice never becomes a second way to reach the
     * booking logic.
     */
    @PostMapping("/transcribe/{formId}")
    public ResponseEntity<TranscribeResponse> transcribe(@PathVariable UUID formId,
                                                         @RequestBody TranscribeRequest body) {
        return ResponseEntity.ok(assistantService.transcribe(formId, body));
    }

    /**
     * Book a time the client tapped.
     *
     * Separate from /chat on purpose: this path does not touch the model, so a
     * client can always complete a booking even when the assistant is having a
     * bad day with the word "yes".
     */
    /**
     * Who and where is free at the time a tapped slotId names.
     *
     * Same identifier the confirm call takes, so the two cannot disagree about
     * which instant the client is looking at.
     */
    @GetMapping("/openings/{formId}")
    public ResponseEntity<Openings> openings(@PathVariable UUID formId,
                                             @RequestParam String slotId) {
        return ResponseEntity.ok(assistantService.openings(formId, slotId));
    }

    @PostMapping("/confirm/{formId}")
    public ResponseEntity<ChatResponse> confirm(@PathVariable UUID formId,
                                                @RequestBody ConfirmRequest body) {
        return ResponseEntity.ok(assistantService.confirm(formId, body));
    }

    /**
     * The service menu, annotated for this client. Excluded services come back
     * WITH their reason rather than being silently removed, so C8 can show what
     * was ruled out and why.
     */
    @GetMapping("/catalogue")
    public ResponseEntity<List<CatalogueEntry>> catalogue(
            @RequestParam(required = false) UUID formId) {
        return ResponseEntity.ok(assistantService.catalogue(formId));
    }
}
