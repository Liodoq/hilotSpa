-- V10 - planned time off (task 3.33).
--
-- The system already had two controls and neither says "Angel is off next
-- Tuesday":
--
--   therapist.status  a RIGHT-NOW flag with no end time, set at the desk when
--                     someone goes on break. Honoured for today only, on
--                     purpose: applying it to the whole week would mean
--                     marking one person ON_BREAK also emptied next Tuesday.
--   therapist.active  they have LEFT the spa. Every day, for ever.
--
-- This is the missing middle: a dated range, in advance, that ends by itself.
--
-- Inclusive on both ends, because that is how a person says it. "Off from the
-- 20th to the 22nd" means three days, and a half-open range here would quietly
-- book them on the last one - the sort of off-by-one nobody finds until a
-- client is standing at the counter.

CREATE TABLE IF NOT EXISTS therapist_leave (
    id           uuid PRIMARY KEY,
    therapist_id uuid NOT NULL REFERENCES therapist (id) ON DELETE CASCADE,
    starts_on    date NOT NULL,
    ends_on      date NOT NULL,
    reason       varchar(255),
    created_at   timestamp NOT NULL DEFAULT now(),
    created_by   varchar(255),

    CONSTRAINT therapist_leave_ends_after_it_starts CHECK (ends_on >= starts_on)
);

-- The availability path asks "who is off on this date" for every day it draws.
CREATE INDEX IF NOT EXISTS therapist_leave_by_therapist
    ON therapist_leave (therapist_id, starts_on, ends_on);
