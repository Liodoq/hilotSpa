package com.hilotspa.backend.entities;

/**
 * What a notification was FOR.
 *
 * Stored as a string, like every other enum here, so adding a kind is a code
 * change and not a migration. That property is what made 3.38 cheap: the second
 * reminder needed a new constant and nothing else, because the unique
 * constraint that stops a double send is on (appointment, KIND) and therefore
 * already counted the two reminders separately.
 */
public enum NotificationKind {

    /**
     * Sent when the visit is roughly a day away, measured from the visit's own
     * start time rather than from a fixed hour of the morning. One per
     * appointment, ever.
     */
    REMINDER_DAY_BEFORE,

    /**
     * Sent when the visit is roughly an hour away. One per appointment, ever.
     *
     * A separate constant rather than a flag on the first, because the unique
     * constraint is what enforces "once": two reminders sharing a kind would
     * mean the second one could never be written.
     */
    REMINDER_HOUR_BEFORE
}
