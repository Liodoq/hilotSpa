-- V11 - what a treatment actually is, in the spa's own words (task 3.35).
--
-- The detail page carried one hardcoded sentence with the name and duration
-- substituted into it, so all thirty treatments said the same thing. A client
-- opening "Ventosa" learned that Ventosa runs thirty minutes.
--
-- Deliberately NULLABLE, and deliberately left empty by this migration. The
-- text has to come from the spa. Writing "Ventosa uses heated cups to draw out
-- muscle tension" here would be the system inventing a clinical claim, which is
-- the one thing this application is built not to do. Empty falls back to the
-- existing sentence, which is honest.
--
-- 600 characters: a paragraph a client will read, not an article. A limit that
-- forces brevity is kinder to the person writing thirty of them.

ALTER TABLE massage ADD COLUMN IF NOT EXISTS description varchar(600);
