package com.hilotspa.backend.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * One row per notification this system tried to send.
 *
 * TRIED, not sent. The row is written before the mail server is called and
 * updated afterwards, so a send that hangs leaves a SENDING row behind rather
 * than no row at all. A log that only records successes cannot answer the one
 * question anybody ever asks it - "I never got a reminder, did you send one?" -
 * because silence and failure look identical in it.
 *
 * The unique constraint on (appointment, kind) is not tidiness. It is the lock:
 * the reminder job claims an appointment by inserting this row, and a second
 * run that overlaps the first loses the insert instead of sending a duplicate.
 * That is why the job does not need to remember anything between runs, and why
 * restarting the backend mid-run cannot double-send.
 */
@Data
@Entity
@Table(name = "notification_log",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_notification_appointment_kind",
           columnNames = { "appointment_id", "kind" }))
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private NotificationKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationChannel channel = NotificationChannel.EMAIL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationStatus status = NotificationStatus.SENDING;

    /**
     * The address it went to, recorded as it was at the time.
     *
     * Deliberately copied rather than read back through the appointment: a
     * client who changes their email next month must not silently rewrite the
     * history of where last month's reminder was actually delivered.
     */
    @Column(length = 320)
    private String recipient;

    /** How many times the mail server has been asked. Caps the retrying. */
    @Column(nullable = false)
    private int attempts = 0;

    /** Why it was skipped, or what the mail server said when it refused. */
    @Column(length = 500)
    private String detail;

    /** Which node sent it. The second branch will have its own. */
    @Column(nullable = false)
    private String originNodeId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Set only on SENT. Null on every other status, on purpose. */
    @Column
    private LocalDateTime sentAt;
}
