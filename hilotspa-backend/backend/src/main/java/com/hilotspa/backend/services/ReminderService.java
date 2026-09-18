package com.hilotspa.backend.services;

import java.time.LocalDate;

/** The day-before reminder (adviser's revision). */
public interface ReminderService {

    /**
     * Send the reminder for every live visit on {@code visitDay}.
     *
     * Separated from the schedule so it can be called for a named day - which
     * is what makes this demonstrable in front of a panel without waiting until
     * 9 AM tomorrow, and testable without a clock.
     *
     * @param branchIds the branches to remind for. Null or empty means every
     *                  branch this node holds - which is what the nightly run
     *                  passes, and what only an administrator may ask for.
     * @return how many were sent on this call. Already-sent visits count zero.
     */
    int remindFor(LocalDate visitDay, java.util.Collection<java.util.UUID> branchIds);

    /**
     * Send ONE client's reminder now, by hand.
     *
     * The case this exists for: a client rings to say they never got it. The
     * whole-day run cannot serve that - it would mail everybody booked that day
     * a second time - and the claim row would refuse a repeat anyway.
     *
     * So this one FORCES. It is an explicit human action with a name against
     * it, not the automatic job, and the row records the attempt count and who
     * asked. What it does not do is bypass the reasons a send is impossible: a
     * client with no address on file is still a SKIPPED row.
     *
     * @param by who pressed it, written into the row.
     * @return true when the mail server accepted it.
     */
    boolean remindOne(java.util.UUID appointmentId, String by);
}
