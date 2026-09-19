-- V9 - the node registry (task 3.2).
--
-- Who the peers are, whether they answered, and how far their log had got the
-- last time they did. One row per node INCLUDING this one: the admin screen
-- shows them side by side, and a self row that is special-cased in three places
-- is a self row that gets forgotten in the fourth.
--
-- node_id is the primary key rather than a surrogate uuid. It is already the
-- identifier stamped on every write in sync_log and on every audit row, and a
-- second identity for the same thing would need reconciling.

CREATE TABLE IF NOT EXISTS node (
    node_id         varchar(255) PRIMARY KEY,
    name            varchar(255),

    -- Where to reach it. Null for this node - it does not call itself.
    base_url        varchar(500),

    -- The branch this node OWNS, in the single-writer-per-partition sense.
    branch_id       uuid,

    is_self         boolean      NOT NULL DEFAULT false,

    -- UNKNOWN until first asked, then ONLINE or UNREACHABLE.
    state           varchar(16)  NOT NULL DEFAULT 'UNKNOWN',

    -- When it last ANSWERED, not when it was last tried. "Last seen 14:02" has
    -- to mean it was really there at 14:02.
    last_seen_at    timestamp,

    -- What it reported its own log had reached. Stage 3 reads this to decide
    -- whether there is anything worth fetching.
    their_watermark bigint,

    -- How far WE have consumed of that node's log. Stage 3 advances it; stage 2
    -- creates it so the column does not have to be added under a running
    -- system later.
    our_watermark   bigint       NOT NULL DEFAULT 0,

    -- Why it failed, verbatim, for the screen. A state with no reason sends
    -- somebody to read container logs on a machine they may not have.
    last_error      varchar(500),

    created_at      timestamp    NOT NULL DEFAULT now()
);

-- A peer row is created from its URL before its identity is known, so the URL
-- has to be the thing that is unique - otherwise a restart adds a second row
-- for the same machine every time it is unreachable at boot.
CREATE UNIQUE INDEX IF NOT EXISTS node_base_url_key
    ON node (base_url) WHERE base_url IS NOT NULL;
