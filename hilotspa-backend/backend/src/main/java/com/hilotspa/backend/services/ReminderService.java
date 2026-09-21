package com.hilotspa.backend.services;

import java.time.LocalDate;

import com.hilotspa.backend.entities.NotificationKind;

/** The two reminders: roughly a day before a visit, and roughly an hour before. */
public interface ReminderService {

    /**
     * Send the day-before reminder for every live visit on {@code visitDay}.
     *
     * Kept after 3.38 made the reminders time-driven, because it answers a
     * different question: the sweep asks "which visits are due a reminder
     * now?", and this asks "send Thursday's, now, because I say so". That is
     * what makes the feature demonstrable in front of a panel without waiting
     * for a clock, and it is what the administrator's Send button calls.
     *
     * @param branchIds the branches to remind for. Null or empty means every
     *                  branch this node holds - which only an administrator may
     *                  ask for.
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

    /**
     * The time-driven sweep (3.38). Sends whatever is due right now.
     *
     * @param kind which of the two reminders to consider.
     * @return how many were sent on this call.
     */
    int sweep(NotificationKind kind);
}
