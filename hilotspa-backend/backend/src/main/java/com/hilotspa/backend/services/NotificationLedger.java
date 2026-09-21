package com.hilotspa.backend.services;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.hilotspa.backend.entities.Appointment;
import com.hilotspa.backend.entities.NotificationChannel;
import com.hilotspa.backend.entities.NotificationKind;
import com.hilotspa.backend.entities.NotificationLog;
import com.hilotspa.backend.entities.NotificationStatus;
import com.hilotspa.backend.repository.NotificationLogRepository;

/**
 * The two writes the reminder job makes, in their own transactions.
 *
 * This is a SEPARATE BEAN and that is the whole reason it exists. These methods
 * were originally protected methods on ReminderServiceImpl, which does not
 * work: Spring's @Transactional is applied by a proxy around the bean, and a
 * call from one method of a class to another goes straight down the inside and
 * never touches the proxy. REQUIRES_NEW would have been silently ignored, the
 * claim would have stayed inside the caller's transaction, and two overlapping
 * runs would each have seen an empty table and each sent the client an email.
 *
 * The bug would not have shown up in any single-run test. It shows up the first
 * time a run is slow enough to overlap the next one - which is to say, in front
 * of the spa.
 */
@Component
public class NotificationLedger {

    @Autowired private NotificationLogRepository notificationLogRepository;

    @Value("${hilotspa.booking.timezone:Asia/Manila}") private String timezone;
    @Value("${hilotspa.node.id:local-dev}")           private String nodeId;

    /**
     * Claim this appointment by writing the row.
     *
     * Committed before the mail server is called, so the claim is visible to a
     * concurrent run. If that run got here first the unique constraint on
     * (appointment, kind) rejects this insert, which is the caller's signal to
     * do nothing rather than send a second email.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationLog claim(Appointment a, NotificationKind kind,
                                 NotificationLog prior, String recipient) {
        NotificationLog row = prior != null ? prior : new NotificationLog();
        row.setAppointment(a);
        row.setKind(kind);
        row.setChannel(NotificationChannel.EMAIL);
        row.setStatus(NotificationStatus.SENDING);
        row.setRecipient(recipient);
        row.setAttempts(row.getAttempts() + 1);
        row.setDetail(null);
        row.setSentAt(null);
        row.setOriginNodeId(nodeId);
        return notificationLogRepository.save(row);
    }

    /** Record what became of it. sentAt is set on SENT and on nothing else. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(NotificationLog row, NotificationStatus status, String detail) {
        row.setStatus(status);
        row.setDetail(detail == null ? null : clip(detail, 500));
        row.setSentAt(status == NotificationStatus.SENT
                ? LocalDateTime.now(ZoneId.of(timezone)) : null);
        notificationLogRepository.save(row);
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
