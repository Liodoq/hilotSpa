-- HilotSpa verification queries
-- Run:  docker compose exec db psql -U postgres -d hilotspa_db -f /dev/stdin < verify.sql
--   or: docker compose exec db psql -U postgres -d hilotspa_db   then \i

\echo '=== 1. The most recent assessment (does it exist at all?) ==='
SELECT id, intent, main_complaint, main_complaint_duration,
       had_illness, has_therapy, status, created_at
FROM forms ORDER BY created_at DESC LIMIT 1;

\echo '=== 2. Its pain points - the 2D visual mapping actually landing ==='
-- EXPECT: one row per marked spot; anatomical_region never null;
--         side LEFT/RIGHT on limbs and CENTRE on the midline;
--         pain_score_after NULL (staff writes it after the session - correct here)
SELECT anatomical_region, side, body_view,
       pain_score_before, pain_score_after, coordinatex, coordinatey
FROM patient_intake
WHERE form_id = (SELECT id FROM forms ORDER BY created_at DESC LIMIT 1);

\echo '=== 3. Did the form attach to the RIGHT account? (B29) ==='
SELECT u.email, f.intent, f.created_at
FROM forms f JOIN users u ON u.id = f.users_id
ORDER BY f.created_at DESC LIMIT 3;

\echo '=== 4. Demographics linked to a user? (B65 - was writing NULL) ==='
-- EXPECT: one row per client who saved a profile. Zero rows means orphaned again.
SELECT u.email, d.age, d.sex, d.status, d.occupation, d.height, d.weight
FROM demographics d JOIN users u ON u.id = d.users_id;

\echo '=== 5. Orphaned demographics (should be ZERO) ==='
SELECT count(*) AS orphaned FROM demographics WHERE users_id IS NULL;

\echo '=== 6. Assistant reliability - the Chapter IV numbers ==='
-- rejectedCount is how many services the model named that Java had not approved.
-- This is a MEASURED hallucination rate, not an asserted one.
SELECT occurred_at, details
FROM audit_log
WHERE action = 'ASSISTANT_RECOMMEND'
ORDER BY occurred_at DESC LIMIT 10;

\echo '=== 7. Aggregate reliability (run this at the end of the study) ==='
SELECT
  count(*)                                                          AS calls,
  count(*) FILTER (WHERE details LIKE '%"status":"OK"%')            AS answered_by_model,
  count(*) FILTER (WHERE details LIKE '%"status":"FALLBACK"%')      AS fell_back,
  count(*) FILTER (WHERE details LIKE '%"status":"REFER"%')         AS referred,
  count(*) FILTER (WHERE details NOT LIKE '%"rejectedCount":0%')    AS calls_with_a_rejected_id
FROM audit_log
WHERE action = 'ASSISTANT_RECOMMEND';

\echo '=== 8. The protocol table the whole safety claim rests on ==='
SELECT m.name AS service, sp.condition, sp.rule, sp.authored_by
FROM service_protocol sp JOIN massage m ON m.id = sp.service_id
ORDER BY sp.rule, m.name;
