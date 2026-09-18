package com.hilotspa.backend.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hilotspa.backend.entities.AppointmentStatus;
import com.hilotspa.backend.entities.Branch;
import com.hilotspa.backend.config.CurrentUser;
import com.hilotspa.backend.model.ReportDtos.Basis;
import com.hilotspa.backend.model.ReportDtos.BranchRow;
import com.hilotspa.backend.model.ReportDtos.MonthRow;
import com.hilotspa.backend.model.ReportDtos.Reports;
import com.hilotspa.backend.model.ReportDtos.ServiceRow;
import com.hilotspa.backend.repository.AppointmentRepository;
import com.hilotspa.backend.repository.BranchRepository;

/**
 * A3 - most-availed treatment and peak month.
 *
 * The whole of the care in this class is in one question: what counts as a
 * visit? Get that wrong quietly and every figure on the page is wrong in a way
 * nobody can see. So the rule is decided in exactly one place (basisFor), it is
 * returned to the caller in words, and the screen prints those words.
 */
@Service
public class ReportServiceImpl implements ReportService {

    /** A visit that was actually availed. */
    private static final Set<AppointmentStatus> AVAILED =
            EnumSet.of(AppointmentStatus.COMPLETED);

    /**
     * A visit that was held on the calendar, whatever became of it afterwards.
     *
     * IN_PROGRESS is in here and COMPLETED is too: a booking that is running
     * right now was certainly booked. CANCELLED and NO_SHOW are not - a slot
     * that was released is not demand the spa served, and counting it would
     * flatter the busiest month with the visits that fell through.
     */
    private static final Set<AppointmentStatus> BOOKED = EnumSet.of(
            AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED,
            AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED);

    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private BranchRepository branchRepository;

    @Value("${hilotspa.booking.timezone:Asia/Manila}") private String timezone;

    /** How far back the report looks when the caller names no range. */
    @Value("${hilotspa.reports.default-months:12}") private int defaultMonths;

    @Override
    @Transactional(readOnly = true)
    public Reports reports(LocalDate from, LocalDate to, UUID branchId) {
        LocalDate today = LocalDate.now(ZoneId.of(timezone));

        LocalDate end = to == null ? today : to;
        LocalDate start = from == null
                ? end.minusMonths(defaultMonths).withDayOfMonth(1)
                : from;

        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The start of the range is after its end.");
        }

        // The DTO reports `to` inclusive because that is what a person means by
        // "up to the 30th". The query is half-open, like every other time
        // comparison in this system, so the two differ by one day on purpose.
        LocalDateTime windowStart = start.atStartOfDay();
        LocalDateTime windowEnd = end.plusDays(1).atStartOfDay();

