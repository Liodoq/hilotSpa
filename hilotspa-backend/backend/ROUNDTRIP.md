# Next block: prove the round trip, then connect n8n to the website

Two parts. Part A must pass before Part B is worth starting, because everything
in Part B reads the rows Part A writes.

---

# PART A — one assessment, browser to Postgres

Never been done. The Angular app is built, the backend boots, n8n works — and no
form has ever travelled the whole way.

## What is already wired

`core/forms.api.ts` really does call the backend:

- `POST /api/v1/forms/create`
- `POST /api/v1/demographics/create`

So the wizard's submit should reach Postgres. **Demographics will not**, and
that is not a bug in the UI — see A1.

## A1 — Fix B43 first, or the test cannot start

`SecurityConfig` has:

```java
.requestMatchers("/api/v1/demographics/**").hasAnyRole("STAFF", "ADMIN")
```

A CUSTOMER gets **403** saving their own profile. `profileGuard` then bounces
them out of the wizard before step 1. This is why `profile.store.ts` currently
writes to `localStorage` with a `TODO`.

**The fix is not to loosen that rule.** Widening it to `authenticated()` would
let any logged-in customer read and edit every other client's age, sex, weight
and occupation. Add a self-service endpoint instead, where identity comes from
the token and never from the request.

### `DemographicsController`

```java
@PostMapping("/me")
public ResponseEntity<DemographicsModel> saveMine(@RequestBody DemographicsModel body) {
    // userId comes from the JWT. Anything in the body is attacker-controlled.
    return ResponseEntity.ok(service.upsertForUser(currentUser.id(), body));
}

@GetMapping("/me")
public ResponseEntity<DemographicsModel> getMine() {
    return ResponseEntity.ok(service.findByUserId(currentUser.id()));
}
```

`upsertForUser` updates the existing row when one exists — `Demographics` has a
unique constraint on `users_id`, so a blind insert throws on the second save.

### `SecurityConfig` — **order matters**

Spring takes the **first** matching rule. The `/me` line must sit **above** the
STAFF/ADMIN line or it never runs:

```java
.requestMatchers("/api/v1/demographics/me").authenticated()   // FIRST
.requestMatchers("/api/v1/demographics/**").hasAnyRole("STAFF", "ADMIN")
```

Put it the other way round and you will spend an hour on a 403 that looks
impossible.

### `profile.store.ts`

Replace the `localStorage` read/write with `GET`/`POST /demographics/me`. Keep
localStorage as a cache if you like, but the server becomes the truth.

## A2 — B29 is already fixed. A different bug is not.

**Correction to my earlier claim:** I said `createForm` trusts `userId` from the
body. It does not. It already reads `CurrentUser.id()` and, for a CUSTOMER,
overrides whatever the body said. B29 is closed — verify and mark it.

The real problem is next door, in `DemographicsServiceImpl.createDemographics`:

```java
// Note: User mapping should be handled here via UserRepository if needed
```

**The user is never set.** Every demographics row is written orphaned, with
`users_id` NULL. Postgres allows many NULLs through a unique constraint, so
nothing complains — the rows just belong to nobody, and `findByUserId` can never
find them again.

That is why fixing B43 is not only about the security rule. Logged as **B65**.

## A3 — Run it

B66 made `height` and `weight` nullable, and `ddl-auto=update` will **not**
drop a NOT NULL constraint. The database has to be recreated.

> ⚠️ **Never `docker compose down -v` again.** `-v` removes *every* named volume
> in the project, and that now includes `n8n_data` — the workflows, the Vertex
> credential, the execution history. Drop only the database:

```powershell
docker compose down
docker volume ls                      # find it; likely backend_postgres_data
docker volume rm backend_postgres_data
docker compose up -d --build
```

n8n needs no changes for any of this. Nothing in Part A touches it.

Then in the browser:

1. Register a new customer.
2. Log in.
3. Fill the profile — **this is the B43 checkpoint.** If it saves without a 403,
   A1 worked.
4. Start the assessment: intent → body map (mark at least 2 spots, both views,
   one left/right and one midline) → complaints → history → review → submit.

## A4 — Look in the database

```powershell
docker compose exec db psql -U postgres -d hilotspa_db
```

```sql
-- the assessment
SELECT id, intent, main_complaint, main_complaint_duration,
       had_illness, has_therapy, status, created_at
FROM forms ORDER BY created_at DESC LIMIT 1;

-- the pain points, which is the 2D visual mapping actually landing
SELECT anatomical_region, side, body_view,
       pain_score_before, pain_score_after, coordinatex, coordinatey
FROM patient_intake
WHERE form_id = (SELECT id FROM forms ORDER BY created_at DESC LIMIT 1);

-- the profile
SELECT age, sex, status, occupation, height, weight FROM demographics;

-- did the form attach to the RIGHT user? (this is the B29 check)
SELECT u.email, f.intent, f.created_at
FROM forms f JOIN users u ON u.id = f.users_id
ORDER BY f.created_at DESC LIMIT 1;
```

### What must be true

