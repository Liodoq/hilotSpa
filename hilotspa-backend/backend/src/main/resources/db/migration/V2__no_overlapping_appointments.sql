-- Task 2.37 - the database refuses a double booking, not just the application.
--
-- WHY THIS AND NOT MORE JAVA
--
-- assign() already checks that a therapist and a room are free, inside the
-- transaction that writes the appointment. What it cannot do is make the check
-- and the write atomic against a concurrent transaction: under READ COMMITTED,
-- two requests four milliseconds apart can BOTH pass the existence check and
-- BOTH insert. Every integration test we have runs sequentially, so none of them
-- can catch it. No amount of application code fixes this; the guarantee has to
-- live where the serialisation happens.
--
-- WHY THIS IS ALSO THE SPRINT 3 ARGUMENT
--
-- A therapist and a room each belong to exactly one branch, so exactly one node
-- ever writes them. That is why a LOCAL constraint is sufficient and no
-- distributed consensus is required: there is no second writer to agree with.
-- Single-writer-per-partition stops being a claim in a paper and becomes a
-- constraint a panelist can read.
--
-- NOTES ON THE MECHANICS
--
--   btree_gist  - lets a gist index handle plain equality on a uuid alongside a
--                 range overlap. Without it these constraints cannot be created.
--   tsrange     - defaults to '[)', half-open, which is exactly the rule the
--                 application already applies: a 3 PM finish and a 3 PM start do
--                 not collide.
--   WHERE       - a partial constraint. CANCELLED, COMPLETED and NO_SHOW rows
--                 must not reserve anybody; this is what makes cancelling
--                 genuinely release the hour.

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE appointment
  DROP CONSTRAINT IF EXISTS therapist_not_double_booked;

ALTER TABLE appointment
  ADD CONSTRAINT therapist_not_double_booked
  EXCLUDE USING gist (
    therapist_id WITH =,
    tsrange(start_time, end_time) WITH &&
  ) WHERE (status IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS'));

ALTER TABLE appointment
  DROP CONSTRAINT IF EXISTS room_not_double_booked;

ALTER TABLE appointment
  ADD CONSTRAINT room_not_double_booked
  EXCLUDE USING gist (
    room_id WITH =,
    tsrange(start_time, end_time) WITH &&
  ) WHERE (status IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS'));
