-- V7 - the notification log (adviser's revision: remind the client the day before).
--
-- One row per notification this system TRIED to send. The row is written before
-- the mail server is called, so a send that hangs leaves evidence behind rather
-- than nothing at all.
--
-- The unique constraint is the point of the table, not decoration: the reminder
-- job claims an appointment by inserting here, so two overlapping runs cannot
-- both send. Drop the constraint and the job silently gains the ability to mail
-- a client twice.

CREATE TABLE IF NOT EXISTS notification_log (
    id              uuid PRIMARY KEY,
    appointment_id  uuid        NOT NULL REFERENCES appointment (id) ON DELETE CASCADE,
    kind            varchar(64) NOT NULL,
    channel         varchar(32) NOT NULL,
    status          varchar(32) NOT NULL,
    recipient       varchar(320),
    attempts        integer     NOT NULL DEFAULT 0,
    detail          varchar(500),
    origin_node_id  varchar(255) NOT NULL,
    created_at      timestamp   NOT NULL DEFAULT now(),
    sent_at         timestamp
);

-- The lock. One reminder of one kind per appointment, enforced by the database
-- rather than by the job remembering what it did.
ALTER TABLE notification_log
    DROP CONSTRAINT IF EXISTS uk_notification_appointment_kind;
ALTER TABLE notification_log
    ADD CONSTRAINT uk_notification_appointment_kind UNIQUE (appointment_id, kind);

-- The admin list reads this table newest-first and nothing else does, so this is
-- the only index it needs.
CREATE INDEX IF NOT EXISTS ix_notification_log_created
    ON notification_log (created_at DESC);
