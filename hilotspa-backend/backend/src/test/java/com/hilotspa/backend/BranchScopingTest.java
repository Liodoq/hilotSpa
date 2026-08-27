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
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Process Rule #5 — branch-scoped access, tested rather than asserted.
 *
 * The claim under test is that a STAFF account cannot reach another branch's
 * data by ANY route, including by editing the request. That is why none of
 * these endpoints take a branch parameter: the branch is read from the signed
 * token, so there is nothing for a caller to tamper with. These tests prove the
 * scoping actually happens rather than that the parameter is absent.
 *
 * It is also the property the whole decentralisation argument rests on. A
 * therapist and a room belong to exactly one branch, so only that branch can
 * book them, so two nodes can never award the same slot. If this test fails,
 * that argument fails with it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class BranchScopingTest {

    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper json;

    private TestSupport api() { return new TestSupport(mvc, json); }

    private static List<String> names(JsonNode array, String first, String last) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : array) {
            out.add(n.get(first).asText() + (last == null ? "" : " " + n.get(last).asText()));
        }
        return out;
    }

    // ------------------------------------------------------------ the rule

    @Test
    @DisplayName("two branches' staff see disjoint therapists, and neither sees the other's")
    void therapistsAreBranchScoped() throws Exception {
        TestSupport api = api();
        JsonNode bulan = api.getAs(api.tokenFor(TestSupport.STAFF_BULAN), "/api/v1/therapists");
        JsonNode sorsogon = api.getAs(api.tokenFor(TestSupport.STAFF_SORSOGON), "/api/v1/therapists");

        List<String> bulanNames = names(bulan, "firstName", "lastName");
        List<String> sorsoganNames = names(sorsogon, "firstName", "lastName");

        assertThat(bulanNames).isNotEmpty();
        assertThat(sorsoganNames).isNotEmpty();
        assertThat(bulanNames).doesNotContainAnyElementsOf(sorsoganNames);

        // And every row really does belong to the caller's own branch.
        for (JsonNode t : bulan) {
            assertThat(t.get("branchName").asText()).contains("Bulan");
        }
        for (JsonNode t : sorsogon) {
            assertThat(t.get("branchName").asText()).contains("Sorsogon City");
        }
    }

    @Test
    @DisplayName("rooms are branch-scoped the same way")
    void roomsAreBranchScoped() throws Exception {
        TestSupport api = api();
        JsonNode bulan = api.getAs(api.tokenFor(TestSupport.STAFF_BULAN), "/api/v1/rooms");
        JsonNode sorsogon = api.getAs(api.tokenFor(TestSupport.STAFF_SORSOGON), "/api/v1/rooms");

        for (JsonNode r : bulan) {
            assertThat(r.get("branchName").asText()).contains("Bulan");
        }
        for (JsonNode r : sorsogon) {
            assertThat(r.get("branchName").asText()).contains("Sorsogon City");
        }
        // Both branches seed a room called "Treatment Room 1", so comparing ids
        // rather than names is what makes this test mean anything.
        List<String> bulanIds = names(bulan, "id", null);
        List<String> sorsoganIds = names(sorsogon, "id", null);
        assertThat(bulanIds).doesNotContainAnyElementsOf(sorsoganIds);
    }

    @Test
    @DisplayName("an administrator sees every branch")
    void adminSeesEverything() throws Exception {
        TestSupport api = api();
        String admin = api.tokenFor(TestSupport.ADMIN);
        JsonNode all = api.getAs(admin, "/api/v1/therapists");

        List<String> branches = names(all, "branchName", null);
        assertThat(branches).anyMatch(b -> b.contains("Bulan"));
        assertThat(branches).anyMatch(b -> b.contains("Sorsogon City"));
    }

    // ------------------------------------------------- the write direction

    @Test
    @DisplayName("staff cannot edit a therapist belonging to another branch")
    void staffCannotWriteAcrossBranches() throws Exception {
        TestSupport api = api();
        String sorsoganStaff = api.tokenFor(TestSupport.STAFF_SORSOGON);
        String bulanStaff = api.tokenFor(TestSupport.STAFF_BULAN);

        String foreignTherapistId = api
                .getAs(sorsoganStaff, "/api/v1/therapists").get(0).get("id").asText();

        int status = api.putRaw(bulanStaff, "/api/v1/therapists/" + foreignTherapistId,
                "{\"status\":\"OFF_DUTY\"}").getResponse().getStatus();

        assertThat(status).isEqualTo(403);
    }

    @Test
    @DisplayName("a staff account cannot create a therapist at another branch by naming it")
    void branchInTheBodyIsIgnoredForStaff() throws Exception {
        TestSupport api = api();
        String bulanStaff = api.tokenFor(TestSupport.STAFF_BULAN);
        String admin = api.tokenFor(TestSupport.ADMIN);
        String sorsoganBranchId = api.branchIdContaining(admin, "Sorsogon City");

        // The body asks for Sorsogon City. The token says Bulan. The token wins.
        String body = """
                {"firstName":"Tamper","lastName":"Test","branchId":"%s"}
                """.formatted(sorsoganBranchId);

        JsonNode created = api.parse(api.postRaw(bulanStaff, "/api/v1/therapists", body));
        assertThat(created.get("branchName").asText()).contains("Bulan");
        assertThat(created.get("branchId").asText()).isNotEqualTo(sorsoganBranchId);
    }

    // ------------------------------------------------------ the outer wall

    @Test
    @DisplayName("a customer cannot reach operational data at all")
    void customersAreShutOut() throws Exception {
        TestSupport api = api();
        String customer = api.tokenFor(TestSupport.ANA);

        assertThat(api.getRaw(customer, "/api/v1/therapists").getResponse().getStatus())
                .isEqualTo(403);
        assertThat(api.getRaw(customer, "/api/v1/rooms").getResponse().getStatus())
                .isEqualTo(403);
        assertThat(api.getRaw(customer, "/api/v1/audit-log").getResponse().getStatus())
                .isEqualTo(403);
        assertThat(api.getRaw(customer, "/api/v1/admin/overview").getResponse().getStatus())
                .isEqualTo(403);
        // The day sheet carries other clients' names, so it is staff-only too.
        assertThat(api.getRaw(customer, "/api/v1/appointments/schedule").getResponse().getStatus())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("staff cannot promote themselves - only an administrator lists or edits accounts")
    void staffCannotTouchAccounts() throws Exception {
        TestSupport api = api();
        assertThat(api.getRaw(api.tokenFor(TestSupport.STAFF_BULAN), "/api/v1/users")
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(api.getRaw(api.tokenFor(TestSupport.ANA), "/api/v1/users")
                .getResponse().getStatus()).isEqualTo(403);
    }
}
