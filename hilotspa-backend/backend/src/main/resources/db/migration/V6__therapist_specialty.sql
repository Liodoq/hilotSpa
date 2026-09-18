-- Not every therapist can perform every treatment.
--
-- Bone setting is not massage. A client who books a bone setting and is handed
-- someone who has never set a bone has been failed in a way no amount of
-- scheduling correctness makes up for. So the skill is recorded on the
-- therapist, required by the treatment, and enforced TWICE - when times are
-- offered and again when the therapist is actually assigned. The same discipline
-- as V4's sex preference, and for the same reason: a rule the screen honours and
-- the write path ignores is worse than no rule at all.
--
-- WHAT NULL AND EMPTY MEAN HERE. Both readings are deliberate and both are
-- forgiving, because this migration runs against a database that already holds
-- live services and therapists the seeder has never heard of:
--
--   therapist_specialty  - no rows for a therapist means NO RESTRICTION HAS
--                          BEEN RECORDED, not "can do nothing". Read the other
--                          way, every therapist entered before this migration
--                          would match no treatment, every slot would vanish,
--                          and the calendar would empty with no error anywhere.
--   massage.required_specialty
--                        - NULL means anyone may perform it. Again the
--                          forgiving reading, for the same reason.
--
-- So the backfill below gives every existing therapist every specialty, and
-- sets required_specialty only where the treatment's own name settles it. Every
-- restriction that exists afterwards is one a human chose in the admin. The
-- system never invents a limit and never treats an unanswered question as "no".

CREATE TABLE IF NOT EXISTS therapist_specialty (
    therapist_id uuid        NOT NULL REFERENCES therapist (id) ON DELETE CASCADE,
    specialty    varchar(64) NOT NULL,
    PRIMARY KEY (therapist_id, specialty)
);

CREATE INDEX IF NOT EXISTS idx_therapist_specialty_therapist
    ON therapist_specialty (therapist_id);

ALTER TABLE massage
    ADD COLUMN IF NOT EXISTS required_specialty varchar(64);

-- Backfill: every existing therapist gets every specialty, so nothing that
-- worked before this migration stops working. Narrow them by hand in the admin.
-- ON CONFLICT so re-running against a partly-populated table is harmless.
INSERT INTO therapist_specialty (therapist_id, specialty)
SELECT t.id, s.specialty
FROM therapist t
CROSS JOIN (VALUES ('MASSAGE'), ('BONE_SETTING'), ('HEAD_SPA')) AS s (specialty)
ON CONFLICT DO NOTHING;

-- Only where the treatment's own name makes the answer obvious. Everything else
-- stays NULL - unrestricted - rather than being guessed at.
--
-- "Bone Setting + Therapeutic Massage" matches the first pattern and should:
-- that visit cannot happen without a bone setter, whatever else it includes.
UPDATE massage SET required_specialty = 'BONE_SETTING'
WHERE required_specialty IS NULL AND name ILIKE '%bone%';

UPDATE massage SET required_specialty = 'HEAD_SPA'
WHERE required_specialty IS NULL AND name ILIKE '%head spa%';
