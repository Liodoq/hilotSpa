-- V8 - the sync log (task 3.1).
--
-- An append-only record of every write this node makes to a replicated entity.
-- It is the substrate the node registry (3.2) and pull-based gossip (3.3) are
-- built on, and it is useful on its own before either exists.
--
-- WHAT IT HOLDS, AND WHAT IT DELIBERATELY DOES NOT
--
-- A row says THAT something changed - type, id, and when - and nothing about
-- WHAT it now says. A peer that wants the new state asks for it by id.
--
-- That is not laziness, it is the privacy boundary. This log is the thing a
-- peer is allowed to read. A payload column would put a patient's assessment
-- into a table whose whole purpose is being served to another branch, and no
-- amount of care further up would take it back out. Notifying that form
-- 3f2e... changed leaks nothing; serving its contents is a separate decision,
-- made at a separate endpoint, with its own rules.
--
-- ORDERING
--
-- `id` is a bigserial and therefore monotonic WITHIN this node. That is all a
-- watermark needs: a peer remembers the last id it saw from this node and asks
-- for what came after. There is no global clock and no attempt to invent one -
-- single-writer-per-partition means two nodes never write the same row, so
-- their logs never need interleaving.

CREATE TABLE IF NOT EXISTS sync_log (
    id              bigserial PRIMARY KEY,

    -- Which node made the change. A peer's rows are stored here too once
    -- gossip exists, so this is not always THIS node.
    origin_node_id  varchar(255) NOT NULL,

    entity_type     varchar(64)  NOT NULL,
    entity_id       uuid         NOT NULL,

    -- UPSERT covers insert and update on purpose. A peer holding a read-only
    -- replica does the same thing for both, and distinguishing them would
    -- invite a peer to apply an update to a row it never received.
    action          varchar(16)  NOT NULL,

    -- The partition this change belongs to, when it belongs to one. Null for
    -- globally-owned rows such as the service menu and the protocol table.
    branch_id       uuid,

    occurred_at     timestamp    NOT NULL DEFAULT now()
);

-- The only query gossip makes: "everything from node X after id N, in order".
CREATE INDEX IF NOT EXISTS ix_sync_log_origin_id
    ON sync_log (origin_node_id, id);

-- Answering "what happened to this booking" without scanning the whole log.
CREATE INDEX IF NOT EXISTS ix_sync_log_entity
    ON sync_log (entity_type, entity_id);
