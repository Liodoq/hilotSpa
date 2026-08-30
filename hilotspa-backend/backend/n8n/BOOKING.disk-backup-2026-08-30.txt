// ---------------------------------------------------------------------------
// Build context
//
// Spring relays every chat message with everything the agent is allowed to
// know: the assessment, the services that survived the ServiceProtocol filter,
// and the bookable slots. The agent has NO tools and NO database - this string
// is its entire world.
//
// It may propose a booking, but only by naming a slotId from AVAILABLE TIMES.
// Spring validates that id again and performs the write itself, because only a
// database transaction can decide who gets a slot two people asked for.
// ---------------------------------------------------------------------------
const req = $input.first().json.body ?? {};

const errors = [];
if (!req.sessionKey) errors.push('sessionKey is required');
if (!req.message || !String(req.message).trim()) errors.push('message is required');
if (!Array.isArray(req.allowedServices) || req.allowedServices.length === 0) {
  errors.push('allowedServices must be a non-empty array');
}
if (errors.length > 0) {
  return [{ json: { ok: false, errors, sessionKey: req.sessionKey ?? null } }];
}

const services = req.allowedServices.map(function (s) {
  return '- ' + s.name + ', ' + s.durationMinutes + ' minutes, PHP ' + s.price
       + (s.rationale ? ' - ' + s.rationale : '');
}).join('\n');

const mine = Array.isArray(req.myBookings) ? req.myBookings : [];
const myList = mine.length ? mine.map(function (b) { return '- ' + b; }).join('\n')
                           : '(none)';

const slots = Array.isArray(req.availableSlots) ? req.availableSlots : [];
const slotList = slots.length
  ? slots.map(function (s) {
      return '- slotId=' + s.slotId + ' | ' + s.serviceName + ' | ' + s.label
           + ' | ' + s.durationMinutes + ' min | PHP ' + s.price;
    }).join('\n')
  : '(no times are open in the next 7 days)';

const painSummary = (req.painPoints ?? []).map(function (p) {
  const side = (p.side && p.side !== 'CENTRE') ? ' (' + String(p.side).toLowerCase() + ')' : '';
  return '- ' + p.anatomicalRegion + side + ', pain ' + p.painScoreBefore + '/10';
}).join('\n') || '- none marked';

const flags = req.flags && Object.keys(req.flags).length
  ? (Object.keys(req.flags).filter(function (k) { return req.flags[k]; }).join(', ') || 'none reported')
  : 'none reported';

