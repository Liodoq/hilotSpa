package com.hilotspa.backend;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Task 2.32 - cancelling a booking.
 *
 * The claim being tested is not "the button works". It is that a cancellation
 * does two opposite things correctly at once: it must RELEASE the therapist and
 * the room, so the hour becomes sellable again, and it must KEEP the row, so the
 * audit trail and the price at booking survive. A DELETE that actually deleted
 * would pass the first half and quietly destroy the second.
 *
 * Scoping is tested here too, for the same reason as everywhere else in this
 * suite: "only your own" is a query-level rule that no URL pattern can express,
 * so it can only be proven by asking as the wrong person.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class CancelBookingTest {

    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper json;

    private TestSupport api() { return new TestSupport(mvc, json); }

    private record Booked(String anaToken, String benToken, String staffToken,
                          String appointmentId, String start, String serviceId) {
    }

    private Booked bookOne(TestSupport api) throws Exception {
        String ana = api.tokenFor(TestSupport.ANA);
        String ben = api.tokenFor(TestSupport.BEN);
        String staff = api.tokenFor(TestSupport.STAFF_BULAN);
        String branch = api.branchIdContaining(ana, "Bulan");
        String serviceId = api.serviceIdNamed(ana, "Signature Massage", 60);
        String formId = api.createForm(ana, branch, "LOWER_BACK_PAIN", "LOWER_BACK_PAIN")
                .get("id").asText();

        JsonNode slots = api.getAs(ana,
                "/api/v1/appointments/availability?formId=" + formId
                + "&serviceId=" + serviceId).get("slots");
        assertThat(slots)
                .as("the seeded branch must have open times, or nothing below can be tested")
                .isNotEmpty();
        String start = slots.get(0).get("start").asText();

        MvcResult res = api.postRaw(ana, "/api/v1/appointments", """
                {"formId":"%s","serviceId":"%s","start":"%s",
                 "idempotencyKey":"cancel-fixture","consentText":"Yes please."}
                """.formatted(formId, serviceId, start));
        assertThat(res.getResponse().getStatus()).isEqualTo(201);

        return new Booked(ana, ben, staff, api.parse(res).get("id").asText(), start, serviceId);
    }

    /**
     * Books walk-ins into that exact minute until the branch refuses, and returns
     * how many fitted.
     *
     * It always leaves the branch EXHAUSTED - it only stops on a 409. That is
     * what makes the assertions below meaningful, and it is what I got wrong the
     * first time: the walk-ins booked here stay booked, so a second call cannot
     * be compared against the first as though the slate were clean.
     */
    private int fillUntilFull(TestSupport api, Booked b, String tag) throws Exception {
        int fitted = 0;
        for (int i = 0; i < 10; i++) {
            MvcResult res = api.postRaw(b.staffToken(), "/api/v1/appointments/walk-in", """
                    {"serviceId":"%s","start":"%s","name":"%s %d","idempotencyKey":"%s%d"}
                    """.formatted(b.serviceId(), b.start(), tag, i, tag, i));
            if (res.getResponse().getStatus() == 409) {
                return fitted;
            }
            assertThat(res.getResponse().getStatus()).isEqualTo(201);
            fitted++;
        }
        throw new IllegalStateException(
                "The branch accepted 10 bookings into one minute - it never refused, so this "
                + "test proves nothing. Check the seeded therapist and room counts.");
    }

    // ------------------------------------------------------------- release

    @Test
    @DisplayName("cancelling gives back exactly one therapist and one room")
    void cancellingReleasesTheResources() throws Exception {
        TestSupport api = api();
        Booked b = bookOne(api);

        // Fill the branch at that exact minute, with Ana's booking already in
        // place. This returns only once a walk-in has been REFUSED, so the minute
        // is now fully sold: every therapist and every room is committed.
        fillUntilFull(api, b, "before");

        MvcResult cancelled = api.deleteRaw(b.anaToken(), "/api/v1/appointments/" + b.appointmentId());
        assertThat(cancelled.getResponse().getStatus()).isEqualTo(200);

        // From a full branch, exactly ONE more must now fit - the pair Ana was
        // holding - and then it must be full again. Both halves matter: one is
        // "the cancel released something", the other is "it released only what it
        // held". A cancel that freed two pairs would be a worse bug than one that
        // freed none, and only the second assertion would catch it.
        int afterCancel = fillUntilFull(api, b, "after");
        assertThat(afterCancel)
                .as("a full branch must have room for exactly the one visit that was cancelled")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("the appointment is kept as CANCELLED, never deleted")
    void theRowSurvivesTheCancellation() throws Exception {
        TestSupport api = api();
        Booked b = bookOne(api);

        api.deleteRaw(b.anaToken(), "/api/v1/appointments/" + b.appointmentId());

        JsonNode mine = api.getAs(b.anaToken(), "/api/v1/appointments/mine");
        JsonNode found = null;
        for (JsonNode row : mine) {
            if (row.get("id").asText().equals(b.appointmentId())) {
                found = row;
            }
        }
        assertThat(found)
                .as("a deleted row would take the audit trail and the booked price with it")
                .isNotNull();
        assertThat(found.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(found.get("price"))
                .as("priceAtBooking must survive - it is how revenue is reconciled")
                .isNotNull();
    }

    // ------------------------------------------------------------- scoping

    @Test
    @DisplayName("a client cannot cancel someone else's booking")
    void cancellingIsScopedToTheOwner() throws Exception {
        TestSupport api = api();
        Booked b = bookOne(api);

        MvcResult res = api.deleteRaw(b.benToken(), "/api/v1/appointments/" + b.appointmentId());
        assertThat(res.getResponse().getStatus())
                .as("404, not 403 - confirming the id exists is itself a leak")
                .isEqualTo(404);

        JsonNode mine = api.getAs(b.anaToken(), "/api/v1/appointments/mine");
        boolean stillConfirmed = false;
        for (JsonNode row : mine) {
            if (row.get("id").asText().equals(b.appointmentId())) {
                stillConfirmed = !row.get("status").asText().equals("CANCELLED");
            }
        }
        assertThat(stillConfirmed)
                .as("Ben's refused request must not have touched Ana's visit")
                .isTrue();
    }

    @Test
    @DisplayName("staff can cancel a booking at their own branch")
    void staffCanCancelAtTheirBranch() throws Exception {
        TestSupport api = api();
        Booked b = bookOne(api);

        MvcResult res = api.deleteRaw(b.staffToken(), "/api/v1/appointments/" + b.appointmentId());
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(api.parse(res).get("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("cancelling twice is not an error")
    void cancellingIsIdempotent() throws Exception {
        TestSupport api = api();
        Booked b = bookOne(api);

        assertThat(api.deleteRaw(b.anaToken(), "/api/v1/appointments/" + b.appointmentId())
                .getResponse().getStatus()).isEqualTo(200);

        // A double-tap on Cancel is not a failure. Returning 409 here would make
        // the UI show an error for something that is already true.
        MvcResult second = api.deleteRaw(b.anaToken(), "/api/v1/appointments/" + b.appointmentId());
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(api.parse(second).get("status").asText()).isEqualTo("CANCELLED");
    }
}
