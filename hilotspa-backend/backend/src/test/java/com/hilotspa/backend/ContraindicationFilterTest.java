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
 * §D3 — the safety filter, tested without going near the model.
 *
 * GET /assistant/catalogue runs exactly the same judge() that builds
 * allowedServices before n8n is called, so this exercises the real filter with
 * no network dependency and no Vertex bill. If a service is excluded here, it
 * was never put in front of the model at all.
 *
 * The distinction being defended: the app does not decide anything clinical. It
 * applies a rule a practitioner wrote and signed. These tests therefore also
 * check that a rule cannot be changed without a name against it — an unsigned
 * safety rule IS the app making a medical decision on its own, which is the one
 * thing the Delimitation says it never does.
 *
 * NOTE: the seeded protocol table has ZERO contraindications, because archived
 * records show what was given and never what was withheld. These tests create
 * one, prove it bites, and roll back. The mechanism is proven; the clinical
 * content is still task 4.13.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ContraindicationFilterTest {

    private static final String SIGNATURE = "Gary O. Trinidad, bone setter (test)";

    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper json;

    private TestSupport api() { return new TestSupport(mvc, json); }

    /** The catalogue row for one service, judged against one assessment. */
    private JsonNode entry(TestSupport api, String token, String formId, String serviceId)
            throws Exception {
        for (JsonNode e : api.getAs(token, "/api/v1/assistant/catalogue?formId=" + formId)) {
            if (e.get("serviceId").asText().equals(serviceId)) {
                return e;
            }
        }
        throw new IllegalStateException("Service not in the catalogue: " + serviceId);
    }

    /** Finds a seeded protocol row by service name and condition. */
    private JsonNode protocolFor(TestSupport api, String admin, String serviceName,
                                 String condition) throws Exception {
        for (JsonNode p : api.getAs(admin, "/api/v1/protocols")) {
            if (p.get("serviceName").asText().equals(serviceName)
                    && p.get("condition").asText().equals(condition)) {
                return p;
            }
        }
        throw new IllegalStateException("No seeded rule: " + serviceName + " x " + condition);
    }

    // ------------------------------------------------------------ baseline

    @Test
    @DisplayName("with no contraindications on file, nothing is excluded - and the screen says so")
    void nothingIsExcludedUntilRulesExist() throws Exception {
        TestSupport api = api();
        String ana = api.tokenFor(TestSupport.ANA);
        String branch = api.branchIdContaining(ana, "Bulan");
        String formId = api.createForm(ana, branch, "LOWER_BACK_PAIN", "LOWER_BACK_PAIN")
                .get("id").asText();

        JsonNode catalogue = api.getAs(ana, "/api/v1/assistant/catalogue?formId=" + formId);

        assertThat(catalogue).isNotEmpty();
        for (JsonNode e : catalogue) {
            assertThat(e.get("suitable").asBoolean())
                    .as("no CONTRAINDICATED rows are seeded, so nothing may be excluded")
                    .isTrue();
        }
        // The historically INDICATED service is still marked as such.
        assertThat(catalogue).anyMatch(e -> "INDICATED".equals(e.get("rule").asText()));
    }

    // ----------------------------------------------------------- it bites

    @Test
    @DisplayName("a CONTRAINDICATED rule removes the service for a client with that complaint")
    void contraindicationExcludesTheService() throws Exception {
        TestSupport api = api();
        String admin = api.tokenFor(TestSupport.ADMIN);
        String ana = api.tokenFor(TestSupport.ANA);
        String branch = api.branchIdContaining(ana, "Bulan");

        JsonNode rule = protocolFor(api, admin, "Signature Massage", "LOWER_BACK_PAIN");
        String serviceId = rule.get("serviceId").asText();

        String formId = api.createForm(ana, branch, "LOWER_BACK_PAIN", "LOWER_BACK_PAIN")
                .get("id").asText();

        // Before: allowed.
        assertThat(entry(api, ana, formId, serviceId).get("suitable").asBoolean()).isTrue();

        // The practitioner changes their mind, and signs for it.
        String body = """
                {"rule":"CONTRAINDICATED",
                 "rationale":"Not advised for this presentation.",
                 "authoredBy":"%s"}
                """.formatted(SIGNATURE);
        assertThat(api.putRaw(admin, "/api/v1/protocols/" + rule.get("id").asText(), body)
                .getResponse().getStatus()).isEqualTo(200);

        // After: excluded, WITH the reason, and still present in the list.
        JsonNode after = entry(api, ana, formId, serviceId);
        assertThat(after.get("suitable").asBoolean()).isFalse();
        assertThat(after.get("rule").asText()).isEqualTo("CONTRAINDICATED");
        assertThat(after.get("reason").asText()).isNotBlank();
    }

    @Test
    @DisplayName("a client without that complaint is unaffected")
    void theRuleIsPerClientNotGlobal() throws Exception {
        TestSupport api = api();
        String admin = api.tokenFor(TestSupport.ADMIN);
        String ben = api.tokenFor(TestSupport.BEN);
        String branch = api.branchIdContaining(ben, "Bulan");

        JsonNode rule = protocolFor(api, admin, "Signature Massage", "LOWER_BACK_PAIN");
        String serviceId = rule.get("serviceId").asText();

        api.putRaw(admin, "/api/v1/protocols/" + rule.get("id").asText(), """
                {"rule":"CONTRAINDICATED","authoredBy":"%s"}""".formatted(SIGNATURE));

        // Ben's complaint is a frozen shoulder, not lower back pain.
        String formId = api.createForm(ben, branch, "FROZEN_SHOULDER", "FROZEN_SHOULDER")
                .get("id").asText();

        assertThat(entry(api, ben, formId, serviceId).get("suitable").asBoolean())
                .as("the rule is about a condition, not about the service in general")
                .isTrue();
    }

    @Test
    @DisplayName("a contraindication beats an indication when one assessment carries both")
    void contraindicationWinsOverIndication() throws Exception {
        TestSupport api = api();
        String admin = api.tokenFor(TestSupport.ADMIN);
        String ana = api.tokenFor(TestSupport.ANA);
        String branch = api.branchIdContaining(ana, "Bulan");

        // Signature Massage is seeded INDICATED for BOTH of these conditions.
        JsonNode stiffNeckRule = protocolFor(api, admin, "Signature Massage", "STIFF_NECK");
        String serviceId = stiffNeckRule.get("serviceId").asText();

        api.putRaw(admin, "/api/v1/protocols/" + stiffNeckRule.get("id").asText(), """
                {"rule":"CONTRAINDICATED","authoredBy":"%s"}""".formatted(SIGNATURE));

        // Chief complaint says yes (LOWER_BACK_PAIN, still INDICATED).
        // A marked point says no (STIFF_NECK, now CONTRAINDICATED).
        String formId = api.createForm(ana, branch, "LOWER_BACK_PAIN", "STIFF_NECK")
                .get("id").asText();

        JsonNode judged = entry(api, ana, formId, serviceId);
        assertThat(judged.get("suitable").asBoolean())
                .as("if one condition says yes and another says no, the answer is no")
                .isFalse();
        assertThat(judged.get("rule").asText()).isEqualTo("CONTRAINDICATED");
    }

    // ------------------------------------------------- the signature rule

    @Test
    @DisplayName("a rule cannot be changed without a name against it")
    void anUnsignedChangeIsRefused() throws Exception {
        TestSupport api = api();
        String admin = api.tokenFor(TestSupport.ADMIN);
        String id = protocolFor(api, admin, "Signature Massage", "LOWER_BACK_PAIN")
                .get("id").asText();

        assertThat(api.putRaw(admin, "/api/v1/protocols/" + id,
                        "{\"rule\":\"CONTRAINDICATED\"}").getResponse().getStatus())
                .as("no author at all")
                .isEqualTo(400);

        assertThat(api.putRaw(admin, "/api/v1/protocols/" + id,
                        "{\"rule\":\"CONTRAINDICATED\",\"authoredBy\":\"  \"}")
                        .getResponse().getStatus())
                .as("whitespace is not a signature")
                .isEqualTo(400);

        assertThat(api.putRaw(admin, "/api/v1/protocols/" + id,
                        "{\"rule\":\"CONTRAINDICATED\",\"authoredBy\":"
                        + "\"DERIVED FROM 137 ARCHIVED RECORDS - awaiting practitioner sign-off\"}")
                        .getResponse().getStatus())
                .as("the seeded placeholder is not a signature either")
                .isEqualTo(400);
    }

    @Test
    @DisplayName("staff may read the protocol table but only an administrator may change it")
    void onlyAdminWritesTheSafetyTable() throws Exception {
        TestSupport api = api();
        String admin = api.tokenFor(TestSupport.ADMIN);
        String staff = api.tokenFor(TestSupport.STAFF_BULAN);
        String id = protocolFor(api, admin, "Signature Massage", "LOWER_BACK_PAIN")
                .get("id").asText();

        assertThat(api.getRaw(staff, "/api/v1/protocols").getResponse().getStatus()).isEqualTo(200);
        assertThat(api.putRaw(staff, "/api/v1/protocols/" + id, """
                {"rule":"CONTRAINDICATED","authoredBy":"%s"}""".formatted(SIGNATURE))
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(api.getRaw(api.tokenFor(TestSupport.ANA), "/api/v1/protocols")
                .getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("changing a safety rule is written to the audit log with both rules")
    void protocolChangesAreAudited() throws Exception {
        TestSupport api = api();
        String admin = api.tokenFor(TestSupport.ADMIN);
        JsonNode rule = protocolFor(api, admin, "Signature Massage", "LOWER_BACK_PAIN");

        api.putRaw(admin, "/api/v1/protocols/" + rule.get("id").asText(), """
                {"rule":"CONTRAINDICATED","authoredBy":"%s"}""".formatted(SIGNATURE));

        JsonNode log = api.getAs(admin, "/api/v1/audit-log?action=PROTOCOL_EDITED");
        assertThat(log).isNotEmpty();

        // Search rather than take the first row: a previous run against this dev
        // database may have left its own PROTOCOL_EDITED entries behind.
        assertThat(log).anyMatch(row -> {
            String details = row.get("details").asText();
            return details.contains("INDICATED")
                    && details.contains("CONTRAINDICATED")
                    && details.contains(SIGNATURE);
        });
    }
}
