package com.hilotspa.backend.services;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * A short-lived hold on a proposed time (task 2.1).
 *
 * THE GAP THIS FILLS, precisely.
 *
 * `V2__no_overlapping_appointments.sql` puts EXCLUDE constraints on the
 * appointment table, so two clients cannot end up with the same therapist at
 * the same hour. That guarantee is absolute and nothing here weakens it.
 *
 * But it only applies once a ROW EXISTS. The assistant settles a time, and the
 * client then chooses a therapist and a room - and for those minutes there is
 * no row, nothing to constrain, and the slot is open to everyone. Meanwhile the
 * assistant has been saying "na-hold na po ang time". AssistantServiceImpl even
 * carries a claimsHold() regex whose whole job is to notice the model saying
 * that. The sentence was true of nothing.
 *
 * THIS IS NOT THE BOOKING LOCK. It never gates the write. A hold that has
 * expired, or was never placed, costs the client the slot to a faster booker -
 * which is exactly what happens today, every time. The constraint still decides.
 *
 * DEGRADING IS THE DESIGN. Every method swallows its failure and returns the
 * answer that lets booking proceed: hold() reports success it did not achieve,
 * heldByOthers() reports none. With Redis down the system behaves precisely as
 * it did before this class existed. A branch must never stop taking bookings
 * because an optimisation is unreachable - that is NFR#1, and it is the reason
 * the hold sits on top of the constraint rather than in front of it.
 *
 * SHAPE. One sorted set per (branch, start): member = formId, score = expiry.
 * Reads prune what has expired before counting, so there is no cleanup job and
 * no KEYS scan. Redis TTLs alone could not do this - a set member cannot carry
 * its own expiry - and counting individual keys would mean scanning.
 */
@Service
public class SlotHoldService {

    private static final Logger LOG = LoggerFactory.getLogger(SlotHoldService.class);

    /**
     * Optional on purpose. The starter builds a template whether or not Redis
     * is actually reachable, and a checkout with no Redis at all must still
     * boot - so absence is a state this class handles, not an error it reports.
     */
    @Autowired private ObjectProvider<StringRedisTemplate> redisProvider;

    @Value("${hilotspa.hold.enabled:true}")     private boolean enabled;
    @Value("${hilotspa.hold.ttl-seconds:300}")  private long ttlSeconds;

    /**
     * So a broken Redis is said ONCE, loudly, and then shuts up.
     *
     * Every failure here was logged at DEBUG, on the reasoning that a hold is
     * optional and its loss must not alarm anybody. That reasoning produced
     * exactly the bug it was meant to tolerate: REDIS_HOST was `localhost`
     * inside the container, every hold was a refused connection, and the system
     * looked like it was working perfectly while doing nothing at all.
     *
     * B60's lesson, again: a fallback that cannot distinguish "not attempted"
     * from "failed" hides the failures you most need to see. WARN on the first
     * one so a misconfiguration is visible; DEBUG thereafter so a genuinely
     * absent Redis does not fill the log.
     */
    private final java.util.concurrent.atomic.AtomicBoolean complained =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private void trouble(String what, Exception e) {
        if (complained.compareAndSet(false, true)) {
            LOG.warn("Slot holds are NOT working - {} failed: {}. Bookings are unaffected; "
                   + "clients simply race for a time as they did before holds existed. "
                   + "Check spring.data.redis.host resolves to the Redis container.",
                    what, e.toString());
        } else {
            LOG.debug("hold {} failed: {}", what, e.toString());
        }
    }

    /** How long a settled time stays held. Shown to the client, so it is read. */
    public long ttlSeconds() {
        return ttlSeconds;
    }

    /**
     * Hold this time for this assessment.
     *
     * @return when the hold is in place, AND when it could not be attempted.
     *         The caller cannot act differently on the two and must not refuse
     *         a booking because of the second.
     */
    public boolean hold(UUID branchId, LocalDateTime start, UUID formId) {
        StringRedisTemplate redis = template();
        if (redis == null || branchId == null || start == null || formId == null) {
            return true;
        }
        try {
            String key = key(branchId, start);
            long now = System.currentTimeMillis() / 1000L;
            redis.opsForZSet().add(key, formId.toString(), (double) (now + ttlSeconds));
            // Housekeeping only. The scores are what expire a hold; this stops
            // a key for a long-past hour living forever after everyone left.
            redis.expire(key, Duration.ofSeconds(ttlSeconds + 60));
            return true;
        } catch (Exception e) {
            trouble("placing a hold", e);
            return true;
        }
    }

    /**
     * How many OTHER assessments are holding this time.
     *
     * Counted, not boolean, because the branch has several therapists: two
     * holds at 10:00 leave a third client able to book if a third therapist is
     * free. A boolean would empty the calendar for everyone the moment one
     * person hesitated.
     *
     * @param formId the asker, excluded from the count - a client must never be
     *               blocked by their own hold, which is the state they are in
     *               for the whole of the confirm step.
     */
    public int heldByOthers(UUID branchId, LocalDateTime start, UUID formId) {
        StringRedisTemplate redis = template();
        if (redis == null || branchId == null || start == null) {
            return 0;
        }
        try {
            String key = key(branchId, start);
            long now = System.currentTimeMillis() / 1000L;
            // Prune first. Expiry lives in the score, so an unpruned read would
            // count holds that lapsed minutes ago.
            redis.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, (double) now);

            Long total = redis.opsForZSet().zCard(key);
            if (total == null || total == 0) {
                return 0;
            }
            if (formId != null) {
                Double mine = redis.opsForZSet().score(key, formId.toString());
                if (mine != null) {
                    return (int) (total - 1);
                }
            }
            return total.intValue();
        } catch (Exception e) {
            trouble("counting holds", e);
            return 0;
        }
    }

    /** Let a time go: the visit was written, or the client chose another hour. */
    public void release(UUID branchId, LocalDateTime start, UUID formId) {
        StringRedisTemplate redis = template();
        if (redis == null || branchId == null || start == null || formId == null) {
            return;
        }
        try {
            redis.opsForZSet().remove(key(branchId, start), formId.toString());
        } catch (Exception e) {
            trouble("releasing a hold", e);
        }
    }

    /**
     * Drop every hold this assessment has, wherever it put them.
     *
     * Called when a visit is finally written. A client who was offered 10:00,
     * then 11:00, then booked 14:00 is otherwise still holding two hours they
     * have no intention of taking, for the rest of the TTL.
     */
    public void releaseAll(UUID branchId, Set<LocalDateTime> starts, UUID formId) {
        if (starts == null) {
            return;
        }
        for (LocalDateTime s : starts) {
            release(branchId, s, formId);
        }
    }

    private StringRedisTemplate template() {
        return enabled ? redisProvider.getIfAvailable() : null;
    }

    /** One sorted set per branch and hour. Minute precision; seconds never vary. */
    private static String key(UUID branchId, LocalDateTime start) {
        return "hold:" + branchId + ":" + start.withSecond(0).withNano(0);
    }
}
