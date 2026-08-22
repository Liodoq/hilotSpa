# Booking through the assistant — specification

Status: **specified, not built.** Depends on an `AppointmentController` that does
not exist yet.

## The rule this whole design exists to protect

**Spring performs every write, inside a transaction that re-checks the slot.**

That is the whole constraint, and it is not about consent — it is about two
clients asking for 3 PM four seconds apart. Only a database transaction can make
one of them lose. A model cannot hold a lock, so Process Rule #4 is
unenforceable anywhere the model writes. A hallucinated slot that becomes a real
appointment is a person who travelled for nothing.

**The assistant DOES book** (decision, 2026-08-22). It calls a tool; Spring
executes. From the client's side the AI booked it. Underneath, Spring is still
the only writer.

Consent is conversational, not a button. Nothing is charged at booking time —
`paymentStatus` is UNPAID, payment is at the counter, and the appointment is
cancellable — so requiring a separate tap was my preference, not a requirement.
A spoken "yes" is the better experience for an older client on a phone.

Three things that decision then requires:

1. **Explicit affirmation only.** Book on "yes, book it". Never on "sounds
   good maybe", never on a question, never on silence. Ambiguity means ask again.
2. **Record the consenting turn.** The AuditLog row stores the exact client
   message that authorised the booking. That is the audit trail the button click
   would otherwise have been.
3. **An idempotency key.** Agents retry on timeout. Key on
   `sessionKey + slotStart`; a repeat returns the existing appointment instead of
   creating a second one. Without this, one slow network call books the same
   client twice and neither of you finds out until they arrive.

## Behaviour Liodoq specified

When the client asks for a time:

- **Available** → propose it, ask for confirmation.
- **Not available** → say so plainly, then offer the **nearest alternatives**,
  and make it explicit that they may pick another day instead.
- **Either way the client decides.** The assistant never silently substitutes a
  different time, and never treats "here is another slot" as agreement.

Worked example:

> Sorry, 3:00 PM tomorrow is already taken. The closest I have is **1:30 PM** or
> **4:30 PM** tomorrow, both with a 60-minute Hilot Traditional at ₱500. Would
> either of those work, or would you rather pick another day?

## Who parses "tomorrow 3pm"

**Spring does not.** Natural language never reaches the scheduler.

Spring sends the assistant a **closed list of open slots** for the next 7 days.
The model matches the client's phrasing against that list. It is the same
structural guarantee as `allowedServices`: the model cannot name a slot that is
not on the list, because there is nowhere for one to come from.

⚠️ **The payload must carry the current date and time.** The model has no clock.
Without `now`, "tomorrow" is meaningless and it will invent a date. Send
`now` as an ISO timestamp **and** `timezone: "Asia/Manila"`.

## Endpoints to build

### 1. `GET /api/v1/appointments/availability`

Query: `serviceId`, `from` (date), `days` (default 7).

Branch-scoped from the JWT. A slot is open when **a therapist and a room are
both free** for the full service duration. Returns distinct start times — the
client is not choosing a therapist, so collapse duplicates.

```json
{
  "timezone": "Asia/Manila",
  "now": "2026-08-22T11:40:00+08:00",
  "serviceId": "9a2b…",
  "durationMinutes": 60,
  "slots": [
    { "start": "2026-08-23T13:30:00+08:00", "label": "Sun 23 Aug, 1:30 PM" },
    { "start": "2026-08-23T16:30:00+08:00", "label": "Sun 23 Aug, 4:30 PM" }
  ]
}
```

`label` is pre-rendered by Java on purpose. Left to the model, times get
reformatted, translated, or quietly shifted by an hour.

### 2. `POST /api/v1/appointments`

Body: `serviceId`, `start`, `formId`. Customer comes from the JWT, never the
body (the B29 lesson).

Inside **one transaction**:

1. Re-check therapist and room availability. The slot may have gone in the
   seconds since it was proposed.
2. Assign a free therapist and room.
3. Insert with `source = CHATBOT`, `paymentStatus = UNPAID`,
   `priceAtBooking` copied from the service now.
4. Write the `AuditLog` row.

On conflict return **409** with the recomputed next slots, so the UI can say
"that just went — here are the closest" without another round trip.

### 3. `POST /api/v1/assistant/chat`

Spring's relay to n8n. Adds identity, `allowedServices`, and `availableSlots`
before forwarding. Angular never calls n8n directly.

## Chat payload additions

