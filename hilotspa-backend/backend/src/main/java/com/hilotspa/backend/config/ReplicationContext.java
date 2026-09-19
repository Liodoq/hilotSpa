package com.hilotspa.backend.config;

/**
 * "This write came from a peer, do not log it" (task 3.3).
 *
 * Applying a replicated change is still a write, so SyncAudited would record it
 * and this node would advertise it as its own. The peer would then fetch it
 * back, apply it, advertise it in turn, and the two would pass the same row
 * between them for ever - each one's watermark climbing, no new information
 * anywhere.
 *
 * A ThreadLocal rather than a flag on the entity: the suppression belongs to
 * the ACT of applying, not to the row. The same appointment written by a human
 * at the desk a minute later must be logged normally.
 *
 * Always in a try/finally. A flag left set would silently stop recording every
 * subsequent write on that thread, and the symptom - a peer that mysteriously
 * stops receiving updates - points nowhere near here.
 */
public final class ReplicationContext {

    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);

    private ReplicationContext() {
    }

    public static boolean isApplying() {
        return APPLYING.get();
    }

    public static void run(Runnable body) {
        APPLYING.set(true);
        try {
            body.run();
        } finally {
            APPLYING.remove();
        }
    }
}
