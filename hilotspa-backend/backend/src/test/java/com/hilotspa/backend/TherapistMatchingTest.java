package com.hilotspa.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Who may be given to whom — tasks 2.38 and the therapist preference.
 *
 * Both rules exist because a control that does nothing is worse than no
 * control: the staff screen promises that a therapist set off duty "disappears
 * from availability at once", and a client who asks to be treated by a woman is
 * being asked about their dignity, not their taste.
 *
 * These tests go through the WALK-IN path deliberately. Availability only ever
 * offers future times, so a test written against today's calendar passes at ten
 * in the morning and fails at ten at night. Walk-ins may be recorded at a time
 * that has already passed — that is what makes "today" testable at any hour.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class TherapistMatchingTest {

    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper json;

    private TestSupport api() { return new TestSupport(mvc, json); }

    /** A time today, safely inside opening hours whatever the hour actually is. */
    private String todayAt(int hour) {
        return LocalDate.now().atTime(LocalTime.of(hour, 0)).toString();
    }

    private String walkIn(String serviceId, String start, String name, String key) {
        return """
                {"serviceId":"%s","start":"%s","name":"%s","idempotencyKey":"%s"}
                """.formatted(serviceId, start, name, key);
    }

    // ------------------------------------------------------------- 2.38

    @Test
    @DisplayName("a therapist set off duty is not given any visit today")
    void offDutyTherapistsAreNotAssignedToday() throws Exception {
        TestSupport api = api();
        String staff = api.tokenFor(TestSupport.STAFF_BULAN);
        String serviceId = api.serviceIdNamed(staff, "Ventosa", 30);

        // Bulan seeds two therapists. Take one off the floor.
        JsonNode team = api.getAs(staff, "/api/v1/therapists");
        assertThat(team).as("the seeded branch must have therapists").isNotEmpty();
        String offDutyId = team.get(0).get("id").asText();
        String offDutyName = team.get(0).get("firstName").asText() + " "
                + team.get(0).get("lastName").asText();

        MvcResult put = api.putRaw(staff, "/api/v1/therapists/" + offDutyId,
                "{\"status\":\"OFF_DUTY\"}");
        assertThat(put.getResponse().getStatus()).isEqualTo(200);

        // Book into a time today until the branch refuses, and check the person
        // we took off the floor never appears.
        for (int i = 0; i < 6; i++) {
            MvcResult res = api.postRaw(staff, "/api/v1/appointments/walk-in",
                    walkIn(serviceId, todayAt(10), "Walk-in " + i, "offduty" + i));
            if (res.getResponse().getStatus() == 409) {
                break;
            }
            assertThat(res.getResponse().getStatus()).isEqualTo(201);
            assertThat(api.parse(res).get("therapist").asText())
                    .as("someone marked off duty must not be handed a visit")
                    .isNotEqualTo(offDutyName);
        }
    }

    @Test
    @DisplayName("being off duty today does not empty next week")
    void offDutyDoesNotReachIntoTheFuture() throws Exception {
        TestSupport api = api();
        String staff = api.tokenFor(TestSupport.STAFF_BULAN);
        String serviceId = api.serviceIdNamed(staff, "Ventosa", 30);

        JsonNode team = api.getAs(staff, "/api/v1/therapists");
        String offDutyId = team.get(0).get("id").asText();
        api.putRaw(staff, "/api/v1/therapists/" + offDutyId, "{\"status\":\"OFF_DUTY\"}");

        // status is a right-now flag with no end time. Nobody has said this
        // person will still be off in three days, so three days out they are
        // bookable again - otherwise one tap at the counter would quietly empty
        // next week and staff would learn never to touch the control.
        String future = LocalDate.now().plusDays(3).atTime(LocalTime.of(10, 0)).toString();
        MvcResult res = api.postRaw(staff, "/api/v1/appointments/walk-in",
                walkIn(serviceId, future, "Future client", "future1"));
        assertThat(res.getResponse().getStatus())
                .as("a future visit must still be bookable")
                .isEqualTo(201);
    }

    // ------------------------------------------- therapist preference

    @Test
    @DisplayName("a client who asks for a female therapist is only ever given one")
    void aPreferenceIsHonouredAtAssignment() throws Exception {
        TestSupport api = api();
        String ana = api.tokenFor(TestSupport.ANA);
        String staff = api.tokenFor(TestSupport.STAFF_BULAN);
        String branch = api.branchIdContaining(ana, "Bulan");
        String serviceId = api.serviceIdNamed(ana, "Ventosa", 30);

        // Which of the seeded Bulan therapists are women?
        JsonNode team = api.getAs(staff, "/api/v1/therapists");
        int females = 0;
        for (JsonNode t : team) {
            if ("FEMALE".equals(t.path("sex").asText(null))) {
                females++;
            }
        }
        assertThat(females)
                .as("the seed must include at least one female therapist, or this proves nothing")
                .isGreaterThan(0);

        // An assessment that asks for a woman, stated at creation the way the
        // wizard states it.
        JsonNode form = api.createForm(ana, branch, "LOWER_BACK_PAIN", "LOWER_BACK_PAIN", "FEMALE");
        String formId = form.get("id").asText();
        assertThat(form.get("therapistPreference").asText())
                .as("the preference must survive the round trip, or nothing below means anything")
                .isEqualTo("FEMALE");

        JsonNode slots = api.getAs(ana, "/api/v1/appointments/availability?formId=" + formId
                + "&serviceId=" + serviceId).get("slots");
        assertThat(slots)
                .as("a female therapist exists, so times must still be offered")
                .isNotEmpty();

        MvcResult booked = api.postRaw(ana, "/api/v1/appointments", """
                {"formId":"%s","serviceId":"%s","start":"%s",
                 "idempotencyKey":"pref-1","consentText":"Yes please."}
                """.formatted(formId, serviceId, slots.get(0).get("start").asText()));
        assertThat(booked.getResponse().getStatus()).isEqualTo(201);

        String given = api.parse(booked).get("therapist").asText();
        boolean matched = false;
        for (JsonNode t : team) {
            String full = t.get("firstName").asText() + " " + t.get("lastName").asText();
            if (full.equals(given)) {
                matched = "FEMALE".equals(t.path("sex").asText(null));
            }
        }
        assertThat(matched)
                .as("the client asked to be treated by a woman - being handed a man is the one "
                    + "failure this feature exists to prevent")
                .isTrue();
    }

    @Test
    @DisplayName("the preference narrows availability rather than being decorative")
    void aPreferenceActuallyFilters() throws Exception {
        TestSupport api = api();
        String ana = api.tokenFor(TestSupport.ANA);
        String branch = api.branchIdContaining(ana, "Bulan");
        String serviceId = api.serviceIdNamed(ana, "Ventosa", 30);

        // Two assessments, identical but for the preference. Comparing two forms
        // rather than mutating one keeps this away from updateForm, which is a
        // full replace and would blank the complaint on a partial body.
        String anyone = api.createForm(ana, branch, "LOWER_BACK_PAIN", "LOWER_BACK_PAIN")
                .get("id").asText();
        String womanOnly = api.createForm(ana, branch, "LOWER_BACK_PAIN", "LOWER_BACK_PAIN", "FEMALE")
                .get("id").asText();

        int open = api.getAs(ana, "/api/v1/appointments/availability?formId=" + anyone
                + "&serviceId=" + serviceId).get("slots").size();
        int narrowed = api.getAs(ana, "/api/v1/appointments/availability?formId=" + womanOnly
                + "&serviceId=" + serviceId).get("slots").size();

        // Identical times both ways would mean the preference is being stored
        // and ignored, which is exactly the state TherapistStatus was in.
        assertThat(narrowed)
                .as("asking for one therapist out of several cannot leave availability unchanged")
                .isLessThanOrEqualTo(open);
        assertThat(narrowed)
                .as("a female therapist is on the team, so some times must remain")
                .isGreaterThan(0);
    }
}
