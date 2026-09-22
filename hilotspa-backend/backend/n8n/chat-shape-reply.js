// ---------------------------------------------------------------------------
// Shape reply
//
// Two jobs. Guarantee the client always receives something, and never let a
// slotId the agent invented reach Spring.
//
// If the agent named an unknown slot, the REPLY IS DISCARDED TOO - not just the
// booking flag. That sentence described a time which does not exist, so sending
// it with the booking removed would still tell the client something untrue.
//
// B142. The same principle in the opposite direction, and it is the one that
// actually bit. The agent wrote "your time has been held for you, you can now
// choose your therapist and room" with book=false. Nothing was held, because
// book=false is how the agent says nothing happened. The screen therefore
// stayed on the suggestion panel, and the client - reasonably - typed "I
// confirm", then "Okay finalize it", then "There is no screen to click", while
// the agent kept telling them to confirm on a screen that was showing
// something else entirely.
//
// Prose that CLAIMS a hold while the FIELD says otherwise is the same class of
// lie as an invented slotId, so it gets the same treatment: the sentence goes.
// Rule 7d in the prompt asks the agent not to do this. Asking is not a
// guarantee; this is.
// ---------------------------------------------------------------------------
const built = $('Build context').first().json;
const now = new Date().toISOString();

if (built.ok === false) {
  return [{ json: {
    sessionKey: built.sessionKey,
    reply: 'Sorry - I could not read that request.',
    status: 'ERROR', book: false, slotId: null, serviceId: null,
    errors: built.errors, generatedAt: now
  } }];
}

const raw = $input.first().json;
const text = raw && (raw.output ?? raw.text ?? raw.response);

function fallback(reply, status) {
  return [{ json: {
    sessionKey: built.sessionKey, reply: reply, status: status,
    book: false, slotId: null, serviceId: null, generatedAt: now
  } }];
}

if (!text) {
  return fallback('I am having trouble answering right now. The front desk can help you '
                + 'with any question about our services.', 'FALLBACK');
}

// Be liberal about what the model wraps its JSON in.
let cleaned = String(text).trim();
const fenced = cleaned.match(/```(?:json)?\s*([\s\S]*?)```/i);
if (fenced) { cleaned = fenced[1].trim(); }
const open = cleaned.indexOf('{');
const close = cleaned.lastIndexOf('}');
if (open !== -1 && close > open) { cleaned = cleaned.slice(open, close + 1); }

let parsed = null;
try { parsed = JSON.parse(cleaned); } catch (e) { parsed = null; }

// ---------------------------------------------------------------------------
// A model that ignored the format is still a model that said something useful -
// but the CLIENT must never see the envelope.
//
// This used to hand back the raw text, and when JSON.parse failed (one stray
// quote inside the reply is enough) the client read
//   ... sa screen ninyo.","book":false,"slotId":null}
// on screen. A spa client seeing our JSON is worse than a client seeing a
// polite apology: it looks broken, and at a defence it looks unfinished.
//
// So: salvage the sentence, or say nothing that pretends to be one.
// ---------------------------------------------------------------------------
function salvageReply(value) {
  let s = String(value).trim();

  // Drop a leading envelope opener if the model got that far.
  const head = s.match(/"reply"\s*:\s*"/);
  if (head) { s = s.slice(head.index + head[0].length); }

  // Cut where the ENVELOPE resumes - not at any old quote, which would truncate
  // a sentence that legitimately quotes something.
  const tail = s.search(/"\s*,\s*"(?:book|slotId|serviceId|status)"|"\s*\}\s*$/);
  if (tail !== -1) { s = s.slice(0, tail); }

  // Undo the escaping the model applied inside its own string.
  s = s.replace(/\\n/g, ' ').replace(/\\"/g, '"').replace(/\\\\/g, '\\').trim();

  // If it still smells of JSON, we have not salvaged a sentence. Say so by
  // returning nothing rather than showing debris with a full stop after it.
  if (!s || s.startsWith('{') || /"(?:book|slotId|serviceId|status)"\s*:/.test(s)) {
    return null;
  }
  return s.slice(0, 1200);
}

if (!parsed || typeof parsed.reply !== 'string') {
  const salvaged = salvageReply(text);
  return fallback(
    salvaged || 'I am having trouble answering right now. The front desk can help you '
              + 'with any question about our services.',
    salvaged ? 'OK' : 'FALLBACK');
}

const allowed = new Set(built.slotIds || []);
const wantsToBook = parsed.book === true;
const slotId = parsed.slotId == null ? null : String(parsed.slotId);

if (wantsToBook && (!slotId || !allowed.has(slotId))) {
  // Invented a time. Drop the claim and the sentence that carried it.
  return fallback('Sorry - I could not hold that time. Tell me a day and time that '
                + 'suits you and I will check again.', 'REJECTED');
}

// --- B142: does the SENTENCE claim something the FIELD does not back up? ----
//
// Matched on the phrases the agent actually reaches for, in both languages it
// speaks. Deliberately narrow: "held", "reserved", "finalize", "next step",
// "on your screen". A client asking "what times are held at the spa normally?"
// must not trip this, which is why "hold" as a bare verb is not in the list.
const CLAIMS_A_HOLD = new RegExp([
  'has been held', 'is (?:now )?held', 'have held', 'holding (?:it|that|your)',
  'na-?hold', 'nakahold', 'na-?reserve', 'reserved for you',
  'choose your therapist', 'pili.{0,12}therapist',
  'finali[sz]e your booking', 'complete the booking', 'proceed to finali[sz]e',
  'confirm .{0,30}on your screen', 'on your screen to complete'
].join('|'), 'i');

if (!wantsToBook && CLAIMS_A_HOLD.test(parsed.reply)) {
  // Do NOT try to keep the sentence and strip the claim. The whole reply is
  // built around a thing that did not happen; what is left would be a
  // non-sequitur. Replace it with the only instruction that is actually true,
  // which is to use the panel.
  return fallback('I have not been able to hold a time for you yet - nothing is '
                + 'reserved. Tap <b>See times</b> on one of the treatments below and '
                + 'pick an hour that suits you, and the therapist and room options '
                + 'will open up straight after.', 'UNBACKED');
}

return [{ json: {
  sessionKey: built.sessionKey,
  reply: String(parsed.reply).trim().slice(0, 1200),
  status: 'OK',
  book: wantsToBook,
  slotId: wantsToBook ? slotId : null,
  serviceId: wantsToBook && parsed.serviceId ? String(parsed.serviceId) : null,
  generatedAt: now
} }];
