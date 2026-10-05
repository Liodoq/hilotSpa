package com.hilotspa.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * SPECIFIC OBJECTIVE 1 - the 2D Visual Wellness Profiling module.
 *
 * "To design and develop an interactive, web-based 2D Visual Wellness Profiling
 * module that allows customers to digitally record body areas, severity,
 * wellness concerns, and relevant profile information."
 *
 * WHAT CAN AND CANNOT BE PROVEN HERE.
 *
 * Nobody can automate "a client tapped the lower back on a diagram". What CAN
 * be proven, and is the claim the objective actually rests on, is that whatever
 * the client marked ARRIVES INTACT and COMES BACK UNCHANGED: the same body view,
 * the same anatomical region, the same side, the same coordinates, the same
 * severity, the same wellness concern.
 *
 * That matters because this record is clinical. A body map that silently
 * transposed left and right, or dropped a severity, would still look like a
 * working feature on screen while telling the practitioner something false about
 * the person on the table. The interaction is verified by User Acceptance
 * Testing (Objective 4); the FIDELITY of what it captures is verified here.
 *
 * ISO/IEC 25010 characteristics exercised:
 *   Functional correctness     - what was recorded is what is returned
 *   Functional completeness    - every field the objective names survives
 *   Security / confidentiality - a profile is readable only by its owner
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ObjectiveOneProfilingTest {

    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper json;

    private TestSupport api() { return new TestSupport(mvc, json); }

    // --------------------------------------------------- body areas and severity

    @Test
    @DisplayName("SO1 - a marked body area survives the round trip exactly as recorded")
    void theMarkedBodyAreaIsStoredFaithfully() throws Exception {
        TestSupport api = api();
        String ana = api.tokenFor(TestSupport.ANA);
        String branch = api.branchIdContaining(ana, "Bulan");

        JsonNode created = api.createForm(ana, branch, "LOWER_BACK_PAIN", "LOWER_BACK_PAIN");
        JsonNode read = api.getAs(ana, "/api/v1/forms/" + created.get("id").asText());

        assertThat(read.get("painPoints").size())
                .as("a profile with no recorded body area records nothing at all")
                .isEqualTo(1);

        JsonNode point = read.get("painPoints").get(0);

        // These are the exact values the client "marked". Each one is a separate
        // way for the record to be wrong about a person's body.
        assertThat(point.get("bodyView").asText())
                .as("front and back are not interchangeable").isEqualTo("BACK");
        assertThat(point.get("anatomicalRegion").asText())
                .as("the region is what the practitioner will treat").isEqualTo("LUMBAR");
        assertThat(point.get("side").asText())
                .as("left and right transposed is a clinical error, not a display bug")
                .isEqualTo("CENTRE");
        assertThat(point.get("coordinateX").asInt()).isEqualTo(500);
        assertThat(point.get("coordinateY").asInt()).isEqualTo(380);
    }

    @Test
    @DisplayName("SO1 - severity is recorded on the point it was reported against")
    void severityIsStoredAgainstThePoint() throws Exception {
        TestSupport api = api();
        String ana = api.tokenFor(TestSupport.ANA);
        String branch = api.branchIdContaining(ana, "Bulan");

        JsonNode created = api.createForm(ana, branch, "LOWER_BACK_PAIN", "LOWER_BACK_PAIN");
        JsonNode read = api.getAs(ana, "/api/v1/forms/" + created.get("id").asText());
        JsonNode point = read.get("painPoints").get(0);

        assertThat(point.get("painScoreBefore").asInt())
                .as("severity is the field that decides pressure and protocol")
                .isEqualTo(8);

        // Deliberately absent, not zero. The after-score is recorded by the
        // practitioner once the session has happened; writing 0 at intake would
        // claim an outcome that nobody has observed yet.
        JsonNode after = point.get("painScoreAfter");
        assertThat(after == null || after.isNull())
                .as("an unobserved outcome must not be stored as a measured zero")
                .isTrue();
    }

    @Test
    @DisplayName("SO1 - the wellness concern and profile details are recorded with the body map")
    void theWellnessConcernIsRecordedToo() throws Exception {
        TestSupport api = api();
        String ana = api.tokenFor(TestSupport.ANA);
        String branch = api.branchIdContaining(ana, "Bulan");

        JsonNode created = api.createForm(ana, branch, "LOWER_BACK_PAIN", "LOWER_BACK_PAIN");
        JsonNode read = api.getAs(ana, "/api/v1/forms/" + created.get("id").asText());

        assertThat(read.get("mainComplaint").asText()).isEqualTo("LOWER_BACK_PAIN");
        assertThat(read.get("mainComplaintDuration").asText())
                .as("how long it has gone on is part of the concern, not decoration")
                .isNotBlank();
        assertThat(read.get("intent").asText()).isEqualTo("PAIN");
        assertThat(read.get("branchId").asText())
                .as("a profile belongs to the branch that will treat it")
                .isEqualTo(branch);
        // NOT asserting createdAt. @CreationTimestamp is written by Hibernate at
        // INSERT, and this test runs inside a transaction that never commits, so
        // the value is legitimately still null when the response is built. An
        // assertion that can only pass outside a rolled-back test is an
        // assertion about the test harness, not about the system.
    }

    @Test
    @DisplayName("SO1 - nothing recorded at intake is altered by storing and retrieving it")
    void storingAndRetrievingChangesNothing() throws Exception {
        TestSupport api = api();
        String ana = api.tokenFor(TestSupport.ANA);
        String branch = api.branchIdContaining(ana, "Bulan");

        JsonNode created = api.createForm(ana, branch, "NECK_PAIN", "NECK_PAIN");
        JsonNode read = api.getAs(ana, "/api/v1/forms/" + created.get("id").asText());

        // Compared field by field against what the server said it had stored,
        // rather than against values typed into this test. This catches a field
        // that is accepted, acknowledged, and then quietly lost on read - which
        // no assertion written from the request body would notice.
        for (String field : new String[] {
                "id", "branchId", "mainComplaint", "mainComplaintDuration", "intent", "status" }) {
            assertThat(read.get(field).asText())
                    .as("field '%s' changed between writing and reading", field)
                    .isEqualTo(created.get(field).asText());
        }

        JsonNode wrote = created.get("painPoints").get(0);
        JsonNode back  = read.get("painPoints").get(0);
        for (String field : new String[] {
                "bodyView", "anatomicalRegion", "side", "coordinateX", "coordinateY",
                "painScoreBefore", "complaintType" }) {
            assertThat(back.get(field).asText())
                    .as("pain point field '%s' changed between writing and reading", field)
                    .isEqualTo(wrote.get(field).asText());
        }
    }

    // ------------------------------------------------------------ confidentiality

    @Test
    @DisplayName("SO1 - a wellness profile is readable only by the client it belongs to")
    void aProfileIsPrivateToItsOwner() throws Exception {
        TestSupport api = api();
        String ana = api.tokenFor(TestSupport.ANA);
        String ben = api.tokenFor(TestSupport.BEN);
        String branch = api.branchIdContaining(ana, "Bulan");

        JsonNode anas = api.createForm(ana, branch, "LOWER_BACK_PAIN", "LOWER_BACK_PAIN");

        int status = api.getRaw(ben, "/api/v1/forms/" + anas.get("id").asText())
                .getResponse().getStatus();

        // This record carries physiological data. Holding a valid token is not
        // the same as being the person the record is about.
        assertThat(status)
                .as("one client must not be able to read another client's health record")
                .isIn(403, 404);
    }
}
