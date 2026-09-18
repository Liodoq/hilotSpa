package com.hilotspa.backend.services;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.hilotspa.backend.config.SyncEvents.EntityWritten;
import com.hilotspa.backend.entities.SyncLog;
import com.hilotspa.backend.repository.SyncLogRepository;

/**
 * Writes the sync log (task 3.1).
 *
 * AFTER_COMMIT, in a new transaction. Both halves of that matter:
 *
 * - AFTER_COMMIT, because a rolled back write must leave no trace. A log
 *   claiming a change that was undone is worse than no log at all - a peer
 *   would fetch the row, find it unchanged, and conclude its watermark was
 *   broken rather than that the log was.
 *
 * - REQUIRES_NEW, because by the time this runs the original transaction is
 *   finished and there is nothing to join. Without it the write has no
 *   transaction and Hibernate refuses.
 *
 * A failure here is logged and swallowed. The visit is booked; the client has
 * been told so. Losing a replication notice degrades a peer's view until the
 * next full pull, which is a smaller harm than an exception thrown after the
 * transaction that mattered has already committed.
 */
@Component
public class SyncRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(SyncRecorder.class);

    @Autowired private SyncLogRepository syncLogRepository;

    @Value("${hilotspa.node.id:local-dev}") private String nodeId;
    @Value("${hilotspa.booking.timezone:Asia/Manila}") private String timezone;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(EntityWritten e) {
        try {
            SyncLog row = new SyncLog();
            row.setOriginNodeId(nodeId);
            row.setEntityType(e.entityType());
            row.setEntityId(e.entityId());
            row.setAction(e.action());
            row.setBranchId(e.branchId());
            row.setOccurredAt(LocalDateTime.now(ZoneId.of(timezone)));
            syncLogRepository.save(row);
        } catch (RuntimeException ex) {
            LOG.warn("sync_log write failed for {} {} - {}",
                    e.entityType(), e.entityId(), ex.toString());
        }
    }
}
