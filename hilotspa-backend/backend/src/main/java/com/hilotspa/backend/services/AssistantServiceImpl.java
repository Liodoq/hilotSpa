package com.hilotspa.backend.services;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;

import com.hilotspa.backend.config.CurrentUser;
import com.hilotspa.backend.entities.AuditLog;
import com.hilotspa.backend.entities.ComplaintType;
import com.hilotspa.backend.entities.Forms;
import com.hilotspa.backend.entities.Massage;
import com.hilotspa.backend.entities.PatientIntake;
import com.hilotspa.backend.entities.ProtocolRule;
import com.hilotspa.backend.entities.SafetyFlag;
import com.hilotspa.backend.entities.Role;
import com.hilotspa.backend.entities.ServiceProtocol;
import com.hilotspa.backend.model.AssistantDtos.AllowedService;
import com.hilotspa.backend.model.AssistantDtos.CatalogueEntry;
import com.hilotspa.backend.model.AssistantDtos.ChatResponse;
import com.hilotspa.backend.model.AssistantDtos.ChatSlot;
import com.hilotspa.backend.model.AssistantDtos.ChatToN8n;
import com.hilotspa.backend.model.AssistantDtos.ConfirmRequest;
import com.hilotspa.backend.model.BookingDtos.Openings;
import com.hilotspa.backend.model.AssistantDtos.N8nChatResponse;
import com.hilotspa.backend.model.AssistantDtos.N8nRecommendation;
import com.hilotspa.backend.model.AssistantDtos.N8nResponse;
import com.hilotspa.backend.model.AssistantDtos.PainPointView;
import com.hilotspa.backend.model.AssistantDtos.RecommendRequest;
import com.hilotspa.backend.model.AssistantDtos.RecommendResponse;
import com.hilotspa.backend.model.AssistantDtos.Recommendation;
import com.hilotspa.backend.repository.AuditLogRepository;
import com.hilotspa.backend.repository.FormsRepository;
import com.hilotspa.backend.repository.MassageRepository;
import com.hilotspa.backend.model.BookingDtos.BookRequest;
import com.hilotspa.backend.model.BookingDtos.Slot;
import com.hilotspa.backend.repository.ServiceProtocolRepository;

/**
 * The bridge between the assessment and the assistant.
 *
 * THE SAFETY ARGUMENT, in one place:
 *
 *   1. Java computes allowedServices from the spa-authored, signed
 *      ServiceProtocol table. Anything CONTRAINDICATED for this client is gone
 *      before the model is called.
 *   2. n8n drops any serviceId Spring did not send.
 *   3. This class drops them AGAIN on the way back.
 *
 * Steps 2 and 3 are not redundant. n8n's check protects the client on the happy
 * path; this one protects against a compromised or misconfigured n8n, which at a
 * branch is a machine nobody is watching. It is also the check a panel can read,
 * because it lives in the code rather than in a workflow's JSON.
 *
 * The model therefore cannot recommend a contraindicated service - not because
 * the prompt asks it not to, but because the id has nowhere to come from.
 */
