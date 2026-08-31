package com.hilotspa.backend.services;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    @Value("${hilotspa.n8n.timeout-ms:5000}")
    private int timeoutMs;

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

    @Override
    public ChatResponse chat(UUID formId, String message, UUID focusServiceId) {
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
        return new ChatResponse(reply, raw.status() == null ? "OK" : raw.status(), null, null,
                List.copyOf(slotsById.values()));
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
        return commit(form, slot, reply, raw.reply(), slotsById);
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
                + ". We will see you then.", said, slotsById);
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
                                Map<String, ChatSlot> slotsById) {
        LocalDateTime start;
        try {
            start = LocalDateTime.parse(slot.slotId().substring(slot.slotId().indexOf('@') + 1));
        } catch (Exception e) {
            return new ChatResponse(frontDesk(), "FALLBACK", null,
                    devOnly("could not parse the slot start from " + slot.slotId()),
                    List.copyOf(slotsById.values()));
        }

        try {
            Object booking = bookingService.book(new BookRequest(
                    form.getId(), slot.serviceId(), start,
                    "chat-" + form.getId() + "-" + slot.slotId(),
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
            return bookingService.mine().stream()
                    .map(b -> b.label() + " - " + b.serviceName()
                            + " (" + b.durationMinutes() + " min, " + b.status() + ")")
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
        return "dev".equalsIgnoreCase(activeProfile) ? detail : null;
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
     * The timeout is the point: a hanging model call must never hold a booking
     * screen open. Whatever goes wrong here, the caller falls back.
     */
    private <T> T postToN8n(String path, Object body, Class<T> type) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
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
