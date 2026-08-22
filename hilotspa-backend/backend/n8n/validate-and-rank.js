// ---------------------------------------------------------------------------
// Validate and rank
//
// This is the safety boundary, and it is the reason the assistant can be
// defended in the paper. Whatever the model said, only a serviceId that Spring
// itself sent in allowedServices can survive this node. A hallucinated id, a
// contraindicated service, a reworded name -- all are dropped, not corrected.
//
// It reads allowedServices from the "Build request" node rather than from its
// own input, so it behaves identically whether the Gemini node is enabled or
// disabled.
// ---------------------------------------------------------------------------
const built = $('Build request').first().json;
const now = new Date().toISOString();

if (built.ok === false) {
  return [{ json: {
    formId: built.formId,
    status: 'ERROR',
    errors: built.errors,
    recommendations: [],
    modelUsed: 'none',
    generatedAt: now
  } }];
}

const allowed = new Map(built.allowedServices.map(function (s) {
  return [String(s.serviceId), s];
}));

// --- read the model answer, if there is one -------------------------------
// Three different things can arrive here and they must not be confused:
//   1. the Vertex node is DEACTIVATED  -> n8n passes Build request through, so
//      the input still carries .prompt
//   2. the Vertex node RAN AND FAILED  -> Google's error body arrives, because
//      the node is set neverError so a 401 does not stop the workflow
//   3. the Vertex node SUCCEEDED       -> .candidates is present
// Collapsing 1 and 2 into "stub" hides a broken credential behind a healthy
// looking FALLBACK, which is exactly the bug that eats an afternoon.
let picks = [];
let modelUsed = 'stub';
let parseError = null;

const raw = $input.first().json;

const wasSkipped = raw && Object.prototype.hasOwnProperty.call(raw, 'prompt');
const googleError = raw && raw.error ? raw.error : null;

if (googleError) {
  modelUsed = 'error';
  parseError = 'Vertex rejected the call: '
             + (googleError.code ? googleError.code + ' ' : '')
             + (googleError.status ? googleError.status + ' - ' : '')
             + (googleError.message || 'no message');
} else if (wasSkipped) {
  modelUsed = 'stub';
  parseError = null;
} else {
  const text = raw && raw.candidates && raw.candidates[0] &&
               raw.candidates[0].content && raw.candidates[0].content.parts &&
               raw.candidates[0].content.parts[0]
               ? raw.candidates[0].content.parts[0].text
               : null;

  if (text) {
    modelUsed = raw.modelVersion || 'vertex-gemini';
    const finish = raw.candidates[0].finishReason || null;
    const usage  = raw.usageMetadata || {};

    // Be liberal about what the model wraps its JSON in, but never about what
    // comes out the other side - the guard below is unchanged.
    let cleaned = String(text).trim();
    const fenced = cleaned.match(/```(?:json)?\s*([\s\S]*?)```/i);
    if (fenced) { cleaned = fenced[1].trim(); }
    const open = cleaned.indexOf('{');
    const close = cleaned.lastIndexOf('}');
    if (open !== -1 && close > open) { cleaned = cleaned.slice(open, close + 1); }

    try {
      const parsed = JSON.parse(cleaned);
      if (Array.isArray(parsed.recommendations)) {
        picks = parsed.recommendations;
      } else {
        parseError = 'JSON parsed but had no recommendations array. Keys: '
                   + Object.keys(parsed).join(', ');
      }
    } catch (e) {
      // Say WHAT came back. "not valid JSON" with the evidence discarded is
      // how the last hour was spent guessing.
      parseError = 'Could not parse the model reply.'
        + ' finishReason=' + finish
        + ' thoughtTokens=' + (usage.thoughtsTokenCount ?? '?')
        + ' answerTokens=' + (usage.candidatesTokenCount ?? '?')
        + ' | raw starts: ' + String(text).slice(0, 200).replace(/\s+/g, ' ');
    }
  } else {
    modelUsed = 'error';
    parseError = 'Vertex returned no candidates and no error object';
  }
}

// --- the guard ------------------------------------------------------------
const seen = new Set();
const kept = [];
let rejected = 0;

for (const p of picks) {
  const id = p && p.serviceId != null ? String(p.serviceId) : null;
  if (!id || !allowed.has(id) || seen.has(id)) { rejected++; continue; }
  seen.add(id);
  kept.push({
    serviceId: id,
    rank: kept.length + 1,
    reason: String(p.reason ?? '').slice(0, 300)
  });
  if (kept.length === 3) break;
}

// --- fallback: the protocol table answers when the model cannot ------------
let status = 'OK';
let recommendations = kept;

if (recommendations.length === 0) {
  status = 'FALLBACK';
  const indicated = built.allowedServices.filter(function (s) { return s.rule === 'INDICATED'; });
  const rest     = built.allowedServices.filter(function (s) { return s.rule !== 'INDICATED'; });
  recommendations = indicated.concat(rest).slice(0, 3).map(function (s, i) {
    return {
      serviceId: String(s.serviceId),
      rank: i + 1,
      reason: s.rationale || 'Listed for this assessment in the spa’s own service protocol.'
    };
  });
}

return [{ json: {
  formId: built.formId,
  status: status,
  recommendations: recommendations,
  modelUsed: modelUsed,
  rejectedCount: rejected,
  parseError: parseError,
  generatedAt: now
} }];
