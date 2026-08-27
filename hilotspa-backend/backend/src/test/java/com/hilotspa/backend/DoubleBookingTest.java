package com.hilotspa.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

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
 * Process Rule #4 — a resource cannot be given to two people.
 *
 * The claim: the assistant only ever PROPOSES a time, and the write happens in
 * Spring inside a transaction that re-checks who is free. These tests exhaust a
 * branch's therapists at one time and show that the next request is refused —
 * through the chat path, the direct path, AND the front desk, because all three
 * go through the same assignment.
 *
 * That last part is the one worth showing at a defence. A walk-in typed at the
 * counter and a booking made by the chatbot cannot award the same therapist,
 * and it is not because anybody remembered to check: they call the same method
 * inside the same transaction.
 *
 * HONEST LIMITATION: these requests run sequentially. A truly interleaved race
 * would need two connections committing at the same instant, which cannot be
 * simulated inside a rolled-back test transaction. What is proven here is that
 * the re-check exists and is correct; the isolation guarantee that makes it
 * atomic is Postgres's, not ours.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class DoubleBookingTest {

    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper json;

    private TestSupport api() { return new TestSupport(mvc, json); }

    private record Fixture(String anaToken, String benToken, String staffToken,
                           String anaFormId, String benFormId,
                           String serviceId, String start) {
    }

    /** Two clients at the same branch, and the next time both could be given. */
    private Fixture setUp(TestSupport api) throws Exception {
        String ana = api.tokenFor(TestSupport.ANA);
        String ben = api.tokenFor(TestSupport.BEN);
        String staff = api.tokenFor(TestSupport.STAFF_BULAN);
        String branch = api.branchIdContaining(ana, "Bulan");

        String serviceId = api.serviceIdNamed(ana, "Signature Massage", 60);
        String anaForm = api.createForm(ana, branch, "LOWER_BACK_PAIN", "LOWER_BACK_PAIN")
                .get("id").asText();
        String benForm = api.createForm(ben, branch, "LOWER_BACK_PAIN", "LOWER_BACK_PAIN")
                .get("id").asText();

        JsonNode availability = api.getAs(ana,
                "/api/v1/appointments/availability?formId=" + anaForm + "&serviceId=" + serviceId);
        JsonNode slots = availability.get("slots");
        assertThat(slots)
                .as("the seeded branch must have open times, or nothing below can be tested")
                .isNotEmpty();

        return new Fixture(ana, ben, staff, anaForm, benForm, serviceId,
                slots.get(0).get("start").asText());
    }

    private String bookBody(String formId, String serviceId, String start, String key) {
        return """
                {"formId":"%s","serviceId":"%s","start":"%s",
                 "idempotencyKey":"%s","consentText":"Yes please, book that time."}
                """.formatted(formId, serviceId, start, key);
    }

    // ------------------------------------------------------- exhaustion

    @Test
    @DisplayName("no therapist or room is ever given to two people at the same time")
    void resourcesAreExhaustedNotOverbooked() throws Exception {
        TestSupport api = api();
        Fixture f = setUp(api);

        List<String> therapists = new ArrayList<>();
        List<String> rooms = new ArrayList<>();

        MvcResult first = api.postRaw(f.anaToken(), "/api/v1/appointments",
                bookBody(f.anaFormId(), f.serviceId(), f.start(), "ana-1"));
        assertThat(first.getResponse().getStatus())
                .as("the slot came from availability, so the first client must fit")
                .isEqualTo(201);
        JsonNode a = api.parse(first);
        therapists.add(a.get("therapist").asText());
        rooms.add(a.get("room").asText());

        // Keep asking for the SAME minute until the branch runs out. How many
        // fit depends on how many therapists are free, which depends on what is
        // already in this database - so the test asserts the invariant rather
        // than a number it would have to guess.
        for (int i = 0; i < 10; i++) {
            MvcResult res = api.postRaw(f.staffToken(), "/api/v1/appointments/walk-in", """
                    {"serviceId":"%s","start":"%s","name":"Walk-in %d","idempotencyKey":"w%d"}
                    """.formatted(f.serviceId(), f.start(), i, i));
            int status = res.getResponse().getStatus();
            if (status == 409) {
                break;      // exhausted, which is the correct answer
            }
            assertThat(status).isEqualTo(201);
            JsonNode b = api.parse(res);
            therapists.add(b.get("therapist").asText());
            rooms.add(b.get("room").asText());
        }

        // The whole of Process Rule #4, in two lines.
        assertThat(therapists).doesNotHaveDuplicates();
        assertThat(rooms).doesNotHaveDuplicates();
        assertThat(therapists.size())
                .as("the branch must refuse eventually rather than book forever")
                .isLessThan(11);
    }

    @Test
    @DisplayName("the front desk cannot give away a therapist the assistant already booked")
    void theCounterAndTheChatbotShareOneTruth() throws Exception {
        TestSupport api = api();
        Fixture f = setUp(api);

        // Take every therapist free at that minute, through the ONLINE path.
        String online = api.parse(api.postRaw(f.anaToken(), "/api/v1/appointments",
                bookBody(f.anaFormId(), f.serviceId(), f.start(), "ana-1")))
                .get("therapist").asText();

        String second = null;
        MvcResult benRes = api.postRaw(f.benToken(), "/api/v1/appointments",
                bookBody(f.benFormId(), f.serviceId(), f.start(), "ben-1"));
        if (benRes.getResponse().getStatus() == 201) {
            second = api.parse(benRes).get("therapist").asText();
            assertThat(second)
                    .as("two clients at the same minute may never share a therapist")
                    .isNotEqualTo(online);
        }

        // Now the COUNTER tries the same minute, until it is refused.
        int accepted = 0;
        int status = 201;
        for (int i = 0; i < 10 && status == 201; i++) {
            MvcResult res = api.postRaw(f.staffToken(), "/api/v1/appointments/walk-in", """
                    {"serviceId":"%s","start":"%s","name":"Counter %d","idempotencyKey":"c%d"}
                    """.formatted(f.serviceId(), f.start(), i, i));
            status = res.getResponse().getStatus();
            if (status == 201) {
                accepted++;
                assertThat(api.parse(res).get("therapist").asText())
                        .as("the counter must not be handed a therapist the assistant committed")
                        .isNotEqualTo(online)
                        .isNotEqualTo(second == null ? "" : second);
            }
        }

        assertThat(status)
                .as("the counter and the assistant draw on one pool, so it must run out")
                .isEqualTo(409);
        assertThat(accepted).isLessThan(10);
    }

    // ------------------------------------------------------- idempotency

    @Test
    @DisplayName("the same booking sent twice creates ONE appointment")
    void retriesDoNotDoubleBook() throws Exception {
        TestSupport api = api();
        Fixture f = setUp(api);
        String body = bookBody(f.anaFormId(), f.serviceId(), f.start(), "ana-retry");

        JsonNode first = api.parse(api.postRaw(f.anaToken(), "/api/v1/appointments", body));
        JsonNode second = api.parse(api.postRaw(f.anaToken(), "/api/v1/appointments", body));

        assertThat(second.get("id").asText())
                .as("an agent that retried a timed-out call must not book the client twice")
                .isEqualTo(first.get("id").asText());

        // And exactly one row exists for that client and time.
        int mine = 0;
        for (JsonNode bk : api.getAs(f.anaToken(), "/api/v1/appointments/mine")) {
            if (bk.get("start").asText().equals(f.start())) {
                mine++;
            }
        }
        assertThat(mine).isEqualTo(1);
    }

    @Test
    @DisplayName("a walk-in typed twice at the counter creates ONE appointment")
    void theFrontDeskCanDoubleTapSafely() throws Exception {
        TestSupport api = api();
        Fixture f = setUp(api);
        String body = """
                {"serviceId":"%s","start":"%s","name":"Pedro Santos","idempotencyKey":"w1"}
                """.formatted(f.serviceId(), f.start());

        JsonNode first = api.parse(api.postRaw(f.staffToken(), "/api/v1/appointments/walk-in", body));
        JsonNode second = api.parse(api.postRaw(f.staffToken(), "/api/v1/appointments/walk-in", body));

        assertThat(second.get("id").asText()).isEqualTo(first.get("id").asText());
    }

    // ------------------------------------------------------ walk-in rules

    @Test
    @DisplayName("a walk-in without a name is refused - it is the only thing identifying the visit")
    void aWalkInNeedsAName() throws Exception {
        TestSupport api = api();
        Fixture f = setUp(api);

        assertThat(api.postRaw(f.staffToken(), "/api/v1/appointments/walk-in", """
                {"serviceId":"%s","start":"%s","name":"  "}
                """.formatted(f.serviceId(), f.start())).getResponse().getStatus())
                .isEqualTo(400);
    }

    @Test
    @DisplayName("a walk-in appears on the branch day sheet by name, with no account attached")
    void walkInsAreVisibleToStaff() throws Exception {
        TestSupport api = api();
        Fixture f = setUp(api);
        String day = f.start().substring(0, 10);

        api.postRaw(f.staffToken(), "/api/v1/appointments/walk-in", """
                {"serviceId":"%s","start":"%s","name":"Pedro Santos","idempotencyKey":"w1"}
                """.formatted(f.serviceId(), f.start()));

        JsonNode sheet = api.getAs(f.staffToken(), "/api/v1/appointments/schedule?date=" + day);
        assertThat(sheet).anyMatch(r -> "Pedro Santos".equals(r.get("client").asText())
                && "STAFF_MANUAL".equals(r.get("source").asText())
                && !r.get("hasAssessment").asBoolean());
    }

    @Test
    @DisplayName("a customer cannot record a walk-in")
    void onlyStaffRecordWalkIns() throws Exception {
        TestSupport api = api();
        Fixture f = setUp(api);

        assertThat(api.postRaw(f.anaToken(), "/api/v1/appointments/walk-in", """
                {"serviceId":"%s","start":"%s","name":"Pedro Santos"}
                """.formatted(f.serviceId(), f.start())).getResponse().getStatus())
                .isEqualTo(403);
    }
}
