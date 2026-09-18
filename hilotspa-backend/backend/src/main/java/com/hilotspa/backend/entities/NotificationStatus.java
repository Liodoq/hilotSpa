package com.hilotspa.backend.entities;

/**
 * What became of one notification.
 *
 * SENDING exists because the row is written BEFORE the mail server is called,
 * not after. A row that only appears on success cannot tell you about the send
 * that hung, and "we think we sent it" is exactly the claim a client disputes.
 */
public enum NotificationStatus {
    /** Claimed, not yet answered for. A row stuck here means a send that died mid-flight. */
    SENDING,
    SENT,
    /** The mail server refused it. Retried on the next run, up to a limit. */
    FAILED,
    /** Nothing was attempted, and the detail says why. Never retried. */
    SKIPPED
}