| Check | Why it matters |
|---|---|
| One `forms` row, `intent` set | §E1 — the pain/leisure fork is recorded, not cosmetic |
| One `patient_intake` row **per marked spot** | the body map is persisting, not just drawing |
| `anatomical_region` is a real enum value, never null | §H4 — region is a fact, not a guess |
| `side` is LEFT/RIGHT on limbs, CENTRE on midline | §H8, and the paper form's L/R columns |
| `pain_score_before` set, `pain_score_after` **null** | §H3 — staff fills AFTER later. Null here is correct. |
| `occupation` present | §H1 |
| `users_id` = the account you logged in as | B29 |

**Screenshot this psql output.** It is Chapter IV evidence that the digital
intake captures what the paper form captures.

## A5 — Likely failures, pre-diagnosed

| Symptom | Cause |
|---|---|
| 403 on profile save | the `/me` rule is below the STAFF rule in `SecurityConfig` |
| 403 on submit | JWT expired — log out and back in |
| 400 on submit | an enum name mismatch; compare the JSON in DevTools Network against the entity |
| Form saves, zero `patient_intake` rows | `replacePainPoints` not called, or the UI sent an empty array |
| `side` null everywhere | the UI captures it but `toFormsModel` is not sending it (§H8) |
| CORS error | `FRONTEND_ORIGIN` in `.env` does not match the Angular dev port |

---

# PART B — connecting n8n to the website (task 2.16)

**This is the answer to "when do we connect n8n."** Right after Part A, because
`allowedServices` is computed from a real `Forms` row and the `ServiceProtocol`
table. Today n8n is only reachable from PowerShell.

## The chain, end to end

```
/book (Angular)
   -> POST /api/v1/assistant/recommend/{formId}     (Spring, JWT)
        1. load Forms + pain points, authorize the caller owns it
        2. load services + ServiceProtocol rows for this complaint
        3. DROP every CONTRAINDICATED service        <- the safety filter
        4. POST http://n8n:5678/webhook/hilotspa/recommend
        5. re-validate: every returned id must be in allowedServices
        6. AuditLog: modelUsed, status, rejectedCount
   <- ranked services with reasons
```

## B1 — `AssistantService`

```java
List<Massage> all = massageRepository.findAll();
Set<UUID> banned = serviceProtocolRepository
        .findByConditionAndRule(form.getMainComplaint(), ProtocolRule.CONTRAINDICATED)
        .stream().map(p -> p.getService().getId()).collect(toSet());

List<AllowedService> allowed = all.stream()
        .filter(m -> !banned.contains(m.getId()))
        .map(...)
        .toList();

if (allowed.isEmpty()) {
    // Not an error. It is a clinical answer: nothing the spa offers is
    // indicated here. Do NOT call n8n - return "please see the practitioner".
}
```

Also collect `banned` for every `complaintType` on the pain points, not only the
chief complaint. A client can mark a knee while complaining about a shoulder.

## B2 — Validate the answer again in Java

n8n already drops unknown ids. Do it again in Spring anyway.

The two checks are not redundant — they defend different things. n8n's protects
the client in the normal path. Spring's protects against a compromised or
misconfigured n8n, which is a machine you do not control at the branch. It is
also the check you can point at during the defence, because it lives in the code
the panel is reading.

## B3 — Spring needs its own fallback

If n8n is unreachable — container down, wrong URL, timeout — Spring must return
the protocol-table ranking itself, exactly as the n8n fallback node does.

Set a **5-second timeout**. A hanging AI call must never hold a booking screen.

## B4 — Record the metric

One `AuditLog` row per call:

```
action     = "ASSISTANT_RECOMMEND"
entityType = "Forms"
entityId   = formId
details    = {"status":"OK","modelUsed":"gemini-2.5-flash",
              "rejectedCount":0,"latencyMs":2106}
```

No new table. At the end of the study, one query over `audit_log` gives you the
measured hallucination rate, the fallback rate and the latency distribution —
the AI-reliability numbers the paper promises, as data rather than assertion.

## B5 — Config

```properties
hilotspa.n8n.url=${N8N_WEBHOOK_URL:http://localhost:5678}
hilotspa.n8n.timeout-ms=5000
```

`compose.yaml` already overrides `N8N_WEBHOOK_URL` to `http://n8n:5678` for the
backend container. Inside a container `localhost` is that container.

## B6 — Angular

`/book` calls `POST /api/v1/assistant/recommend/{formId}` on load and renders the
ranked services. The chat box calls `POST /api/v1/assistant/chat`. Angular never
calls port 5678 — it has no JWT n8n could check, and going direct would put an
unauthenticated door in front of patient data.

## Definition of done for Part B

- A real assessment in the browser produces a ranked list on `/book` that came
  from Vertex.
- Stopping n8n (`docker compose stop n8n`) still produces a ranked list, from
  the protocol table, within 5 seconds.
- `audit_log` has one `ASSISTANT_RECOMMEND` row per call with `rejectedCount`.
- A service marked CONTRAINDICATED for the client's complaint never appears —
  verified by marking one in `ServiceProtocol` and re-running.

That last one is the demo. It is the paper's central safety claim, shown rather
than argued.
