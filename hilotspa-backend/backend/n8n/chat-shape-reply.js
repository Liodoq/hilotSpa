// ---------------------------------------------------------------------------
// Shape reply
//
// Two jobs. Guarantee the client always receives something, and never let a
// slotId the agent invented reach Spring.
//
// If the agent named an unknown slot, the REPLY IS DISCARDED TOO - not just the
// booking flag. That sentence described a time which does not exist, so sending
// it with the booking removed would still tell the client something untrue.
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

// A model that ignored the format is still a model that said something useful.
if (!parsed || typeof parsed.reply !== 'string') {
  return fallback(String(text).trim().slice(0, 1200), 'OK');
}

const allowed = new Set(built.slotIds || []);
const wantsToBook = parsed.book === true;
const slotId = parsed.slotId == null ? null : String(parsed.slotId);

if (wantsToBook && (!slotId || !allowed.has(slotId))) {
  // Invented a time. Drop the claim and the sentence that carried it.
  return fallback('Sorry - I could not hold that time. Tell me a day and time that '
                + 'suits you and I will check again.', 'REJECTED');
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
