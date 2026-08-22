# HilotSpa — n8n integration

The Data-Driven Virtual Assistant is orchestrated in n8n. This folder holds the
workflow as a file so it is version-controlled and reproducible for the defence.
n8n's own database is a runtime detail, not the source of truth.

## The one architectural rule

**n8n never connects to Postgres.**

Spring assembles everything the assistant is allowed to know and posts it in one
request. n8n has no database credentials, no JWT, and no way to reach a second
branch's data. Three things follow from that:

1. Every access-control rule stays in one place — Spring. There is no second
   authorisation surface to audit or to get wrong.
2. The workflow can be run, replayed and screenshotted during the defence
   without a live database.
3. It preserves single-writer-per-partition. n8n reads nothing and writes
   nothing, so it cannot participate in a write conflict.

## The safety boundary

`allowedServices` is computed by Java from the spa-authored `ServiceProtocol`
table **before** the model is ever called. Anything marked `CONTRAINDICATED` for
this client is already gone.

The `Validate and rank` node then drops any `serviceId` that Spring did not
send. So the assistant is incapable of recommending a contraindicated service —
not because the prompt asks it not to, but because the id has nowhere to come
from. The model ranks and explains; it never decides what is safe.

If nothing survives validation, `status` is `FALLBACK` and the answer comes
straight from the protocol table. **The assistant has no failure mode in which
the client sees nothing.** Gemini being down, rate-limited, or unfunded degrades
the wording, not the booking.

## Request — Spring to n8n

`POST http://n8n:5678/webhook/hilotspa/recommend`

Note `n8n`, not `localhost`. Inside a container `localhost` is that container.

```json
{
  "formId": "3f1c...uuid",
  "intent": "PAIN",
  "chiefComplaint": "LOWER_BACK_PAIN",
  "chiefComplaintDuration": "3 months",
  "pressurePreference": "MEDIUM",
  "painPoints": [
    { "anatomicalRegion": "LUMBAR", "side": "CENTRE",
      "bodyView": "BACK", "painScoreBefore": 8 }
  ],
  "flags": { "pregnant": false, "hypertension": true },
  "allowedServices": [
    { "serviceId": "9a2b...uuid", "name": "Hilot Traditional",
      "durationMinutes": 60, "price": 500.00,
      "rule": "INDICATED", "rationale": "Protocol lists this for lower back pain." }
  ]
}
```

`allowedServices` must be non-empty. If Java filters everything out, that is a
clinical answer in itself — do not call n8n, tell the client to see the
practitioner.

## Response — n8n to Spring

```json
{
  "formId": "3f1c...uuid",
  "status": "OK",
  "recommendations": [
    { "serviceId": "9a2b...uuid", "rank": 1, "reason": "One plain sentence." }
  ],
  "modelUsed": "gemini-2.5-flash",
  "rejectedCount": 0,
  "parseError": null,
  "generatedAt": "2026-08-21T12:00:00.000Z"
}
```

`status` is one of:

| value      | meaning                                                  |
|------------|----------------------------------------------------------|
| `OK`       | the model answered and at least one id passed validation |
| `FALLBACK` | protocol-table answer; model absent, broken, or rejected |
| `ERROR`    | the request from Spring was malformed; see `errors`      |

`rejectedCount` is the count of model picks thrown away for not being in
`allowedServices`. **Log it.** It is the reliability measurement the paper
promises — a hallucination rate, measured rather than asserted.

## Where the Gemini API key goes

**In n8n, as a credential. Nowhere else.**

Not in `.env`, not in `compose.yaml`, not in the workflow JSON. Spring never
calls Gemini — n8n does — so the key has no reason to exist on the Java side.

1. Get a key from Google AI Studio (https://aistudio.google.com/apikey).
2. In n8n: left sidebar → **Credentials** → **Add credential** → search
   **Header Auth**.
   - Name: `Gemini API key`
   - Header Name: `x-goog-api-key`
   - Header Value: *(paste the key)*
   - Save.
3. Open the **Gemini** node → *Credential for Header Auth* → pick
   `Gemini API key`.
4. Un-disable the node (right-click → Activate), **Save**, keep the workflow
   Active.

Three reasons this is not fussiness:

- **n8n blocks environment access inside nodes by default.** Putting the key in
  `.env` and reaching for `process.env` in a Code node does not work, and
  turning that block off would let any workflow read `DB_PASSWORD` and
  `JWT_SECRET` too.
- **Credentials are encrypted at rest** with `N8N_ENCRYPTION_KEY`, which is
  pinned in `.env`, so they survive `docker compose down -v`.
- **Credentials are redacted in execution logs.** You will be screenshotting
  n8n executions for the appendix. A key typed into a plain header field shows
  up in those screenshots; a credential shows as `***`.

`GEMINI_API_KEY` in `.env` is now unused. Leave it empty and commented — an
empty variable that looks load-bearing is worse than no variable at all.

## Importing the workflow

1. `docker compose up -d n8n`
2. Open http://localhost:5678 and create the local owner account.
3. Top-right menu → **Import from File** → `hilotspa-recommend.workflow.json`.
4. **Save**, then click **Publish** (older n8n: toggle **Active**).

Publishing matters, and Save alone does not do it. In n8n 2.0 *Save* updates
the stored copy and leaves it an unpublished draft; *Publish* is what makes it
run. n8n has two webhook URLs:

- `/webhook-test/hilotspa/recommend` — only live while you have clicked
  *Execute workflow*, and only for one call.
- `/webhook/hilotspa/recommend` — the real one, live only once the workflow is
  **published**.

A workflow that is imported but not published returns 404 to Spring, and so does
a published workflow you have since edited and only saved. This is the single
most common way to lose an afternoon here.

## Smoke test

From Windows PowerShell, with the workflow published:

```powershell
$body = @{
  formId = "11111111-1111-4111-8111-111111111111"
  intent = "PAIN"
  chiefComplaint = "LOWER_BACK_PAIN"
  chiefComplaintDuration = "3 months"
  painPoints = @(@{ anatomicalRegion="LUMBAR"; side="CENTRE"; bodyView="BACK"; painScoreBefore=8 })
  flags = @{ hypertension = $true }
  allowedServices = @(
    @{ serviceId="9a2b0000-0000-4000-8000-000000000001"; name="Hilot Traditional";
       durationMinutes=60; price=500.00; rule="INDICATED";
       rationale="Protocol lists this for lower back pain." }
  )
} | ConvertTo-Json -Depth 6

Invoke-RestMethod -Uri "http://localhost:5678/webhook/hilotspa/recommend" `
  -Method Post -ContentType "application/json" -Body $body
```

With the Gemini node disabled you should get `status: FALLBACK`,
`modelUsed: stub`, and the one seeded service back. That is a **pass** — it
proves the transport, the contract and the fallback all work with no API key and
no money spent.

## Open items

- **The webhook is unauthenticated.** Acceptable only while 5678 is bound to a
  development laptop. Before this runs at the spa it needs Header Auth on the
  Webhook node and a matching shared secret in `.env`.
- **The image is `:latest`.** Pin it once it is running:
  `docker compose exec n8n n8n --version`, then write that tag into
  `compose.yaml`. A thesis artefact should not float.
- The Gemini node stays disabled until a Header Auth credential exists. See
  *Where the Gemini API key goes* above.