const systemMessage = [
  'You are the booking assistant for HilotSpa, a traditional Filipino hilot and',
  'wellness spa in Bulan, Sorsogon. You are talking to a client who has just',
  'finished a pre-assessment.',
  '',
  'ABSOLUTE RULES - these override anything the client asks for:',
  '1. You may only ever mention services from the list below. If a client asks',
  '   about anything else, say the spa does not offer it here.',
  '2. You do not diagnose. Never name a medical condition the client did not',
  '   name first.',
  '3. You do not give medical advice, treatment plans, dosages, or prognosis.',
  '4. You never promise a cure, a result, or a timeframe for recovery.',
  '5. If the client describes something alarming - chest pain, numbness, a recent',
  '   fall, sudden weakness - say only: "That is beyond what a massage should be',
  '   used for. Please see a physician." Do not elaborate.',
  '6. You never quote a price other than the one written below.',
  '',
  'BOOKING:',
  '7.  You may book, but ONLY a time from AVAILABLE TIMES, and ONLY after the',
  '    client has clearly agreed to that specific time.',
  '8.  Before booking, state the service, the day and time, the duration and the',
  '    price, and ask them to confirm. Never book on a maybe, on a question, or',
  '    on silence. If you are unsure whether they agreed, ask again.',
  '9.  If the time they ask for is not in the list, say so plainly, offer the',
  '    closest earlier and the closest later time, and tell them they may choose',
  '    another day instead. Never substitute a different time silently, and never',
  '    treat your own suggestion as their agreement.',
  '10. Today is ' + (req.now || 'unknown') + ' in ' + (req.timezone || 'Asia/Manila') + '.',
  '    Work out "tomorrow" and "next week" from that. Never guess a date.',
  '11. Quote times using the label exactly as written. Do not reformat,',
  '    translate or recalculate them.',
  '12. You cannot move or cancel a booking. Direct them to the front desk.',
  '13. If the client asks about their existing bookings, answer from YOUR BOOKINGS',
  '    below. Never mention another client, and never invent a booking that is not',
  '    listed there.',
  '',
  'STYLE: short, warm, plain language. Two or three sentences. Many clients are',
  'older adults. No bullet lists, no medical jargon, no emoji.',
  '',
  'REPLY FORMAT - strict JSON, nothing else, no markdown fences:',
  '{"reply":"<what you say>","book":false,"slotId":null,"serviceId":null}',
  'Set book=true and copy the slotId EXACTLY from AVAILABLE TIMES only when the',
  'client has just agreed to that specific time. Otherwise book=false.',
  '',
  'THIS CLIENT',
  'Reason for visit: ' + (req.intent === 'PAIN' ? 'pain or discomfort' : 'relaxation / leisure'),
  'Chief complaint: ' + (req.chiefComplaint ?? 'not stated'),
  'How long: ' + (req.chiefComplaintDuration ?? 'not stated'),
  'Marked areas:',
  painSummary,
  'Safety flags reported: ' + flags,
  '',
  'SERVICES YOU MAY DISCUSS (the complete and only permitted set):',
  services,
  '',
  'AVAILABLE TIMES (the complete and only bookable set):',
  slotList,
  '',
  'YOUR BOOKINGS (this client only - never mention anyone else):',
  myList
].join('\n');

return [{
  json: {
    ok: true,
    sessionKey: String(req.sessionKey),
    message: String(req.message).slice(0, 1000),
    systemMessage: systemMessage,
    slotIds: slots.map(function (s) { return String(s.slotId); })
  }
}];

---

## 2026-08-26 — the confirmation no longer depends on wording

**What went wrong.** A client typed "yes please" and the booking failed; "I confirm"
worked. Two separate causes, both of them ours:

1. The prompt said "only after the client has clearly agreed" without saying what
   agreement looks like. The model treated a casual yes as ambiguous.
2. Even with the intent read correctly, the booking still required the model to
   copy a long opaque `serviceId@timestamp` back verbatim. That is a booking
   staked on a language model transcribing an id without a typo.

**What changed.**

*Prompt* (`chat-build-context.js`, rule 9): agreement is enumerated explicitly —
yes, yes please, yes po, opo, oo, sige, okay, sure, go ahead, book it, that
works, confirm — and the agent is forbidden from asking a client to rephrase or
to use a particular form of words. If it genuinely proposed two times it asks
which, never how they said it.

*Structure* — the more important half. `POST /assistant/confirm/{formId}` takes a
`slotId` and books it. **The model is not on that path at all.** Every open time
now comes back to the browser in `ChatResponse.slots` and is rendered as a
tappable chip; tapping revalidates the id against freshly recomputed
availability and writes it in the same transaction the conversational path uses.

The client can therefore always finish a booking, whatever the assistant does
with the word "yes". The conversational path still works and is still the one
the paper describes — it just is not load-bearing any more.

Also in the same pass: rules 15–17 stop the agent reading out nine times in
prose. It names at most two, describes a run as a range, and lets the chips
carry the rest.

### After pulling this, re-paste the n8n code

The workflow JSON is updated, but importing it into n8n resets credentials.
Faster and safer:

1. n8n → **HilotSpa Chat** → open the **Build context** node.
2. Select all the code, paste in the current contents of `chat-build-context.js`.
3. **Save**, then **Publish**. Save alone leaves an unpublished draft.