@Service
public class AssistantServiceImpl implements AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantServiceImpl.class);

    private static final int MAX_RECOMMENDATIONS = 3;

    @Autowired private FormsRepository formsRepository;
    @Autowired private MassageRepository massageRepository;
    @Autowired private ServiceProtocolRepository serviceProtocolRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private BookingService bookingService;

    @Value("${hilotspa.n8n.url:http://localhost:5678}")
    private String n8nUrl;

    @Value("${hilotspa.n8n.timeout-ms:25000}")
    private int timeoutMs;

    /** Connecting is either instant or n8n is down; only READING is slow. */
    @Value("${hilotspa.n8n.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    /** Task 2.17 - shared secret sent on every webhook call. Blank = no header. */
    @Value("${hilotspa.n8n.auth-header:X-HilotSpa-Key}")
    private String n8nAuthHeader;

    @Value("${hilotspa.n8n.auth-secret:}")
    private String n8nSecret;

    @Value("${hilotspa.node.id:local-dev}")
    private String nodeId;

    @Value("${hilotspa.booking.timezone:Asia/Manila}")
    private String timezone;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    /**
     * Whether a fallback may say WHY it fell back, in the response the browser
     * reads.
     *
     * This exists because the profile check below never once fired. Nothing in
     * compose.yaml or application.properties sets spring.profiles.active - the
     * dev profile is a TEST-time thing here (@ActiveProfiles on the suites, and
     * DevDataSeeder) and has never been on in the running container. So `debug`
     * has been null on every response since it was written, and three separate
     * debugging sessions were spent asking for a field that could not exist.
     * A diagnostic nobody can switch on is not a diagnostic.
     */
    @Value("${hilotspa.assistant.expose-debug:false}")
    private boolean exposeDebug;

    /** Slots offered per service. Enough choice to be useful, short enough to
     *  keep the prompt readable and cheap. */
    /**
     * How many open times per service the assistant is allowed to see.
     *
     * Raised from 8 when it became clear the old number was not the real
     * problem - the ORDER was. See spread() below.
     */
    /** The booking window the agent may talk about. Capped again server-side by
     *  hilotspa.booking.max-days. */
    private static final int SLOT_DAYS = 7;

    /** How many times from any one day. Two - a morning and an afternoon - so
     *  no single day eats the whole budget and no day is represented by 9:00 AM
     *  alone. */
    private static final int SLOTS_PER_DAY = 2;

    /**
     * How many open times per service the agent may see: enough for every open
     * day in the window to offer both. Deliberately derived rather than typed,
     * so raising the window cannot silently starve the later days again.
     */
    private static final int SLOTS_PER_SERVICE = SLOT_DAYS * SLOTS_PER_DAY;

    /**
     * The service the conversation has narrowed to gets its WHOLE calendar.
     *
     * Sampling two times a day is right for browsing and wrong for answering
     * "is there 3 PM on Thursday?" - the agent would say no, meaning only that
     * 3 PM was not in the sample. Once a client has chosen a treatment, that
     * one service is sent at full resolution, so a question about a specific
     * time has a truthful answer and a bookable id behind it. Only one service
     * is ever expanded, so the request does not grow with the menu.
     */
    private static final int SLOTS_FOR_FOCUS = 140;

    @Override
    public RecommendResponse recommend(UUID formId) {
        long started = System.currentTimeMillis();

        Forms form = formsRepository.findById(formId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Form not found"));
        assertCanAccess(form);

        int catalogueSize = (int) massageRepository.count();
        List<AllowedService> allowed = allowedServicesFor(form);
        int excluded = catalogueSize - allowed.size();

        // Not an error, and not something to ask a model about. If the protocol
        // table rules out everything the spa offers, that IS the clinical answer.
        if (allowed.isEmpty()) {
            RecommendResponse referral = new RecommendResponse(
                    formId, "REFER", List.of(), "none", 0,
                    0, excluded,
                    System.currentTimeMillis() - started,
                    "Nothing in our service list is advised for what you have described. "
                    + "Please speak with the practitioner before booking.");
            audit(form, referral, null);
            return referral;
        }

        RecommendResponse response;
        String failure = null;
        try {
            N8nResponse raw = callN8n(buildRequest(form, allowed));
            response = validate(formId, raw, allowed, excluded,
                    System.currentTimeMillis() - started);
        } catch (Exception e) {
            // A hanging or broken assistant must never hold up a booking screen.
            // The protocol table answers instead, exactly as the n8n fallback
            // node would have.
            failure = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("Assistant recommend failed for form {} calling {}: {}",
                    form.getId(), n8nUrl, e.toString(), e);
            response = new RecommendResponse(
                    formId, "FALLBACK", protocolRanking(allowed), "unavailable", 0,
                    allowed.size(), excluded,
                    System.currentTimeMillis() - started,
                    "Suggested from the spa's own service protocol.");
        }

        audit(form, response, failure);
        return response;
    }

    // ---------------------------------------------------------------- filter

    /**
     * How the protocol table judges each service for one client.
     *
     * banned  - CONTRAINDICATED for at least one of this client's conditions
     * notes   - the practitioner's rationale, keyed by service id
     *
     * Judged against the chief complaint AND every complaint marked on a pain
     * point: a client can mark a knee while complaining about a shoulder.
     */
    private record Verdict(Set<UUID> banned, Map<UUID, String> reason,
                           Map<UUID, String> indicated) {
    }

    private Verdict judge(Forms form) {
        Set<ComplaintType> conditions = new HashSet<>();
        if (form != null) {
            if (form.getMainComplaint() != null) {
                conditions.add(form.getMainComplaint());
            }
            for (PatientIntake p : form.getPainPoints()) {
                if (p.getComplaintType() != null) {
                    conditions.add(p.getComplaintType());
                }
            }
        }

        Set<UUID> banned = new HashSet<>();
        Map<UUID, String> reason = new HashMap<>();
        Map<UUID, String> indicated = new HashMap<>();

        for (ComplaintType condition : conditions) {
            for (ServiceProtocol p : serviceProtocolRepository.findByCondition(condition)) {
                UUID serviceId = p.getService().getId();
                if (p.getRule() == ProtocolRule.CONTRAINDICATED) {
                    banned.add(serviceId);
                    reason.putIfAbsent(serviceId, rationaleOr(p, condition,
                            "not advised with " + condition.getDisplayName()));
                } else if (p.getRule() == ProtocolRule.INDICATED) {
                    indicated.putIfAbsent(serviceId, rationaleOr(p, condition,
                            "listed for " + condition.getDisplayName()));
                }
            }
        }
        // A contraindication always beats an indication. If one condition says
        // yes and another says no, the answer is no.
        indicated.keySet().removeAll(banned);
        return new Verdict(banned, reason, indicated);
    }

    private static String rationaleOr(ServiceProtocol p, ComplaintType condition, String fallback) {
        String r = p.getRationale();
        return (r == null || r.isBlank()) ? fallback : r;
    }

    /** Everything the spa offers, minus everything contraindicated for this client. */
    private List<AllowedService> allowedServicesFor(Forms form) {
        Verdict v = judge(form);
        List<AllowedService> allowed = new ArrayList<>();
        for (Massage m : massageRepository.findAll()) {
            // Withdrawn from the menu, so it is not on offer at all. This is a
            // commercial decision, not a clinical one, which is why it happens
            // before judge() rather than inside it.
            if (!m.isOnSale() || v.banned().contains(m.getId())) {
                continue;
            }
            String note = v.indicated().get(m.getId());
            allowed.add(new AllowedService(
                    m.getId(), m.getName(), m.getDurationMinute(), m.getPrice(),
                    note != null ? "INDICATED" : "NEUTRAL",
                    note != null ? note : ""));
        }
        return allowed;
    }

    @Override
    public List<CatalogueEntry> catalogue(UUID formId) {
        Forms form = null;
        if (formId != null) {
            form = formsRepository.findById(formId).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Form not found"));
            assertCanAccess(form);
        }
        Verdict v = judge(form);

        List<CatalogueEntry> out = new ArrayList<>();
        for (Massage m : massageRepository.findAll()) {
            if (!m.isOnSale()) {
                continue;   // not sold any more; nothing to show a client
            }
            boolean banned = v.banned().contains(m.getId());
            String note = banned ? v.reason().get(m.getId()) : v.indicated().get(m.getId());
            out.add(new CatalogueEntry(
                    m.getId(), m.getName(), m.getDurationMinute(), m.getPrice(),
                    !banned,
                    banned ? "CONTRAINDICATED" : (v.indicated().containsKey(m.getId())
                            ? "INDICATED" : "NEUTRAL"),
                    note == null ? "" : note,
                    m.getImageName()));
        }
        return out;
    }

    // ------------------------------------------------------------------ chat

    /**
     * The client's chosen language, or null to let the agent mirror them.
     *
     * Anything else is dropped. The value is interpolated into a system prompt,
     * so an unrecognised string is not a harmless typo - it is a sentence
     * someone else chose appearing in our instructions to the model.
     */
    private static String normaliseLanguage(String language) {
        if (language == null) {
            return null;
        }
        String v = language.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("fil") || v.startsWith("tl")) {
            return "fil";
        }
        if (v.startsWith("en")) {
            return "en";
        }
        return null;
    }


    @Override
    public ChatResponse chat(UUID formId, String message, UUID focusServiceId,
                             String language) {
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is required");
        }

        Forms form = formsRepository.findById(formId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Form not found"));
        assertCanAccess(form);

        List<AllowedService> allowed = allowedServicesFor(form);
        if (allowed.isEmpty()) {
            return new ChatResponse(
                    "Nothing in our service list is advised for what you have described. "
                    + "Please speak with the practitioner before booking.",
                    "REFER", null, null, List.of());
        }

        Map<String, ChatSlot> slotsById = bookableSlots(formId, allowed, focusServiceId);

        RecommendRequest base = buildRequest(form, allowed);
        ChatToN8n body = new ChatToN8n(
                // Keyed per assessment, so two clients never share a memory
                // window - the n8n Simple Memory node reads this.
                "form-" + form.getId(),
                clip(message.trim(), 1000),
                LocalDateTime.now(ZoneId.of(timezone)).toString(),
                timezone,
                // Normalised here rather than trusted: this string is pasted
                // into a prompt, and only two values may ever reach it.
                normaliseLanguage(language),
                base.intent(),
                base.chiefComplaint(),
                base.chiefComplaintDuration(),
                base.painPoints(),
                base.flags(),
                allowed,
                List.copyOf(slotsById.values()),
                nameOf(allowed, focusServiceId),
                myBookingSummaries(),
                List.of());

        N8nChatResponse raw;
        try {
            raw = postToN8n("/webhook/hilotspa/chat", body, N8nChatResponse.class);
        } catch (Exception e) {
            // Same rule as the recommendation path: a broken assistant degrades
            // the wording, never the service. But SAY WHY - a fallback that
            // cannot distinguish "not attempted" from "failed" hides exactly the
            // failures worth seeing (the B60 lesson, repeated here by me).
            log.error("Assistant chat failed for form {} calling {}{}: {}",
                    form.getId(), n8nUrl, "/webhook/hilotspa/chat", e.toString(), e);
            return new ChatResponse(frontDesk(), "FALLBACK", null,
                    devOnly("POST " + n8nUrl + "/webhook/hilotspa/chat -> " + e),
                    List.copyOf(slotsById.values()));
        }

        String reply = raw == null ? null : raw.reply();
        if (reply == null || reply.isBlank()) {
            log.warn("Assistant chat: n8n returned no reply for form {} (raw={})",
                    form.getId(), raw);
            return new ChatResponse(frontDesk(), "FALLBACK", null,
                    devOnly("n8n answered but reply was empty: " + raw),
                    List.copyOf(slotsById.values()));
        }
        reply = clip(reply.trim(), 1200);

        if (raw != null && raw.book()) {
            return attemptBooking(form, raw, slotsById, reply);
        }

        // B112. The guard below attemptBooking() only ever examined replies that
        // ADMITTED to booking - it fires on raw.book(). A reply that announces a
        // booking in prose while book is false went straight to the client
        // untouched, and did exactly that: "booked na po kayo for Basic Massage,
        // Thursday 12:30 PM" with nothing written and no confirmation card.
        //
        // The model is not on the writing path and never has been, so it cannot
        // know whether a booking happened. Any past-tense claim from it is
        // therefore unfounded by construction - which is what makes this
        // checkable in Java rather than a matter of prompt wording.
        if (claimsBooking(reply)) {
            log.warn("Assistant claimed a booking that did not happen, form {}: {}",
                    form.getId(), reply);
            return new ChatResponse(
                    "I have not booked anything yet - I do not do the booking myself. "
                    + "Pick one of the times below and you can choose your therapist "
                    + "and room next.",
                    "CHOOSE", null,
                    devOnly("reply claimed a booking with book=false; discarded: " + reply),
                    List.copyOf(slotsById.values()));
        }

        // B115. The model settled a time in PROSE and left book=false, so no
        // slotId reached Spring, so the who-and-where card never opened and no
        // visit was ever written. The client had agreed to everything and the
        // conversation simply stopped: "Na-hold na po namin ang time na Fri 4
        // Sep, 9:00 AM" with nothing held anywhere.
        //
        // Why the model does it: rules 7 and 7b spend six lines telling it that
        // it does NOT book and must never say it has. book=true reads like the
        // thing it was just forbidden to do, so it stops setting the flag as
        // well as stopping saying the word. The prompt now separates the two
        // (rule 7d), but a prompt is a request and this is a guarantee.
        //
        // Recovery, not rejection: the reply names the day and the hour, and
        // Spring knows every slot it offered, so the sentence can be matched
        // back to the slot it describes. Nothing is written by doing so - it
        // returns CHOOSE, which opens the therapist-and-room card the client
        // still has to confirm. A wrong match is therefore visible and
        // cancellable rather than silent.
        //
        // B120. Which treatment the sentence is about has to be settled BEFORE
        // the slot lookup, and it has to be settled the same way here as it is
        // for the panel. It was not: this passed raw.serviceId() while the panel
        // used a resolved value, so the two disagreed on the turn it mattered.
        //
        // "Opo, na-hold na po ang Therapeutic Massage for you on Sunday,
        // September 6, at 3:30 PM" carries no MINUTES, so serviceNamedIn cannot
        // identify it either - it requires name and duration together, because
        // Signature Massage exists at both 60 and 90. With no service to filter
        // by, the lookup scanned every service's slots; the two-a-day sample the
        // other treatments get happens to be 9:00 AM and 3:30 PM, so three
        // different services all answered to "Sunday 3:30 PM", the ambiguity
        // guard did its job, and the hold was refused for being too well
        // described.
        //
        // focusServiceId is the honest fallback: it is the treatment the
        // conversation has already narrowed to and the one whose calendar is on
        // screen while the client is reading this sentence.
        UUID about = raw.serviceId() != null ? raw.serviceId() : serviceNamedIn(reply, allowed);
        UUID within = about != null ? about : focusServiceId;

        if (raw.slotId() == null && claimsHold(reply)) {
            ChatSlot settled = slotNamedIn(reply, within, slotsById);
            if (settled != null) {
                log.warn("Assistant settled a time with book=false, form {}; recovered "
                        + "slot {} from the reply text", form.getId(), settled.slotId());
                return new ChatResponse(reply, "CHOOSE", null,
                        devOnly("book=false but the reply settled a time; recovered "
                                + settled.slotId()),
                        List.copyOf(slotsById.values()), settled.slotId(),
                        settled.serviceId());
            }
            log.warn("Assistant claimed a hold that matches no slot Spring sent, form {}: {}",
                    form.getId(), reply);
        }

        // `about` is resolved above, before the hold check, so the panel and the
        // hold recovery can never disagree about which treatment is being
        // discussed. The model is ASKED to report serviceId and Spring works it
        // out anyway when it does not: the first version of this trusted the
        // field alone and the panel never moved once, because serviceId had
        // always been in the reply schema and nothing in the prompt had ever
        // told the model to set it. Reading a field nobody was asked to fill is
        // the same mistake as B115 in the other direction.
        return new ChatResponse(reply, raw.status() == null ? "OK" : raw.status(), null, null,
                List.copyOf(slotsById.values()), null, about);
    }

    /**
     * Does this sentence tell the client they are already booked?
     *
     * Deliberately narrow. "I-book ko po kayo" is a promise and legitimate;
     * "fully booked" is availability and legitimate. Only completed claims about
     * THIS client's own visit match, in the two languages the assistant speaks.
     *
     * Prompt rule 7b already forbids all of this. Rules are guidance to a model;
     * this is the wall behind them, and the wall is the part the paper's
     * integrity claim can actually rest on (paper-deltas D2).
     */
    private static final java.util.regex.Pattern BOOKING_CLAIM =
            java.util.regex.Pattern.compile(
                "\\bbooked na\\b"                                  // "booked na po kayo"
              + "|\\bnaka-?book\\b|\\bna-?book(ed)?\\b"          // naka-book / na-book
              + "|\\bna-?reserve\\b|\\bna-?schedule\\b"
              + "|\\byou('re| are) booked\\b"
              + "|\\b(i have|i've) booked\\b"
              + "|\\byour (appointment|visit|booking) (is|has been) "
              + "(confirmed|booked|set|scheduled)\\b"
              + "|\\byou('re| are) all set\\b",
                java.util.regex.Pattern.CASE_INSENSITIVE);

    private static boolean claimsBooking(String reply) {
        return reply != null && BOOKING_CLAIM.matcher(reply).find();
    }

    /**
     * Does this reply announce that a time has been HELD?
     *
     * Deliberately narrower than BOOKING_CLAIM. That one catches a lie - a
     * claim that a visit exists - and discards the sentence. This one catches
     * a sentence that is TRUE in intent but arrived without the slotId that
     * makes it true, so the sentence is kept and the missing half is recovered.
     */
    private static final java.util.regex.Pattern HOLD_CLAIM =
            java.util.regex.Pattern.compile(
                // Filipino marks the completed aspect several ways, and the
                // first version of this only knew ONE of them: it matched
                // "na-hold" and missed "naka-hold", which is the form the model
                // actually used. The conversation then dead-ended pointing at a
                // screen that never changed. A verb-form list is not a place to
                // guess - "na", "naka", "ni" and "ini" are all ordinary here.
                "\\b(na|naka|ni|ini)-?hold\\b"
              + "|\\bhold(ed)? na\\b|\\bnakahold\\b"
              + "|\\bay held na\\b|\\bheld na po\\b|\\bis held\\b"
              + "|\\b(i have|i've|we have|we've) held\\b"
              + "|\\byour time is (held|set|reserved)\\b"
              + "|\\b(na|naka|ni|ini)-?reserve\\b"
                // Rule 7b tells the model to follow a hold by saying the client
                // chooses their therapist and room next. That sentence is
                // therefore evidence a time was settled, whatever verb carried
                // it - and it is the half the model reproduces most reliably.
              + "|pagpili (ninyo |niyo )?ng therapist"
              + "|makakapili (na )?kayo ng therapist"
              + "|choose (your |a )?therapist and room",
                java.util.regex.Pattern.CASE_INSENSITIVE);

    private boolean claimsHold(String reply) {
        return reply != null && HOLD_CLAIM.matcher(reply).find();
    }

    /**
     * Which service is this reply about, worked out from the reply itself.
     *
     * Name alone is not enough: the spa sells "Signature Massage" at 60 and 90
     * minutes, which are two different services with one name, so a match on the
     * name would pick whichever came first and send the panel to the wrong
     * duration. Name AND minutes together are what identify it - which is also
     * exactly how the prompt requires the model to write it ("always say the
     * minutes with the name"), so this is reading back a format Spring imposed.
     *
     * Ambiguity returns null rather than a guess. The panel not moving is a
     * small failure; the panel moving to the wrong treatment while the client
     * reads about a different one is a much worse one.
     */
    private UUID serviceNamedIn(String reply, List<AllowedService> allowed) {
        if (reply == null || reply.isBlank()) {
            return null;
        }
        UUID found = null;
        for (AllowedService svc : allowed) {
            if (svc.name() == null || svc.durationMinutes() == null
                    || !reply.contains(svc.name())
                    || !reply.contains(String.valueOf(svc.durationMinutes()))) {
                continue;
            }
            if (found != null && !found.equals(svc.serviceId())) {
                return null;
            }
            found = svc.serviceId();
        }
        return found;
    }

    /**
     * Find the slot a reply is describing, by the day and hour it prints.
     *
     * Spring rendered both of those labels itself and handed them to the model
     * in AVAILABLE TIMES, so a reply that names a real slot names it in exactly
     * the words Spring chose. That is what makes matching on them safe rather
     * than clever: it is not parsing free text, it is recognising Spring's own
     * output coming back.
     *
     * Requires BOTH labels, and the service when the model reported one. A
     * reply mentioning only "9:00 AM" matches nothing - the same hour exists on
     * seven days.
     */
    private ChatSlot slotNamedIn(String reply, UUID serviceId,
                                 Map<String, ChatSlot> slotsById) {
        if (reply == null) {
            return null;
        }
        ChatSlot found = null;
        for (ChatSlot slot : slotsById.values()) {
            if (serviceId != null && !serviceId.equals(slot.serviceId())) {
                continue;
            }
            // B118. This used to be reply.contains(slot.dayLabel()). The day
            // label is rendered in English - "Sun 6 Sep" - and the assistant
            // mirrors the client's language, so a Filipino sentence that named
            // the day perfectly well ("sa Linggo, September 6, 3:30 PM") matched
            // nothing and the hold was refused. The client had agreed, Spring
            // had the slot in its hand, and the panel sat on OPEN TIMES.
            //
            // Matching a DATE rather than a STRING fixes it in both languages at
            // once, and keeps the guarantee that made the original narrow: the
            // day and the hour must BOTH be named, and two slots answering to
            // the same sentence still refuse.
            LocalDateTime when = slotStart(slot);
            if (when == null || !dayNamedIn(reply, when.toLocalDate())
                    || !timeNamedIn(reply, when)) {
                continue;
            }
            if (found != null) {
                // Two slots answer to the same sentence. Guessing between them
                // is how a client ends up holding an hour they never named.
                return null;
            }
            found = slot;
        }
        return found;
    }

    /** ChatSlot carries the start as an ISO string so the browser need not parse a slotId. */
    private static LocalDateTime slotStart(ChatSlot slot) {
        try {
            return slot.start() == null ? null : LocalDateTime.parse(slot.start());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Filipino weekday names, indexed by DayOfWeek.getValue() - 1 (1 = Monday).
     *
     * Hard-coded rather than taken from a Locale: the JDK's tl/fil data is not
     * guaranteed present in a slim container image, and a missing locale would
     * degrade to English silently - which is the exact failure being fixed.
     */
    private static final String[] TL_DAYS = {
            "lunes", "martes", "miyerkules", "huwebes", "biyernes", "sabado", "linggo"
    };

    private static final DateTimeFormatter D_MMMM =
            DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH);
    private static final DateTimeFormatter MMMM_D =
            DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH);
    private static final DateTimeFormatter D_MMM =
            DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MMM_D =
            DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter BARE_TIME =
            DateTimeFormatter.ofPattern("h:mm", Locale.ENGLISH);

    /**
     * Did this sentence name this day, in either language the assistant speaks?
     *
     * A weekday name alone is enough because the slot list is one week long, so
     * "Linggo" picks out exactly one date. Anything looser is still safe: two
     * slots matching the same sentence makes slotNamedIn refuse rather than
     * guess, and the caller only ever opens a card the client must still
     * confirm - nothing is written on the strength of a match here.
     */
    private boolean dayNamedIn(String reply, LocalDate day) {
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        List<String> tokens = new ArrayList<>();
        tokens.add(day.format(DAY_FMT));          // "Sun 6 Sep" - Spring's own chip
        tokens.add(day.format(MMMM_D));           // "September 6"
        tokens.add(day.format(D_MMMM));           // "6 September"
        tokens.add(day.format(MMM_D));            // "Sep 6"
        tokens.add(day.format(D_MMM));            // "6 Sep"
        tokens.add(day.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        tokens.add(TL_DAYS[day.getDayOfWeek().getValue() - 1]);
        if (day.equals(today)) {
            tokens.add("today");
            tokens.add("ngayon");
        }
        if (day.equals(today.plusDays(1))) {
            tokens.add("tomorrow");
            tokens.add("bukas");
        }
        return namesAnyOf(reply, tokens);
    }

    /**
     * "3:30 PM", or a bare "3:30" for a sentence that ends in ng hapon.
     *
     * The bare form is bounded on both sides so it cannot be found inside
     * 13:30 - a substring match here would hold an hour nobody said.
     */
    private boolean timeNamedIn(String reply, LocalDateTime start) {
        return namesAnyOf(reply, List.of(start.format(TIME_FMT), start.format(BARE_TIME)));
    }

    /** Whole-token search: contains() would find "Sun" inside "susunod". */
    private static boolean namesAnyOf(String haystack, List<String> needles) {
        for (String n : needles) {
            if (n == null || n.isBlank()) {
                continue;
            }
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "(?<![\\p{L}\\p{N}])" + java.util.regex.Pattern.quote(n)
                            + "(?![\\p{L}\\p{N}])",
                    java.util.regex.Pattern.CASE_INSENSITIVE
                            | java.util.regex.Pattern.UNICODE_CASE);
            if (p.matcher(haystack).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The agent said the client agreed. Spring checks that is even possible.
     *
     * A slotId the agent invented is dropped AND its sentence is discarded with
     * it - the text described a time that does not exist, so shipping it without
     * the booking would still misinform the client.
     */
    private ChatResponse attemptBooking(Forms form, N8nChatResponse raw,
                                        Map<String, ChatSlot> slotsById, String reply) {
        ChatSlot slot = raw.slotId() == null ? null : slotsById.get(raw.slotId());
        if (slot == null) {
            // The model meant to book and named a time we cannot honour. Say so
            // plainly and leave the times on screen - the client can tap one and
            // never has to guess at a phrasing that works.
            log.warn("Chat booking rejected for form {}: slotId '{}' is not in the "
                    + "{} slots Spring sent", form.getId(), raw.slotId(), slotsById.size());
            return new ChatResponse(
                    "Sorry - I could not hold that exact time. Tap one of the times below "
                    + "and I will book it straight away.", "REJECTED", null,
                    devOnly("slotId not in the list Spring sent: " + raw.slotId()),
                    List.copyOf(slotsById.values()));
        }
        // The prose path no longer writes. It has settled a TIME; who and where
        // is a separate question, and a client who says "I confirm" has not
        // answered it. Hand the slot back and let them choose - the write still
        // happens through confirm(), which is the one path that books.
        //
        // The model's own sentence is DISCARDED here, deliberately. It was
        // written believing this path books, so it says "na-book na po" - and
        // nothing has been booked. A reply that announces a booking above a
        // picker asking who should give it is the assistant lying, which is the
        // one thing this system cannot afford to do. The client-facing sentence
        // comes from the UI instead, which is also the only place that knows
        // what language the screen is in.
        return new ChatResponse("", "CHOOSE", null,
                devOnly("model said: " + reply),
                List.copyOf(slotsById.values()), slot.slotId(), slot.serviceId());
    }

    /**
     * The tap path.
     *
     * A client agreeing in words depends on a model parsing "yes please" the
     * same way it parses "I confirm", and on it copying a long opaque id back
     * without a typo. Neither is something to stake a booking on. Tapping a time
     * comes straight here, is validated against the same recomputed slot map,
     * and is written by the same transaction - the model is not on this path at
     * all.
     */
    @Override
    public Openings openings(UUID formId, String slotId) {
        if (slotId == null || slotId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slotId is required");
        }
        Forms form = formsRepository.findById(formId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Form not found"));
        assertCanAccess(form);

        UUID serviceId = serviceIdIn(slotId);
        LocalDateTime start;
        try {
            start = LocalDateTime.parse(slotId.substring(slotId.indexOf('@') + 1));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unreadable slotId");
        }
        if (serviceId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unreadable slotId");
        }
        return bookingService.openings(formId, serviceId, start);
    }

    @Override
    public ChatResponse confirm(UUID formId, ConfirmRequest request) {
        if (request == null || request.slotId() == null || request.slotId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slotId is required");
        }

        Forms form = formsRepository.findById(formId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Form not found"));
        assertCanAccess(form);

        // Recomputed, not trusted from the client. A slotId that was open when
        // the page rendered may have gone since.
        //
        // The tapped service is expanded to its FULL calendar. Without this a
        // time the client can see and tap - one that came from the focused list
        // - could be missing from the sample recomputed here, and the tap would
        // fail with "that time has just gone" while the time was still free.
        Map<String, ChatSlot> slotsById = bookableSlots(
                formId, allowedServicesFor(form), serviceIdIn(request.slotId()));
        ChatSlot slot = slotsById.get(request.slotId());
        if (slot == null) {
            return new ChatResponse(
                    "That time has just gone. Here are the times still open.",
                    "CONFLICT", null, devOnly("slotId no longer available: " + request.slotId()),
                    List.copyOf(slotsById.values()));
        }

        String said = "Confirmed by tapping " + slot.serviceName() + ", " + slot.label();
        return commit(form, slot, "Booked. " + slot.serviceName() + ", " + slot.label()
                + ". We will see you then.", said, slotsById,
                request.therapistId(), request.roomId());
    }

    /**
     * The single write path for both.
     *
     * `consent` is the exact text that authorised the booking - the client's own
     * words on the conversational path, the tap on the other. It is stored on the
     * audit row, because "the client agreed" has to be evidence rather than an
     * assertion.
     */
    private ChatResponse commit(Forms form, ChatSlot slot, String reply, String consent,
                                Map<String, ChatSlot> slotsById,
                                /** The client's pick, or null for "any available". */
                                UUID wantTherapist, UUID wantRoom) {
        LocalDateTime start;
        try {
            start = LocalDateTime.parse(slot.slotId().substring(slot.slotId().indexOf('@') + 1));
        } catch (Exception e) {
            return new ChatResponse(frontDesk(), "FALLBACK", null,
                    devOnly("could not parse the slot start from " + slot.slotId()),
                    List.copyOf(slotsById.values()));
        }

        try {
            // The idempotency key includes the pick: a client who is told
            // "Ana has just been booked", chooses Ben and taps again is making a
            // DIFFERENT request, and must not be handed the first one's answer.
            Object booking = bookingService.book(new BookRequest(
                    form.getId(), slot.serviceId(), start,
                    wantTherapist, wantRoom,
                    "chat-" + form.getId() + "-" + slot.slotId()
                            + (wantTherapist == null ? "" : "-t" + wantTherapist)
                            + (wantRoom == null ? "" : "-r" + wantRoom),
                    consent));
            // The times just changed - hand back the fresh set, not the stale one.
            // Still focused on the treatment just booked: a client who books one
            // visit often wants a second, and that is the calendar they are
            // looking at.
            return new ChatResponse(reply, "BOOKED", booking, null,
                    List.copyOf(bookableSlots(form.getId(), allowedServicesFor(form),
                            slot.serviceId()).values()));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                // Spring's own words when it has them: "the slot went" and "you
                // are already booked then" are different problems with different
                // fixes, and only one of them is solved by tapping another time.
                String reason = e.getReason();
                return new ChatResponse(
                        reason != null && !reason.isBlank()
                                ? reason + " The other open times are below."
                                : "That time was taken just before I could hold it. "
                                  + "Tap another below and I will book it.",
                        "CONFLICT", null, null,
                        List.copyOf(bookableSlots(form.getId(), allowedServicesFor(form),
                                slot.serviceId()).values()));
            }
            return new ChatResponse(frontDesk(), "FALLBACK", null, devOnly(e.toString()),
                    List.copyOf(slotsById.values()));
        } catch (Exception e) {
            log.error("Booking from chat failed for form {} slot {}: {}",
                    form.getId(), slot.slotId(), e.toString(), e);
            return new ChatResponse(frontDesk(), "FALLBACK", null, devOnly(e.toString()),
                    List.copyOf(slotsById.values()));
        }
    }

    /**
     * Every bookable time the agent is permitted to name.
     *
     * Exactly the same idea as allowedServices: it cannot offer a slot that is
     * not here, because there is nowhere for one to come from. The same map is
     * what the confirm endpoint validates a tapped slotId against, so the tap
     * path and the conversational path are checked identically.
     */
    private Map<String, ChatSlot> bookableSlots(UUID formId, List<AllowedService> allowed,
                                               UUID focusServiceId) {
        Map<String, ChatSlot> slotsById = new LinkedHashMap<>();
        for (AllowedService svc : allowed) {
            boolean focused = svc.serviceId().equals(focusServiceId);
            try {
                List<Slot> open = bookingService
                        .availability(formId, svc.serviceId(), null, SLOT_DAYS).slots();
                for (Slot slot : focused ? cap(open, SLOTS_FOR_FOCUS) : spread(open)) {
                    String key = svc.serviceId() + "@" + slot.slotId();
                    slotsById.put(key, new ChatSlot(key, svc.serviceId(), svc.name(),
                            slot.label(), svc.durationMinutes(), svc.price(),
                            slot.start() == null ? null : slot.start().toString(),
                            dayLabel(slot.start()), timeLabel(slot.start())));
                }
            } catch (Exception e) {
                // One service failing to price a calendar must not silence the
                // whole conversation - but it must not vanish either.
                log.warn("Could not compute availability for service {}: {}",
                        svc.serviceId(), e.toString());
            }
        }
        return slotsById;
    }

    /**
     * Choose WHICH of a service's open times the assistant is shown.
     *
     * The old rule was "the first eight", and availability comes back in
     * chronological order - so in practice that meant "the first eight of the
     * earliest open day": 9:00 through 12:30, and nothing else all week. A
     * client asking about any later date was told the service was not available
     * then. That was false: the times existed, they had simply been cut off
     * before the agent could see them. B89.
     *
     * The rule now is coverage first. Every open day in the window gets its
     * earliest time before any day gets a second one, and the second pick comes
     * from the middle of that day so mornings and afternoons are both on offer.
     * The agent can therefore answer truthfully about any date in the window,
     * which is what "Analyzing available time, dates, and resources" in the
     * Scope actually promises.
     */
    /**
     * The service a slotId belongs to.
     *
     * The key is "serviceId@startIsoTime", built in bookableSlots. Reading it
     * back is what lets the tap path expand exactly the service that was
     * tapped, without the client being trusted to tell us which one it was -
     * an id that does not parse simply expands nothing and the tap falls
     * through to the normal "that time has gone" answer.
     */
    /** The focused service's display name, for the prompt. Null when the client
     *  has not chosen one - which is also the honest answer to "which calendar
     *  is complete?" at that point: none of them. */
    private static String nameOf(List<AllowedService> allowed, UUID serviceId) {
        if (serviceId == null) {
            return null;
        }
        for (AllowedService a : allowed) {
            if (a.serviceId().equals(serviceId)) {
                return a.name() + ", " + a.durationMinutes() + " minutes";
            }
        }
        return null;
    }

    private static UUID serviceIdIn(String slotId) {
        int at = slotId == null ? -1 : slotId.indexOf('@');
        if (at <= 0) {
            return null;
        }
        try {
            return UUID.fromString(slotId.substring(0, at));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The whole calendar, with a ceiling so a pathological configuration - a
     *  five-minute slot step, say - cannot produce an unbounded request. */
    private static List<Slot> cap(List<Slot> available, int max) {
        return available.size() <= max ? available : available.subList(0, max);
    }

    private static List<Slot> spread(List<Slot> available) {
        Map<LocalDate, List<Slot>> byDay = new LinkedHashMap<>();
        for (Slot s : available) {
            if (s.start() == null) {
                continue;
            }
            byDay.computeIfAbsent(s.start().toLocalDate(), d -> new ArrayList<>()).add(s);
        }

        List<Slot> out = new ArrayList<>();
        // Pass 1 - one time on every open day, nearest first.
        for (List<Slot> day : byDay.values()) {
            if (out.size() >= SLOTS_PER_SERVICE) {
                break;
            }
            out.add(day.get(0));
        }
        // Pass 2 - a middle-of-the-day time, nearest days first, while there is
        // budget left. Without this every offer is 9:00 AM.
        if (SLOTS_PER_DAY > 1) {
            for (List<Slot> day : byDay.values()) {
                if (out.size() >= SLOTS_PER_SERVICE) {
                    break;
                }
                if (day.size() < 2) {
                    continue;
                }
                Slot mid = day.get(day.size() / 2);
                if (!out.contains(mid)) {
                    out.add(mid);
                }
            }
        }
        out.sort(Comparator.comparing(Slot::start));
        return out;
    }

    private static final DateTimeFormatter DAY_FMT =
            DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    /**
     * Rendered here rather than in the browser or the model.
     *
     * The client's device clock may be wrong or in another zone, and a model
     * asked to say "tomorrow" will eventually say it about the wrong day. The
     * spa's own timezone is the only authority for what "today" means.
     */
    private String dayLabel(LocalDateTime start) {
        if (start == null) {
            return "";
        }
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        LocalDate day = start.toLocalDate();
        if (day.equals(today)) {
            return "Today";
        }
        if (day.equals(today.plusDays(1))) {
            return "Tomorrow";
        }
        return day.format(DAY_FMT);
    }

    private String timeLabel(LocalDateTime start) {
        return start == null ? "" : start.format(TIME_FMT);
    }


    /**
      * One line per booking the CALLER already has.
      *
      * Availability tells the agent a slot is taken; this tells it which ones
      * are the client's own. Those are two different questions, and only the
      * second may name a person - so other clients' bookings never appear here.
      */
    private List<String> myBookingSummaries() {
        try {
            // B122. The therapist and the room belong in this line. Without
            // them the agent could see that the client HAD a visit but not who
            // it was with, so when a booked client asked "can I assign a
            // different therapist?" it had nothing true to say and invented
            // something: it told them a picker was open on their screen. Naming
            // Lito Fernandez and Treatment Room 2 costs nothing here - this list
            // is the caller's OWN bookings and never anyone else's.
            return bookingService.mine().stream()
                    .map(b -> b.label() + " - " + b.serviceName()
                            + " (" + b.durationMinutes() + " min, " + b.status() + ")"
                            + (b.therapist() == null || b.therapist().isBlank()
                                    ? "" : " with " + b.therapist())
                            + (b.room() == null || b.room().isBlank()
                                    ? "" : " in " + b.room()))
                    .limit(10)
                    .toList();
        } catch (Exception e) {
            log.warn("Could not read the caller's bookings for chat context: {}", e.toString());
            return List.of();
        }
    }

    /**
     * Diagnostics reach the browser under the dev profile only.
     *
     * A fallback that cannot say why it fell back is the bug that keeps costing
     * us afternoons; asking for container logs mid-test costs more. At the spa
     * this returns null and the client sees only the polite line.
     */
    private String devOnly(String detail) {
        return exposeDebug || "dev".equalsIgnoreCase(activeProfile) ? detail : null;
    }

    private static String frontDesk() {
        return "I am having trouble answering right now. The front desk can help you "
             + "with any question about our services.";
    }

    // ----------------------------------------------------------------- build

    private RecommendRequest buildRequest(Forms form, List<AllowedService> allowed) {
        List<PainPointView> points = new ArrayList<>();
        for (PatientIntake p : form.getPainPoints()) {
            points.add(new PainPointView(
                    // displayName, not name(). The prompt is an artefact a
                    // panellist reads, and FROZEN_SHOULDER is not a word.
                    p.getAnatomicalRegion() == null ? null : p.getAnatomicalRegion().getDisplayName(),
                    p.getSide() == null ? null : p.getSide().name(),
                    p.getBodyView() == null ? null : p.getBodyView().name(),
                    p.getPainScoreBefore(),
                    p.getComplaintType() == null ? null : p.getComplaintType().getDisplayName()));
        }

        String complaint = form.getMainComplaint() != null
                ? form.getMainComplaint().getDisplayName()
                : form.getMainComplaintOther();

        return new RecommendRequest(
                form.getId(),
                form.getIntent() == null ? null : form.getIntent().name(),
                complaint,
                form.getMainComplaintDuration(),
                // H9 - was hardcoded null while the preference sat unparsed in a
                // free-text field. The client said Firm; the assistant can say it back.
                form.getPressurePreference() == null
                        ? null : form.getPressurePreference().getDisplayName(),
                points,
                flagsFrom(form),
                allowed);
    }

    /**
     * What the client ticked on the safety checklist - H9 / B44, now real.
     *
     * Until today these lived in the free-text remarks column, so the assistant
     * was told only `noted_on_record: true`: a parsed claim pulled out of prose
     * is a guess, and guessing about pregnancy is not something this system
     * should do. They are enum-backed rows now, so the flag the client ticked is
     * the flag the model is told about, in the client's own wording.
     *
     * These do NOT filter the service list. That is deliberate - the filter runs
     * off the practitioner's signed ServiceProtocol table, and no rule keys on a
     * safety flag until she has authored one (task 4.13). The model may take
     * them into account when it explains a choice; it may not invent a rule.
     */
    private Map<String, Boolean> flagsFrom(Forms form) {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (SafetyFlag f : form.getSafetyFlags()) {
            flags.put(f.name().toLowerCase(), true);
        }
        String remarks = form.getRemarks();
        if (remarks != null && !remarks.isBlank()) {
            flags.put("free_text_note_on_record", true);
        }
        if (Boolean.TRUE.equals(form.getHadIllness())) {
            flags.put("past_illness_reported", true);
        }
        if (Boolean.TRUE.equals(form.getHasTherapy())) {
            flags.put("previous_therapy", true);
        }
        return flags;
    }

    // ------------------------------------------------------------------ call

    private N8nResponse callN8n(RecommendRequest body) {
        return postToN8n("/webhook/hilotspa/recommend", body, N8nResponse.class);
    }

    /**
     * The only place this application talks to n8n.
     *
     * Two timeouts, because they answer different questions. A connect that
     * takes longer than a moment means n8n is not there - fail fast and say so.
     * A read that takes fifteen seconds means the model is thinking, and cutting
     * it off there is how the assistant came to answer "I am having trouble"
     * every time the conversation got interesting enough to be slow.
     */
    private <T> T postToN8n(String path, Object body, Class<T> type) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        RestClient.RequestBodySpec request = RestClient.builder()
                .requestFactory(factory)
                .build()
                .post()
                .uri(n8nUrl + path)
                .contentType(MediaType.APPLICATION_JSON);

        // Task 2.17. Without this, anything that can reach port 5678 can drive
        // the assistant and spend the project's Vertex credits. The header is
        // omitted when unset so a fresh clone still runs - and the application
        // WARNS at startup rather than failing quietly open.
        if (n8nSecret != null && !n8nSecret.isBlank()) {
            request = request.header(n8nAuthHeader, n8nSecret);
        }

        return request.body(body).retrieve().body(type);
    }

    /**
     * Says out loud that the assistant is reachable by anyone on the network.
     *
     * A security gap you have to remember is a security gap. Same pattern as the
     * seeder's warnings about missing prices and unsigned rules: the system
     * states its own readiness at boot instead of leaving it to be discovered.
     */
    @PostConstruct
    void warnIfWebhooksAreOpen() {
        if (n8nSecret == null || n8nSecret.isBlank()) {
            log.warn("n8n webhooks are UNAUTHENTICATED - N8N_WEBHOOK_SECRET is not set. "
                    + "Anything that can reach {} can drive the assistant. Acceptable on a dev "
                    + "laptop only; set it before any real client record exists (task 2.17).",
                    n8nUrl);
        }
    }

    // -------------------------------------------------------------- validate

    /** The second guard. Only ids Spring sent may reach the client. */
    private RecommendResponse validate(UUID formId, N8nResponse raw,
                                       List<AllowedService> allowed, int excluded,
                                       long latencyMs) {

        Map<UUID, AllowedService> byId = new LinkedHashMap<>();
        for (AllowedService s : allowed) {
            byId.put(s.serviceId(), s);
        }

        List<Recommendation> kept = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        int rejected = 0;

        List<N8nRecommendation> picks = raw == null || raw.recommendations() == null
                ? List.of() : raw.recommendations();

        for (N8nRecommendation r : picks) {
            UUID id = r.serviceId();
            if (id == null || !byId.containsKey(id) || !seen.add(id)) {
                rejected++;
                continue;
            }
            kept.add(new Recommendation(id, byId.get(id).name(), kept.size() + 1,
                    trim(r.reason()),
                    byId.get(id).durationMinutes(), byId.get(id).price()));
            if (kept.size() == MAX_RECOMMENDATIONS) {
                break;
            }
        }

        if (kept.isEmpty()) {
            return new RecommendResponse(formId, "FALLBACK", protocolRanking(allowed),
                    raw == null ? "unavailable" : nullToEmpty(raw.modelUsed()),
                    rejected, allowed.size(), excluded, latencyMs,
                    "Suggested from the spa's own service protocol.");
        }

        return new RecommendResponse(formId, "OK", kept,
                nullToEmpty(raw.modelUsed()), rejected,
                allowed.size(), excluded, latencyMs, null);
    }

    /** Deterministic answer: INDICATED first, then the rest, capped at three. */
    private List<Recommendation> protocolRanking(List<AllowedService> allowed) {
        List<Recommendation> out = new ArrayList<>();
        for (AllowedService s : allowed) {
            if ("INDICATED".equals(s.rule())) {
                out.add(new Recommendation(s.serviceId(), s.name(), out.size() + 1,
                        s.rationale().isBlank()
                                ? "Listed for this assessment in the spa's own service protocol."
                                : s.rationale(),
                        s.durationMinutes(), s.price()));
            }
            if (out.size() == MAX_RECOMMENDATIONS) {
                return out;
            }
        }
        for (AllowedService s : allowed) {
            if (!"INDICATED".equals(s.rule())) {
                out.add(new Recommendation(s.serviceId(), s.name(), out.size() + 1,
                        "Available for this visit.",
                        s.durationMinutes(), s.price()));
            }
            if (out.size() == MAX_RECOMMENDATIONS) {
                break;
            }
        }
        return out;
    }

    // ----------------------------------------------------------------- audit

    /**
     * One row per call. rejectedCount is the number of services the model named
     * that Java had not approved - a MEASURED hallucination rate rather than an
     * asserted one. At the end of the study a single query over audit_log gives
     * the reliability numbers Chapter IV promises.
     */
    private void audit(Forms form, RecommendResponse r, String failure) {
        try {
            // Built by hand rather than with Jackson. This project is on Jackson 3
            // (tools.jackson.*), the details column is six flat scalars, and an
            // audit row must never be the thing that breaks a request.
            StringBuilder d = new StringBuilder(256);
            d.append('{');
            field(d, "status", r.status()).append(',');
            field(d, "modelUsed", r.modelUsed()).append(',');
            d.append("\"rejectedCount\":").append(r.rejectedCount()).append(',');
            d.append("\"returned\":").append(r.recommendations().size()).append(',');
            d.append("\"allowed\":").append(r.allowedCount()).append(',');
            d.append("\"excluded\":").append(r.excludedCount()).append(',');
            d.append("\"latencyMs\":").append(r.latencyMs());
            if (failure != null) {
                d.append(',');
                field(d, "failure", failure);
            }
            d.append('}');

            AuditLog log = new AuditLog();
            log.setAction("ASSISTANT_RECOMMEND");
            log.setEntityType("Forms");
            log.setEntityId(form.getId());
            log.setBranch(form.getBranch());
            log.setActor(form.getUser());
            log.setOriginNodeId(nodeId);
            log.setDetails(clip(d.toString(), 1000));
            auditLogRepository.save(log);
        } catch (Exception ignored) {
            // Never let the measurement break the feature it measures.
        }
    }

    private static StringBuilder field(StringBuilder d, String key, String value) {
        return d.append('"').append(key).append("\":\"").append(escape(value)).append('"');
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ").replace("\t", " ");
    }

    // ------------------------------------------------------------------ misc

    private void assertCanAccess(Forms form) {
        if (CurrentUser.isAdmin()) {
            return;
        }
        if (CurrentUser.hasRole(Role.STAFF)) {
            UUID own = CurrentUser.branchId().orElse(null);
            if (own != null && form.getBranch() != null && own.equals(form.getBranch().getId())) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your branch");
        }
        UUID me = CurrentUser.id().orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Not authenticated"));
        if (form.getUser() == null || !me.equals(form.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your assessment");
        }
    }

    private static String trim(String s) {
        return s == null ? "" : clip(s.trim(), 300);
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
