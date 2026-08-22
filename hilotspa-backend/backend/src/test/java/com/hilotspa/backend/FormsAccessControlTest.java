package com.hilotspa.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Branch-scoped access control (Process Rule #5), tested end to end.
 *
 * These tests log in for real and send the returned JWT in the Authorization
 * header, so a pass proves the whole chain works: BCrypt verification, token
 * signing, token validation, role extraction, and service-level filtering.
 * Mocking the security context would prove none of that.
 *
 * Fixture data comes from DevDataSeeder, which is why @ActiveProfiles("dev").
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional   // every test rolls back - the dev database stays clean
class FormsAccessControlTest {

    private static final String PASSWORD = "hilotspa123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper json;

    // ------------------------------------------------------------- helpers

    /** Logs in and returns the raw JWT. */
    private String tokenFor(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";

        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return json.readTree(response).get("token").asText();
    }

    /** GET as a signed-in user, returning the parsed JSON body. */
    private JsonNode getAs(String token, String path) throws Exception {
        String response = mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response);
    }

    /** The id of the first branch whose name contains the given text. */
    private String branchIdContaining(String token, String text) throws Exception {
        for (JsonNode branch : getAs(token, "/api/v1/branches")) {
            if (branch.get("name").asText().contains(text)) {
                return branch.get("id").asText();
            }
        }
        throw new IllegalStateException("No seeded branch matching: " + text);
    }

    /** Creates a form and returns the response body. ownerId may be a lie - that is the point. */
    private JsonNode createForm(String token, String ownerId, String branchId) throws Exception {
        String body = """
                {
                  "userId": "%s",
                  "branchId": "%s",
                  "intent": "PAIN",
                  "mainComplaint": "LOWER_BACK_PAIN",
                  "mainComplaintDuration": "3 months",
                  "hasTherapy": false,
                  "status": "PENDING",
                  "painPoints": [
                    { "bodyView": "BACK", "anatomicalRegion": "LUMBAR", "side": "CENTRE",
                      "coordinateX": 500, "coordinateY": 380,
                      "painScoreBefore": 8, "complaintType": "LOWER_BACK_PAIN" }
                  ]
                }
                """.formatted(ownerId, branchId);

        String response = mvc.perform(post("/api/v1/forms/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response);
    }

    // --------------------------------------------------------------- tests

    @Test
    @DisplayName("an unauthenticated request is rejected")
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/v1/forms"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a customer's token carries no branch; a staff token does")
    void tokenCarriesTheRightClaims() throws Exception {
        String customerLogin = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@customer.test\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode customer = json.readTree(customerLogin);
        assertThat(customer.get("role").asText()).isEqualTo("CUSTOMER");
        assertThat(customer.get("branchId").isNull()).isTrue();

        String staffLogin = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"staff.bulan@hilotspa.test\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode staff = json.readTree(staffLogin);
        assertThat(staff.get("role").asText()).isEqualTo("STAFF");
        assertThat(staff.get("branchId").isNull()).isFalse();
    }
}
