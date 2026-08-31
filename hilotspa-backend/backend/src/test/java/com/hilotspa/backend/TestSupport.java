package com.hilotspa.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared plumbing for the integration tests.
 *
 * Every helper logs in for real and sends the returned JWT, so a passing test
 * exercises the whole chain - BCrypt verification, token signing, validation,
 * role extraction, and service-level filtering. Mocking the security context
 * would prove none of that, which is the point of testing authorisation at all.
 *
 * Fixtures come from DevDataSeeder, hence @ActiveProfiles("dev") on each test.
 */
final class TestSupport {

    static final String PASSWORD = "hilotspa123";

    static final String ADMIN          = "admin@hilotspa.test";
    static final String STAFF_BULAN    = "staff.bulan@hilotspa.test";
    static final String STAFF_SORSOGON = "staff.sorsogon@hilotspa.test";
    static final String ANA            = "ana@customer.test";
    static final String BEN            = "ben@customer.test";

    private final MockMvc mvc;
    private final JsonMapper json;

    TestSupport(MockMvc mvc, JsonMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    // -------------------------------------------------------------- auth

    String tokenFor(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
        String res = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    // ------------------------------------------------------------- verbs

    JsonNode getAs(String token, String path) throws Exception {
        String res = mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res);
    }

    /** For asserting on the status rather than the body. */
    MvcResult getRaw(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn();
    }

    MvcResult postRaw(String token, String path, String body) throws Exception {
        return mvc.perform(post(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
    }

    MvcResult putRaw(String token, String path, String body) throws Exception {
        return mvc.perform(put(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
    }

    MvcResult deleteRaw(String token, String path) throws Exception {
        return mvc.perform(delete(path).header("Authorization", "Bearer " + token)).andReturn();
    }

    JsonNode parse(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    // ---------------------------------------------------------- fixtures

    String branchIdContaining(String token, String text) throws Exception {
        for (JsonNode b : getAs(token, "/api/v1/branches")) {
            if (b.get("name").asText().contains(text)) {
                return b.get("id").asText();
            }
        }
        throw new IllegalStateException("No seeded branch matching: " + text);
    }

    String serviceIdNamed(String token, String name, int minutes) throws Exception {
        for (JsonNode s : getAs(token, "/api/v1/assistant/catalogue")) {
            if (s.get("name").asText().equals(name)
                    && s.get("durationMinutes").asInt() == minutes) {
                return s.get("serviceId").asText();
            }
        }
        throw new IllegalStateException("No seeded service: " + name + " " + minutes + "min");
    }

    /**
     * Creates an assessment owned by the caller.
     *
     * `secondCondition` becomes a pain point's complaintType, which matters for
     * the contraindication tests: judge() reads the chief complaint AND every
     * marked point, so one form can carry two conditions that disagree.
     */
    JsonNode createForm(String token, String branchId, String mainComplaint,
                        String secondCondition) throws Exception {
        return createForm(token, branchId, mainComplaint, secondCondition, null);
    }

    /**
     * The same, with a therapist preference - FEMALE, MALE, or null for none.
     *
     * Set at creation rather than by a later PUT on purpose: updateForm is a
     * full replace, so a partial body would blank the complaint and the pain
     * points, and the test would then be asserting against an assessment no
     * client could ever have produced. The wizard posts the whole thing at once,
     * so this is what really happens.
     */
    JsonNode createForm(String token, String branchId, String mainComplaint,
                        String secondCondition, String therapistPreference) throws Exception {
        String body = """
                {
                  "branchId": "%s",
                  "intent": "PAIN",
                  "mainComplaint": "%s",
                  "mainComplaintDuration": "3 months",
                  "hadIllness": false,
                  "hasTherapy": false,
                  "status": "PENDING",
                  %s
                  "painPoints": [
                    { "bodyView": "BACK", "anatomicalRegion": "LUMBAR", "side": "CENTRE",
                      "coordinateX": 500, "coordinateY": 380,
                      "painScoreBefore": 8, "complaintType": "%s" }
                  ]
                }
                """.formatted(branchId, mainComplaint,
                        therapistPreference == null ? ""
                                : "\"therapistPreference\": \"" + therapistPreference + "\",",
                        secondCondition);

        MvcResult res = postRaw(token, "/api/v1/forms/create", body);
        if (res.getResponse().getStatus() != 201) {
            throw new IllegalStateException("createForm failed: "
                    + res.getResponse().getStatus() + " " + res.getResponse().getContentAsString());
        }
        return parse(res);
    }
}
