-- B85 - a walk-in can have a pre-assessment.
--
-- RUN THIS *AFTER* the rebuilt backend has booted once.
--
-- Order matters, and getting it wrong is how this file was first written:
--   1. `docker compose up -d --build backend`  -> Hibernate ddl-auto=update ADDS
--      the new nullable walk_in_name column. Adding a nullable column is the one
--      schema change it will make on a populated table.
--   2. this script                              -> does the two things it will NOT
--      do: relax an existing NOT NULL, and add a check constraint to a table that
--      already exists.
--
-- Run before step 1 and the constraint fails with
--   ERROR: column "walk_in_name" does not exist
-- which is harmless - the ALTER above it has already applied - but you must come
-- back and run the file again.
--
-- Every statement below is idempotent, so re-running this is always safe.
--
--   type db\2026-08-30-b85-walkin-assessment.sql | docker compose exec -T db psql -U postgres -d hilotspa_db
--
-- This is also the argument for task 0.7 / R12: under Flyway this would be a
-- numbered migration every node applies in the right order automatically,
-- instead of a step whose ordering someone has to remember.

-- Belt and braces: create the column ourselves if the app has not booted yet, so
-- this file works whichever order it is run in.
ALTER TABLE forms
  ADD COLUMN IF NOT EXISTS walk_in_name varchar(255);

-- A walk-in has no account. This is the whole bug.
ALTER TABLE forms
  ALTER COLUMN users_id DROP NOT NULL;

-- ...but a row must still identify SOMEBODY. Same rule as appointment_has_a_client,
-- enforced in the database as well as the service, so no path can write a row
-- that names nobody - psql included.
ALTER TABLE forms
  DROP CONSTRAINT IF EXISTS forms_has_a_client;

ALTER TABLE forms
  ADD CONSTRAINT forms_has_a_client
  CHECK (users_id IS NOT NULL OR walk_in_name IS NOT NULL);
