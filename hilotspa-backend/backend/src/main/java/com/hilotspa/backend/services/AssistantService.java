package com.hilotspa.backend.services;

import java.util.UUID;

import java.util.List;

import com.hilotspa.backend.model.AssistantDtos.CatalogueEntry;
import com.hilotspa.backend.model.AssistantDtos.ChatResponse;
import com.hilotspa.backend.model.AssistantDtos.TranscribeRequest;
import com.hilotspa.backend.model.AssistantDtos.TranscribeResponse;
import com.hilotspa.backend.model.AssistantDtos.ConfirmRequest;
import com.hilotspa.backend.model.BookingDtos.Openings;
import com.hilotspa.backend.model.AssistantDtos.RecommendResponse;

public interface AssistantService {

    /**
     * Rank the services this client may safely be offered.
     *
     * Authorises the caller against the form, filters the catalogue through the
     * spa-authored ServiceProtocol table, asks n8n to rank what is left, and
     * validates the answer again on the way back.
     */
    RecommendResponse recommend(UUID formId);

    /**
     * The whole service menu, annotated for this client.
     *
     * Pass null for formId to get the plain menu with nothing judged - a browser
     * who has not filled in an assessment yet.
     */
    List<CatalogueEntry> catalogue(UUID formId);

    /**
     * One turn of conversation about a completed assessment.
     *
     * The agent may only discuss services that survived the ServiceProtocol
     * filter, for the same structural reason the recommendation path works:
     * nothing else is ever put in front of it.
     */
    ChatResponse chat(UUID formId, String message, UUID focusServiceId, String language);

    /**
     * Turn a recording into text. Writes nothing, books nothing.
     *
     * Exists because the browser's own SpeechRecognition sends audio to the
     * browser vendor's service, which is a second route to Google that the
     * project's data-handling argument never covered. This path keeps the audio
     * inside the same Google Cloud project, region and terms as the assistant
     * itself - and it gives a microphone to the browsers that have no
     * SpeechRecognition at all.
     */
    TranscribeResponse transcribe(UUID formId, TranscribeRequest req);

    /**
     * Book a time the client tapped.
     *
     * Deliberately not routed through the model. A confirmation that depends on
     * a language model parsing "yes please" the same way it parses "I confirm",
     * and on it copying a long opaque id back without a typo, is a booking
     * staked on two things that will eventually fail. The slotId is revalidated
     * against freshly computed availability and written by the same transaction
     * the conversational path uses.
     */
    ChatResponse confirm(UUID formId, ConfirmRequest request);

    /**
     * Who and where is free at the time this slotId names.
     *
     * Takes the slotId rather than a service and a formatted date, because the
     * slotId already carries both and confirm() already parses it exactly this
     * way. Two endpoints deriving the same instant from two different wire
     * formats is a disagreement waiting to happen - and a date on a query
     * string is a parsing question nobody needs to answer twice.
     */
    Openings openings(UUID formId, String slotId);
}
