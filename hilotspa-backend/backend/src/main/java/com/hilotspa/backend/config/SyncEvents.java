package com.hilotspa.backend.config;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * The bridge between JPA's entity callbacks and Spring's event bus.
 *
 * JPA instantiates an @EntityListeners class itself, so it is not a Spring bean
 * and cannot be injected into. This holder is the standard way across that gap:
 * Spring populates the static field once at startup, and the listener reads it.
 *
 * A static field is a smell and this one is deliberate. The alternative is
 * calling the sync recorder explicitly from every service that writes - which
 * is seven files today and every future one, and the failure mode is a write
 * that silently never replicates. A missing call cannot be seen; it looks
 * exactly like a node that had nothing to say.
 */
@Component
public class SyncEvents {

    private static ApplicationEventPublisher publisher;

    @Autowired
    public SyncEvents(ApplicationEventPublisher publisher) {
        SyncEvents.publisher = publisher;
    }

    /**
     * One write, not yet committed.
     *
     * Published from the JPA callback and consumed AFTER COMMIT, so a rolled
     * back transaction leaves no trace. A log claiming a change that was undone
     * is worse than no log: a peer would fetch the row and find it unchanged,
     * and conclude its watermark was broken.
     */
    public record EntityWritten(String entityType, UUID entityId,
                                String action, UUID branchId) {
    }

    static void publish(EntityWritten e) {
        // Null during the tests that build an EntityManagerFactory without a
        // Spring context. Dropping the event is right there: there is no node
        // to replicate to either.
        if (publisher != null) {
            publisher.publishEvent(e);
        }
    }
}
