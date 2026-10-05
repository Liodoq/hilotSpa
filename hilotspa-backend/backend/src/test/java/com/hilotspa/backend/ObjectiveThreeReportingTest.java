package com.hilotspa.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

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
 * SPECIFIC OBJECTIVE 3 - the centralised reporting and analytics module.
 *
 * "To develop a centralized reporting and analytics module that supports branch
 * management by presenting service frequency, completed visits, revenue, branch
 * activity, and peak booking periods."
 *
 * WHAT THIS CLASS ASSERTS, AND WHY IT ASSERTS IT THIS WAY.
 *
 * A report is not proven by checking that a number appeared. Any broken report
 * produces numbers. It is proven by checking that the numbers AGREE WITH EACH
 * OTHER and that the report says what it counted - because those are the two
 * things a reader cannot verify for themselves from a printed page.
 *
 * So these tests are deliberately INVARIANT tests rather than fixture tests.
 * They assert properties that must hold whatever is in the database: that the
 * per-service figures add up to the headline, that the branch figures add up to
 * the same headline, that no month is silently missing from the range, and that
 * a tie is never reported as a winner. An invariant that holds on every data set
 * is a stronger claim than an equality that holds on one.
 *
 * ISO/IEC 25010 characteristics exercised:
 *   Functional correctness      - the arithmetic agrees with itself
 *   Functional completeness     - every month in range is represented
 *   Functional appropriateness  - the report states its own basis
 *   Security / confidentiality  - a client cannot read management figures
 *   Security / integrity        - a staff account cannot widen its own scope
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ObjectiveThreeReportingTest {

    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper json;

    private TestSupport api() { return new TestSupport(mvc, json); }

    /** A window wide enough to hold the seeded history. */
    private static final LocalDate FROM = LocalDate.now().minusMonths(5).withDayOfMonth(1);
    private static final LocalDate TO   = LocalDate.now();

    private String range() { return "?from=" + FROM + "&to=" + TO; }

    private static BigDecimal money(JsonNode n) {
        return n == null || n.isNull() ? BigDecimal.ZERO : new BigDecimal(n.asText());
    }

    // ------------------------------------------------- who may read figures

    @Test
    @DisplayName("SO3 - management figures are refused to a client")
    void aClientCannotReadTheReports() throws Exception {
        TestSupport api = api();
        int status = api.getRaw(api.tokenFor(TestSupport.ANA), "/api/v1/reports" + range())
                .getResponse().getStatus();

        // 401/403 both acceptable; 200 is not. Revenue and branch performance are
        // management information, and a client holding a valid token is still a
        // client.
        assertThat(status)
                .as("a customer token must not reach the reporting module")
                .isIn(401, 403);
    }

    @Test
    @DisplayName("SO3 - branch staff and administrators can both generate a report")
    void staffAndAdminCanGenerateAReport() throws Exception {
        TestSupport api = api();
        for (String who : new String[] { TestSupport.STAFF_BULAN, TestSupport.ADMIN }) {
            int status = api.getRaw(api.tokenFor(who), "/api/v1/reports" + range())
                    .getResponse().getStatus();
            assertThat(status).as("%s must be able to generate a report", who).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("SO3 - a staff account cannot widen its own scope by naming another branch")
    void staffScopeIsDecidedByTheServer() throws Exception {
        TestSupport api = api();
        String staff = api.tokenFor(TestSupport.STAFF_BULAN);
        String admin = api.tokenFor(TestSupport.ADMIN);

        String otherBranch = api.branchIdContaining(admin, "Sorsogon City");

        JsonNode asked = api.getAs(staff, "/api/v1/reports" + range() + "&branchId=" + otherBranch);

        // The branchId a staff account sends is IGNORED, not validated. A client
        // that can name its own branch is a client that can name somebody
        // else's, so the token decides and the parameter is discarded.
        assertThat(asked.get("branchName").asText())
                .as("a staff report must stay on the caller's own branch")
                .contains("Bulan");
    }

    @Test
    @DisplayName("SO3 - a staff report carries no branch comparison, an administrator's does")
    void theBranchTableIsForManagementOnly() throws Exception {
        TestSupport api = api();

        JsonNode staffReport = api.getAs(api.tokenFor(TestSupport.STAFF_BULAN),
                "/api/v1/reports" + range());
        assertThat(staffReport.get("branches").size())
                .as("a comparison of one branch is not a comparison")
                .isZero();

        // An administrator gets a row for every branch that HAS activity in the
        // range - not a row per branch that exists. On a quiet range that is
        // legitimately none, and asserting otherwise would be asserting that the
        // seed data has bookings rather than that the module behaves.
        JsonNode adminReport = api.getAs(api.tokenFor(TestSupport.ADMIN),
                "/api/v1/reports" + range());
        long counted = adminReport.get("visitsCounted").asLong();
        if (counted > 0) {
            assertThat(adminReport.get("branches").size())
                    .as("visits were counted, so some branch must account for them")
                    .isGreaterThanOrEqualTo(1);
            for (JsonNode b : adminReport.get("branches")) {
                assertThat(b.get("visits").asLong())
                        .as("a branch with no activity does not belong in the table")
                        .isPositive();
                assertThat(b.get("rank").asInt()).isPositive();
            }
        } else {
            assertThat(adminReport.get("branches").size())
                    .as("nothing was counted, so there is nothing to compare")
                    .isZero();
        }
    }

    @Test
    @DisplayName("SO3 - one new booking moves the counted figures by exactly one visit")
    void aBookingIsCountedExactlyOnce() throws Exception {
        TestSupport api = api();
        String ana   = api.tokenFor(TestSupport.ANA);
        String admin = api.tokenFor(TestSupport.ADMIN);

        // A window starting today, so the booking we are about to make falls
        // inside it. It also cannot contain a COMPLETED visit, because complete()
        // refuses a visit that has not started - which pins the basis to BOOKED
        // and makes the comparison below mean one thing only.
        String window = "?from=" + LocalDate.now() + "&to=" + LocalDate.now().plusMonths(2);

        JsonNode before = api.getAs(admin, "/api/v1/reports" + window);
        long beforeVisits = before.get("visitsCounted").asLong();

        String branch = api.branchIdContaining(ana, "Bulan");
        String serviceId = api.serviceIdNamed(ana, "Signature Massage", 60);
        String formId = api.createForm(ana, branch, "LOWER_BACK_PAIN", "LOWER_BACK_PAIN")
                .get("id").asText();

        JsonNode slots = api.getAs(ana, "/api/v1/appointments/availability?formId=" + formId
                + "&serviceId=" + serviceId).get("slots");
        assertThat(slots).as("the branch must have an open time, or nothing can be counted")
                .isNotEmpty();
        String start = slots.get(0).get("start").asText();

        int created = api.postRaw(ana, "/api/v1/appointments", """
                {"formId":"%s","serviceId":"%s","start":"%s",
                 "idempotencyKey":"so3-counts-once","consentText":"Yes please."}
                """.formatted(formId, serviceId, start)).getResponse().getStatus();
        assertThat(created).isEqualTo(201);

        JsonNode after = api.getAs(admin, "/api/v1/reports" + window);

        assertThat(after.get("basis").asText())
                .as("a wholly future window cannot contain a completed visit")
                .isEqualTo("BOOKED");
        assertThat(after.get("visitsCounted").asLong())
                .as("one booking is one visit - not none, and not two")
                .isEqualTo(beforeVisits + 1);
        assertThat(after.get("branches").size())
                .as("a counted visit must appear under the branch that took it")
                .isGreaterThanOrEqualTo(1);

        long perService = 0;
        for (JsonNode s : after.get("services")) { perService += s.get("visits").asLong(); }
        assertThat(perService)
                .as("the treatments table must still reconcile after the new booking")
                .isEqualTo(after.get("visitsCounted").asLong());
    }

    // ------------------------------------------- the figures agree with each other

    @Test
    @DisplayName("SO3 - service frequency sums exactly to the headline visit count")
    void serviceFrequencyAddsUpToTheHeadline() throws Exception {
        JsonNode r = api().getAs(api().tokenFor(TestSupport.ADMIN), "/api/v1/reports" + range());

        long summed = 0;
        for (JsonNode s : r.get("services")) { summed += s.get("visits").asLong(); }

        assertThat(summed)
                .as("the treatments table and the headline must be the same number "
                  + "counted two ways")
                .isEqualTo(r.get("visitsCounted").asLong());
    }

    @Test
    @DisplayName("SO3 - revenue sums exactly to the headline revenue")
    void revenueAddsUpToTheHeadline() throws Exception {
        JsonNode r = api().getAs(api().tokenFor(TestSupport.ADMIN), "/api/v1/reports" + range());

        BigDecimal summed = BigDecimal.ZERO;
        for (JsonNode s : r.get("services")) { summed = summed.add(money(s.get("revenue"))); }

        // compareTo, not equals: 0 and 0.00 are the same amount of money and
        // different BigDecimals.
        assertThat(summed.compareTo(money(r.get("revenueCounted"))))
                .as("per-treatment revenue must reconcile to the total")
                .isZero();
    }

    @Test
    @DisplayName("SO3 - branch activity sums exactly to the headline visit count")
    void branchActivityAddsUpToTheHeadline() throws Exception {
        JsonNode r = api().getAs(api().tokenFor(TestSupport.ADMIN), "/api/v1/reports" + range());

        long summed = 0;
        for (JsonNode b : r.get("branches")) { summed += b.get("visits").asLong(); }

        // Three independent routes to one number - services, branches, headline.
        // A visit filed against no branch, or against two, breaks exactly here.
        assertThat(summed)
                .as("every counted visit belongs to exactly one branch")
                .isEqualTo(r.get("visitsCounted").asLong());
    }

    @Test
    @DisplayName("SO3 - every share is a percentage and the shares are not inflated")
    void sharesAreCredible() throws Exception {
        JsonNode r = api().getAs(api().tokenFor(TestSupport.ADMIN), "/api/v1/reports" + range());

        int total = 0;
        for (JsonNode s : r.get("services")) {
            int pct = s.get("pct").asInt();
            assertThat(pct).as("a share outside 0-100 is not a share").isBetween(0, 100);
            total += pct;
        }
        if (r.get("visitsCounted").asLong() > 0) {
            // Shares are rounded per row, so they sum to ABOUT 100. Forcing them
            // to exactly 100 would mean moving a visit from one treatment to
            // another, which is a worse lie than a rounding gap.
            assertThat(total).as("rounded shares should land near 100")
                    .isBetween(100 - r.get("services").size(), 100 + r.get("services").size());
        }
    }

    // ---------------------------------------------------- peak booking periods

    @Test
    @DisplayName("SO3 - every month in the requested range is present, including empty ones")
    void noMonthIsSilentlyMissing() throws Exception {
        JsonNode r = api().getAs(api().tokenFor(TestSupport.ADMIN), "/api/v1/reports" + range());

        long expected = YearMonth.from(FROM).until(YearMonth.from(TO), java.time.temporal.ChronoUnit.MONTHS) + 1;
        assertThat(r.get("months").size())
                .as("a closed month that vanishes looks like a busy one that fell off the chart")
                .isEqualTo((int) expected);

        // And they must be in order, with no gaps.
        YearMonth cursor = YearMonth.from(FROM);
        for (JsonNode m : r.get("months")) {
            assertThat(m.get("month").asText()).isEqualTo(cursor.toString());
            cursor = cursor.plusMonths(1);
        }
    }

    @Test
    @DisplayName("SO3 - a peak month is only declared when one month actually leads")
    void aTieIsNeverReportedAsAWinner() throws Exception {
        JsonNode r = api().getAs(api().tokenFor(TestSupport.ADMIN), "/api/v1/reports" + range());

        JsonNode peak = r.get("peakMonth");
        JsonNode note = r.get("peakNote");

        boolean hasPeak = peak != null && !peak.isNull();
        boolean hasNote = note != null && !note.isNull() && !note.asText().isBlank();

        assertThat(hasPeak || hasNote)
                .as("the reader must be told either which month led, or why none did")
                .isTrue();
        assertThat(hasPeak && hasNote)
                .as("a peak and an explanation for having no peak are mutually exclusive")
                .isFalse();

        if (hasPeak) {
            long best = 0;
            int atBest = 0;
            for (JsonNode m : r.get("months")) {
                long v = m.get("visits").asLong();
                if (v > best) { best = v; atBest = 1; } else if (v == best) { atBest++; }
            }
            assertThat(peak.get("visits").asLong())
                    .as("the declared peak must be the busiest month")
                    .isEqualTo(best);
            assertThat(atBest).as("a declared peak must be unique").isEqualTo(1);
        }
    }

    // ------------------------------------------- the report explains itself

    @Test
    @DisplayName("SO3 - the report states what it counted, so a printed figure can be checked")
    void theReportIsSelfDescribing() throws Exception {
        JsonNode r = api().getAs(api().tokenFor(TestSupport.ADMIN), "/api/v1/reports" + range());

        assertThat(r.get("basis").asText())
                .as("COMPLETED and BOOKED are different claims about the business")
                .isIn("COMPLETED", "BOOKED");
        assertThat(r.get("countedNote").asText())
                .as("a figure in an appendix with no statement of what it counted "
                  + "cannot be checked by anybody")
                .isNotBlank();
        assertThat(r.get("from").asText()).isEqualTo(FROM.toString());
        assertThat(r.get("to").asText()).isEqualTo(TO.toString());
        assertThat(r.get("generatedAt").asText()).isNotBlank();
    }

    @Test
    @DisplayName("SO3 - a range of one month returns exactly one month")
    void anExplicitRangeIsHonoured() throws Exception {
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        JsonNode r = api().getAs(api().tokenFor(TestSupport.ADMIN),
                "/api/v1/reports?from=" + first + "&to=" + LocalDate.now());

        assertThat(r.get("months").size())
                .as("the range the caller asked for is the range they must get")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("SO3 - a branch that does not exist is refused, not quietly ignored")
    void anUnknownBranchIsRefused() throws Exception {
        TestSupport api = api();
        int status = api.getRaw(api.tokenFor(TestSupport.ADMIN),
                "/api/v1/reports" + range() + "&branchId=" + java.util.UUID.randomUUID())
                .getResponse().getStatus();

        // Silently reporting on everything would hand back a figure the caller
        // believes is about one branch.
        assertThat(status).as("an unknown branch must not fall back to all branches")
                .isEqualTo(404);
    }
}
