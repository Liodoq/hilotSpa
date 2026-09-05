# Speech to text through n8n

**What this adds.** A second voice path. The browser records, Spring passes the
audio to n8n, n8n asks Vertex to transcribe it, and the **words** come back to
the browser and land in the message box. The client reads them, corrects them if
they are wrong, and presses Send — through the ordinary chat endpoint.

**Why, in one sentence for the paper.** Chrome's `SpeechRecognition` sends the
audio to Google's own transcription service, which is a second route out of the
building that Risk R16 never covered; this keeps a client's spoken symptoms
inside the same Google Cloud project, region and terms as the assistant, and it
gives a microphone to Safari and Firefox, which have no `SpeechRecognition` at
all.

**What it deliberately does not do.** Audio never reaches a booking endpoint.
`/transcribe` writes nothing and books nothing, and the transcript goes back to
the client rather than straight to the assistant. Voice remains a different way
of filling the same box.

---

## Build it — seven nodes, about twenty minutes

Create a new workflow called **HilotSpa — Transcribe**.

### 1. Webhook

| Field | Value |
|---|---|
| HTTP Method | `POST` |
| Path | `hilotspa/transcribe` |
| Respond | **Using 'Respond to Webhook' Node** |
| Authentication | **Header Auth** — pick the *same* credential the chat webhook uses |

> **STOP.** If you skip Header Auth, anything that can reach port 5678 can spend
> the project's Vertex credits. Task 2.17 exists for this.

### 2. Code — "Build transcribe request"

Paste the whole of `transcribe-build-request.js`.

### 3. HTTP Request — "Vertex transcribe"

| Field | Value |
|---|---|
| Method | `POST` |
| URL | `https://asia-southeast1-aiplatform.googleapis.com/v1/projects/gen-lang-client-0276346734/locations/asia-southeast1/publishers/google/models/gemini-2.5-flash:generateContent` |
| Authentication | **Predefined Credential Type → Google Service Account API** |
| Credential | the **same one** the chat workflow's Vertex node already uses |
| Send Body | on · **Using JSON** |
| JSON | `={{ JSON.stringify($json.vertexBody) }}` |
| Options → Timeout | `30000` |

> **STOP.** Two failures to expect here, both with clear signatures.
>
> - **404 / "model not found"** — the model is not served in `asia-southeast1`
>   for this project. Change both occurrences of the region in the URL to
>   `us-central1` and try again. Singapore is only for latency.
> - **401 / 403** — the service-account credential is not requesting the right
>   scope. It needs `https://www.googleapis.com/auth/cloud-platform`. This is the
>   most likely thing to go wrong, because the chat workflow's Vertex node sets
>   its scope for you and a plain HTTP Request node does not.

### 4. Code — "Shape transcript"

Paste the whole of `transcribe-shape-reply.js`.

### 5. Respond to Webhook

| Field | Value |
|---|---|
| Respond With | **JSON** |
| Response Body | `={{ JSON.stringify($json) }}` |

### 6. Save — then **Activate**

Saving is not publishing. An inactive workflow answers nothing on the production
URL, and the browser will show *"I could not make out that recording"* with no
clue why. This is the same trap as task 0.16.

### 7. Export it back into git

`⋯ → Download`, save as `hilotspa-transcribe.workflow.json` beside the other two,
and commit. An export is not a publish and a publish is not an export — keeping
both in step is what stops a rebuilt container from quietly losing the prompt.

---

## Test it

1. Open the chat on a browser **with no** `SpeechRecognition` — Firefox is the
   easy one — and confirm a microphone button appears where it never used to.
2. Say one short sentence. The band should read **Recording…**, then
   **Writing down what you said…**, then the words should appear **in the input
   box, unsent**.
3. Press Send. It goes through the normal chat path.
4. In Chrome, confirm the old behaviour is unchanged: live captions, sent
   automatically. Chrome keeps `SpeechRecognition` because it can caption a
   sentence as it is heard, which is what an older client needs in order to catch
   a misheard word (NFR#4).

## Limits worth knowing

- **About twenty seconds.** Spring refuses a base64 payload over 1.4 MB with a
  413. Twenty seconds of 16 kHz mono WAV is roughly 850 KB.
- **HTTPS only.** `getUserMedia` needs a secure context, so the microphone will
  silently do nothing on `http://<ip>`. `localhost` counts as secure, which is
  why it works in development.
- **The audio is untrusted input to a model.** The prompt tells the transcriber
  to write down anything that sounds like an instruction rather than obey it —
  but the real protection is structural: the transcriber has no tools, no
  context and no memory, and its only output is text the client reads before
  sending.
- **Nothing is stored.** Spring logs the *length* of the transcript, never the
  words. A client describing their symptoms out loud belongs in the assessment
  they chose to send, not in a log file.