        List<Branch> branches = branchRepository.findAll();
        if (branches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "There are no branches on file, so there is nothing to report on.");
        }

        // Who this report is allowed to be about.
        //
        // A STAFF caller's branch comes from their token and the branchId they
        // sent is IGNORED, not validated - a client that can name its own branch
        // is a client that can name someone else's, and every other staff route
        // in this system follows the same rule. Only an administrator's
        // parameter is honoured.
        UUID scope = CurrentUser.isAdmin()
                ? branchId
                : CurrentUser.branchId().orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Your account is not attached to a branch, so there is nothing to report on."));

        List<UUID> branchIds;
        String branchName;
        if (scope == null) {
            branchIds = branches.stream().map(Branch::getId).toList();
            branchName = branches.size() == 1
                    ? branches.get(0).getName()
                    : "All branches";
        } else {
            UUID want = scope;
            Branch one = branches.stream()
                    .filter(b -> want.equals(b.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Branch not found"));
            branchIds = List.of(one.getId());
            branchName = one.getName();
        }

        Basis basis = basisFor(branchIds, windowStart, windowEnd);
        Set<AppointmentStatus> statuses = basis == Basis.COMPLETED ? AVAILED : BOOKED;

        List<ServiceRow> services = services(statuses, branchIds, windowStart, windowEnd);
        long total = services.stream().mapToLong(ServiceRow::visits).sum();
        BigDecimal revenue = services.stream()
                .map(ServiceRow::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<MonthRow> months = months(statuses, branchIds, start, end);
        MonthRow peak = peakOf(months);

        // Branch against branch, but only when there is more than one branch in
        // scope. A comparison of one is not a comparison, and a staff account
        // shown its own number under a heading reading "Branches" is invited to
        // wonder what happened to the others.
        List<BranchRow> branchRows = branchIds.size() > 1
                ? branches(statuses, branchIds, windowStart, windowEnd)
                : List.of();

        return new Reports(
                LocalDateTime.now(ZoneId.of(timezone)),
                start, end,
                branchId, branchName,
                basis, note(basis, total),
                total, revenue,
                services, months, branchRows,
                peak, peak == null ? peakNote(months) : null);
    }

    /**
     * Completed if the spa has closed off any visit in this range, booked if it
     * has not.
     *
     * The fallback is not a convenience. A spa that has not got into the habit
     * of marking visits completed would otherwise open this page onto a table
     * of zeros, and a report that shows zero where the answer is "we have not
     * recorded it yet" is worse than no report - it is a wrong one.
     */
    private Basis basisFor(List<UUID> branchIds, LocalDateTime from, LocalDateTime to) {
        long completed = appointmentRepository.countInRange(AVAILED, branchIds, from, to);
        return completed > 0 ? Basis.COMPLETED : Basis.BOOKED;
    }

    private String note(Basis basis, long total) {
        if (total == 0) {
            return "No visits fall in this range at all, on any basis.";
        }
        if (basis == Basis.COMPLETED) {
            return "Counts visits marked completed. Cancelled visits, no-shows and "
                 + "visits the spa has not yet closed off are excluded.";
        }
        return "No visit in this range has been marked completed yet, so this counts "
             + "visits that were BOOKED and not cancelled. It is a measure of demand, "
             + "not of attendance.";
    }

    private List<ServiceRow> services(Set<AppointmentStatus> statuses, List<UUID> branchIds,
                                      LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = appointmentRepository.countByService(statuses, branchIds, from, to);
        long total = rows.stream().mapToLong(r -> ((Number) r[2]).longValue()).sum();

        List<ServiceRow> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            long visits = ((Number) r[2]).longValue();
            out.add(new ServiceRow(
                    (UUID) r[0],
                    (String) r[1],
                    visits,
                    // Rounded per row, so the column sums to about 100 and not
                    // exactly. Forcing it to 100 means moving a visit from one
                    // treatment to another, which is a worse lie than 99.
                    total == 0 ? 0 : (int) Math.round(visits * 100.0 / total),
                    toMoney(r[3])));
        }
        return out;
    }

    /**
     * Every month in the range, including the empty ones.
     *
     * A month with no visits has to appear as a zero. Dropping it makes a
     * closed August look like a busy one that merely fell off the chart, and
     * "peak month" is a claim about the months around it as much as itself.
     */
    private List<MonthRow> months(Set<AppointmentStatus> statuses, List<UUID> branchIds,
                                  LocalDate start, LocalDate end) {
        List<Object[]> rows = appointmentRepository.countByMonth(
                statuses, branchIds, start.atStartOfDay(), end.plusDays(1).atStartOfDay());

        java.util.Map<YearMonth, long[]> counted = new java.util.HashMap<>();
        java.util.Map<YearMonth, BigDecimal> earned = new java.util.HashMap<>();
        for (Object[] r : rows) {
            YearMonth ym = YearMonth.of(((Number) r[0]).intValue(), ((Number) r[1]).intValue());
            counted.put(ym, new long[] { ((Number) r[2]).longValue() });
            earned.put(ym, toMoney(r[3]));
        }

        long high = counted.values().stream().mapToLong(v -> v[0]).max().orElse(0);

        List<MonthRow> out = new ArrayList<>();
        YearMonth cursor = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        while (!cursor.isAfter(last)) {
            long visits = counted.containsKey(cursor) ? counted.get(cursor)[0] : 0;
            out.add(new MonthRow(
                    cursor.toString(),
                    cursor.atDay(1).format(MONTH_LABEL),
                    visits,
                    earned.getOrDefault(cursor, BigDecimal.ZERO),
                    high > 0 && visits == high));
            cursor = cursor.plusMonths(1);
        }
        return out;
    }

    /**
     * Branch against branch, busiest first.
     *
     * The node on each row is read from the BOOKINGS, not from this node's own
     * configuration. A branch labelled with whichever node happens to be
     * answering the request is a guess, and it is wrong in exactly the
     * situation two nodes exist for - an administrator in Bulan reading
     * Sorsogon's figures.
     */
    private List<BranchRow> branches(Set<AppointmentStatus> statuses, List<UUID> branchIds,
                                     LocalDateTime from, LocalDateTime to) {
        java.util.Map<UUID, java.util.TreeSet<String>> nodes = new java.util.HashMap<>();
        for (Object[] r : appointmentRepository.nodesByBranch(statuses, branchIds, from, to)) {
            nodes.computeIfAbsent((UUID) r[0], k -> new java.util.TreeSet<>())
                 .add(String.valueOf(r[1]));
        }

        List<Object[]> rows = appointmentRepository.countByBranch(statuses, branchIds, from, to);
        long total = rows.stream().mapToLong(r -> ((Number) r[2]).longValue()).sum();

        List<BranchRow> out = new ArrayList<>(rows.size());
        long previous = -1;
        int rank = 0;
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            long visits = ((Number) r[2]).longValue();
            // Ties share a rank. Two branches level on 40 visits are both 1st,
            // and calling one of them second because it sorted later is a claim
            // the data does not support.
            if (visits != previous) {
                rank = i + 1;
                previous = visits;
            }
            UUID id = (UUID) r[0];
            out.add(new BranchRow(
                    id,
                    (String) r[1],
                    String.join(", ", nodes.getOrDefault(id, new java.util.TreeSet<>())),
                    visits,
                    total == 0 ? 0 : (int) Math.round(visits * 100.0 / total),
                    toMoney(r[3]),
                    rank));
        }
        return out;
    }

    /** The one busiest month, or null when nothing happened or two months tie. */
    private MonthRow peakOf(List<MonthRow> months) {
        List<MonthRow> top = months.stream().filter(MonthRow::peak).toList();
        return top.size() == 1 ? top.get(0) : null;
    }

    private String peakNote(List<MonthRow> months) {
        List<MonthRow> top = months.stream().filter(MonthRow::peak).toList();
        if (top.isEmpty()) {
            return "No month in this range has a visit in it.";
        }
        String names = String.join(" and ", top.stream().map(MonthRow::label).toList());
        return "There is no single busiest month: " + names + " tie on "
             + top.get(0).visits() + " visits each.";
    }

    /** SUM() comes back as whatever the driver felt like. Pin it to money. */
    private static BigDecimal toMoney(Object raw) {
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal v = raw instanceof BigDecimal b ? b : new BigDecimal(raw.toString());
        return v.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
