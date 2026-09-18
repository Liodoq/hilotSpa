package com.hilotspa.backend.entities;

/**
 * What a notification was FOR.
 *
 * Stored as a string, like every other enum here, so adding a kind later is a
 * code change and not a migration. The day-before reminder is the only one the
 * adviser asked for; the enum exists so that the second one - a cancellation
 * notice, a follow-up - does not have to reopen this table.
 */
public enum NotificationKind {
    /** Sent the day before a visit. One per appointment, ever. */
    REMINDER_DAY_BEFORE
}