```json
{
  "now": "2026-08-22T11:40:00+08:00",
  "timezone": "Asia/Manila",
  "availableSlots": [
    { "slotId": "s1", "serviceId": "9a2b…", "start": "2026-08-23T13:30:00+08:00",
      "label": "Sun 23 Aug, 1:30 PM", "durationMinutes": 60, "price": 500.00 }
  ]
}
```

`slotId` exists so the reply can reference a slot by token rather than by a
timestamp the model might retype incorrectly.

## Response contract

The agent returns JSON, not prose:

```json
{
  "reply": "Sorry, 3:00 PM tomorrow is taken. The closest I have is 1:30 PM or 4:30 PM…",
  "proposal": { "slotId": "s1", "serviceId": "9a2b…" },
  "alternatives": ["s1", "s7"]
}
```

`proposal` is what renders the **Confirm** button. It is optional — most turns
have none.

## The guard in `Shape reply`

Same shape as the service guard, and just as unforgiving:

- Every `slotId` in `proposal` and `alternatives` **must** exist in
  `availableSlots`. Unknown ids are dropped.
- If `proposal` referenced an unknown slot, **the reply text is discarded too**,
  not just the proposal. The text described a slot that does not exist, so it is
  a lie, and shipping it with the button removed still misinforms the client.
  Replace it with a deterministic sentence built from the real next two slots.
- Count these as `rejectedSlotCount` alongside `rejectedCount`. Same reliability
  metric, second surface.

## Booking flow

1. Client: "Book me for tomorrow 3pm."
2. Agent matches it against `availableSlots`.
   - **Not there** → says so, offers nearest earlier + later, invites another
     day. Books nothing.
   - **There** → states service, time, duration and price, and asks to confirm.
3. Client: "Yes, book it."
4. Agent calls the **bookAppointment** tool with `slotId` + `idempotencyKey`.
5. Tool calls `POST /api/v1/appointments` (Spring, JWT, transactional).
   - **200** → agent confirms with the details Spring returned, not its own.
   - **409** → "that time was just taken", plus the fresh slots in the response.
6. Angular refreshes the booking list from Spring, never from the reply text.

The agent quotes Spring's response back, not its own earlier sentence. If they
ever disagree, Spring is right.

**The tool is the only write the agent can reach.** It takes a `slotId` from a
list Spring supplied and nothing else — no free-text date, no therapist choice,
no price. There is no second tool, and the agent has no database credentials, so
the blast radius of any prompt injection is one appointment at one slot that was
already open.

## System prompt changes

Replace rule 7 (*"You cannot make, move, or cancel a booking"*) with:

```
7.  You may book, but ONLY a slot from AVAILABLE SLOTS below, and ONLY after the
    client has clearly said yes to that specific time. Refer to slots by slotId.
8.  Before booking, state the service, the day and time, the duration and the
    price, and ask them to confirm. Never book on a maybe, on a question, or on
    silence. If you are unsure whether they agreed, ask again.
9.  If the time they ask for is not in the list, say so plainly, offer the
    closest earlier and closest later slot, and tell them they may choose another
    day instead. Never substitute a different time silently, and never treat your
    own suggestion as their agreement.
10. After booking, repeat back exactly what the system returned. If the system
    says the slot was taken, tell them and offer the new closest times. Never
    claim a booking the system did not confirm.
11. Today is {{now}} in {{timezone}}. Work out "tomorrow" and "next week" from
    that. Never guess a date.
12. Quote times using the slot's label exactly as given. Do not reformat,
    translate or recalculate them.
13. You cannot move or cancel an existing booking. Direct them to the front desk.
```

## Tests that must pass before this is called done

| Message | Required behaviour |
|---|---|
| A time that is open, then "yes" | books it; appointment row with source=CHATBOT |
| A time that is open, then "hmm maybe" | books NOTHING; asks again |
| A time that is taken | says so, offers nearest earlier + later, invites another day |
| "Yes book it" with no time discussed | asks which time; books nothing |
| A date outside the 7-day window | says how far ahead booking is open; invents nothing |
| Two clients confirming the same slot | second gets 409 and fresh alternatives |
| The same confirmation sent twice | ONE appointment, not two (idempotency key) |
| Model returns an unknown slotId (force it) | proposal AND text dropped, deterministic fallback |

The last row is the one worth screenshotting for the appendix. It is the proof
that the guarantee is structural rather than a matter of prompt wording.
