/**
 * n8n Code node — "Shape transcript"
 *
 * Between the Vertex HTTP Request node and Respond to Webhook. Spring expects
 * exactly { "transcript": "..." } and nothing else.
 *
 * Everything here is defensive on purpose: a transcript that comes back as
 * undefined must become an empty string, not the word "undefined" sitting in
 * the client's message box waiting to be sent to the assistant.
 */

const r = $input.first().json ?? {};

const parts = r?.candidates?.[0]?.content?.parts ?? [];
let text = parts.map(p => (typeof p?.text === 'string' ? p.text : '')).join('').trim();

// The model was told not to, but strip a wrapping quote if one appears -
// a quoted sentence in the message box is a sentence the client did not say.
if (text.length > 1 && text.startsWith('"') && text.endsWith('"')) {
  text = text.slice(1, -1).trim();
}

// Same ceiling Spring applies. Twenty seconds of speech is nowhere near this;
// anything longer means something went wrong upstream.
return [{ json: { transcript: text.slice(0, 1000) } }];
