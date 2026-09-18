package com.hilotspa.backend.config;

import java.util.UUID;

import com.hilotspa.backend.config.SyncEvents.EntityWritten;
import com.hilotspa.backend.entities.Appointment;
import com.hilotspa.backend.entities.Branch;
import com.hilotspa.backend.entities.Massage;
import com.hilotspa.backend.entities.Room;
import com.hilotspa.backend.entities.ServiceProtocol;
import com.hilotspa.backend.entities.Therapist;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

/**
 * Records every write to a replicated entity (task 3.1).
 *
 * Attached with @EntityListeners, which makes the set of replicated entities an
 * EXPLICIT ALLOW-LIST: an entity is replicated because somebody annotated it,
 * never by default. That matters because the default here is a privacy
 * decision - Forms, PatientIntake, Demographics and User are deliberately NOT
 * annotated. A branch has no business knowing that another branch's patient
 * record changed, and the safe way to express that is for the log never to
 * learn it in the first place.
 *
 * Sitting at the persistence layer rather than in the services is the point.
 * "Every write is recorded because the recorder is below the code that writes"
 * is a claim that stays true as the system grows; "we remembered to call it
 * everywhere" is a claim that decays silently.
 */
public class SyncAudited {

    @PostPersist
    @PostUpdate
    public void written(Object entity) {
        emit(entity, "UPSERT");
    }

    @PostRemove
    public void removed(Object entity) {
        emit(entity, "DELETE");
    }

    private void emit(Object entity, String action) {
        UUID id = idOf(entity);
        if (id == null) {
            return;
        }
        SyncEvents.publish(new EntityWritten(
                entity.getClass().getSimpleName(), id, action, branchOf(entity)));
    }

    /**
     * The partition this row belongs to, or null when it belongs to none.
     *
     * Written as explicit cases rather than reflection over a "getBranch"
     * method: a silent null from a renamed getter would make a branch-owned
     * row look global, and a global row is one every node replicates.
     */
    private static UUID branchOf(Object e) {
        if (e instanceof Appointment a) {
            return a.getBranch() == null ? null : a.getBranch().getId();
        }
        if (e instanceof Therapist t) {
            return t.getBranch() == null ? null : t.getBranch().getId();
        }
        if (e instanceof Room r) {
            return r.getBranch() == null ? null : r.getBranch().getId();
        }
        if (e instanceof Branch b) {
            return b.getId();
        }
        // Massage and ServiceProtocol are global: one menu and one protocol
        // table for the whole business, which is what "global configuration
        // broadcast" (3.8) means.
        return null;
    }

    private static UUID idOf(Object e) {
        if (e instanceof Appointment a) { return a.getId(); }
        if (e instanceof Therapist t) { return t.getId(); }
        if (e instanceof Room r) { return r.getId(); }
        if (e instanceof Branch b) { return b.getId(); }
        if (e instanceof Massage m) { return m.getId(); }
        if (e instanceof ServiceProtocol p) { return p.getId(); }
        return null;
    }
}
