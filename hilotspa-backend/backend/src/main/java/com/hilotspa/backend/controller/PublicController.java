package com.hilotspa.backend.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hilotspa.backend.entities.Massage;
import com.hilotspa.backend.entities.Therapist;
import com.hilotspa.backend.model.PublicDtos.PublicService;
import com.hilotspa.backend.model.PublicDtos.PublicTherapist;
import com.hilotspa.backend.model.PublicDtos.PublicSpa;
import com.hilotspa.backend.repository.MassageRepository;
import com.hilotspa.backend.repository.TherapistRepository;

/**
 * The only unauthenticated endpoint in the system, and the front door of the
 * public site.
 *
 * A spa that cannot show its own menu without an account is not a spa website,
 * it is an intranet - and a panel opening the URL would meet a login form
 * rather than the system. So visitors get the treatment list and the spa's own
 * details, and nothing else.
 *
 * What is deliberately NOT here: no protocol rules, no contraindication
 * verdicts, no availability, no therapists, no rooms, no counts of anything.
 * Every one of those is either a judgement about a named client or an
 * operational fact about the branch, and neither is a stranger's business.
 * Withdrawn treatments are filtered out, so the public menu can never advertise
 * something the spa has stopped selling.
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicController {

    @Autowired
    private MassageRepository massageRepository;

    @Autowired
    private TherapistRepository therapistRepository;

    @Value("${hilotspa.spa.name:Knead Wellness Spa}")      private String spaName;
    @Value("${hilotspa.spa.tagline:}")                     private String tagline;
    @Value("${hilotspa.spa.address:}")                     private String address;
    @Value("${hilotspa.spa.phone:}")                       private String phone;
    @Value("${hilotspa.spa.hours:}")                       private String hours;
    @Value("${hilotspa.spa.facebook:}")                    private String facebook;
    @Value("${hilotspa.spa.maps-url:}")                    private String mapsUrl;

    /** The landing page in one call: the spa's details and its live menu. */
    @GetMapping("/spa")
    public ResponseEntity<PublicSpa> spa() {
        return ResponseEntity.ok(new PublicSpa(
                spaName, tagline, address, phone, hours, facebook, mapsUrl, menu(), team()));
    }

    /** The menu on its own, for the services page. */
    @GetMapping("/services")
    public ResponseEntity<List<PublicService>> services() {
        return ResponseEntity.ok(menu());
    }

    /**
     * Who works here - first names only.
     *
     * Published so a client can see that both women and men are on the team
     * before they are asked to state a preference. Deliberately carries no id:
     * an id would let a stranger ask about one named person's availability, and
     * a therapist's working pattern is not public information.
     *
     * Only active staff. Someone who has left the spa is not on the team, and
     * their status on any given day is nobody outside the branch's business.
     */
    private List<PublicTherapist> team() {
        List<PublicTherapist> out = new ArrayList<>();
        for (Therapist t : therapistRepository.findAll()) {
            if (!t.isActive()) {
                continue;
            }
            out.add(new PublicTherapist(
                    t.getFirstName(),
                    t.getSex() == null ? null : t.getSex().getDisplayName()));
        }
        out.sort((a, b) -> a.firstName().compareToIgnoreCase(b.firstName()));
        return out;
    }

    private List<PublicService> menu() {
        List<PublicService> out = new ArrayList<>();
        for (Massage m : massageRepository.findAll()) {
            if (!m.isOnSale()) {
                continue;   // withdrawn - never advertise it
            }
            out.add(new PublicService(
                    m.getId(), m.getName(), m.getDurationMinute(),
                    m.getPrice(), m.getImageName()));
        }
        out.sort((a, b) -> {
            int byName = a.name().compareToIgnoreCase(b.name());
            return byName != 0 ? byName
                    : Integer.compare(
                        a.durationMinutes() == null ? 0 : a.durationMinutes(),
                        b.durationMinutes() == null ? 0 : b.durationMinutes());
        });
        return out;
    }
}
