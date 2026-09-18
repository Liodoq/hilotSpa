package com.hilotspa.backend.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A3 - the administrator's reports.
 *
 * Two questions the spa actually asks: which treatment is availed most, and
 * which month is busiest. Both are counted from the appointment table at
 * request time, like every other figure this system shows - nothing is stored,
 * rolled up or estimated, so a panelist can put the number on screen and the
 * same number out of psql and watch them agree.
 *
 * The thing this file is most careful about is saying WHAT WAS COUNTED. A
 * ranking of treatments is worthless if the reader cannot tell whether a
 * cancelled booking is in it, and "most availed" and "most booked" are not the
 * same claim - people cancel more of some treatments than others.
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    /**
     * What the ranking counted.
     *
     * COMPLETED is the honest basis: a visit that was availed is one the client
     * turned up for. BOOKED exists because a spa that has not yet got into the
     * habit of closing visits off would otherwise open this page onto a table of
     * zeros, which reads as a broken report rather than as an unused feature.
     * The server picks, and the screen always says which it picked.
     */
    public enum Basis { COMPLETED, BOOKED }

    /** One treatment's line in the ranking. */
    public record ServiceRow(
            UUID serviceId,
            String name,
            long visits,
            /** Share of the visits counted, rounded. Sums to ~100, not exactly. */
            int pct,
            /**
             * Sum of priceAtBooking, not of today's price.
             *
             * A service whose price changed in June would otherwise have its
             * January visits re-valued at the new rate, and the total would
             * disagree with what the counter actually took.
             */
            BigDecimal revenue) {
    }

    /**
     * One branch's line in the comparison, ranked against the others.
     *
     * Only ever more than one row for an administrator. A staff account's report
     * carries no branch table at all, because a comparison of one is not a
     * comparison, and showing a branch its own number under the heading
     * "Branches" invites the reader to wonder what happened to the other one.
     */
    public record BranchRow(
            UUID branchId,
            String name,
            /**
             * The node that WROTE these bookings, read from the rows.
             *
             * Not the node answering this request. Several when a branch's
             * history spans a move; blank when there is nothing in range.
             */
            String nodeId,
            long visits,
            int pct,
            BigDecimal revenue,
            /** 1 is busiest. Ties share a rank. */
            int rank) {
    }

    /** One month's total. Present even when zero, so a gap reads as a gap. */
    public record MonthRow(
            /** Sortable key, e.g. "2026-09". */
            String month,
            /** What a person reads, e.g. "Sep 2026". */
            String label,
            long visits,
            BigDecimal revenue,
            /** True for the busiest month in the range, and for every month tying it. */
            boolean peak) {
    }

    public record Reports(
            LocalDateTime generatedAt,
            LocalDate from,
            /** Inclusive, unlike the half-open window the query actually uses. */
            LocalDate to,
            /** Null means every branch. */
            UUID branchId,
            String branchName,
            Basis basis,
            /**
             * The basis in words, for the screen to print verbatim.
             *
             * The report has to be able to explain itself on paper, away from
             * whoever generated it - a figure in a thesis appendix with no
             * statement of what it counted cannot be checked by anybody.
             */
            String countedNote,
            long visitsCounted,
            BigDecimal revenueCounted,
            List<ServiceRow> services,
            List<MonthRow> months,
            /**
             * Branch against branch, busiest first. EMPTY for a staff account
             * and for a single-branch spa - see BranchRow.
             */
            List<BranchRow> branches,
            /**
             * The single busiest month, or null when the range is empty or two
             * months tie. A tie reported as a winner is a lie the reader has no
             * way to catch.
             */
            MonthRow peakMonth,
            /** Set when there is no peak, saying why. Null when peakMonth is set. */
            String peakNote) {
    }
}
