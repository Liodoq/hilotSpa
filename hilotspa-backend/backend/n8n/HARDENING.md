# Hardening n8n

Three things stand between the current dev setup and something you can leave
running while a real client record exists. Do them in this order — each one is
independent, but the backup exists so that a mistake in step 2 or 3 costs you
nothing.

Run every command from `hilotspa-backend/backend` (the folder with
`compose.yaml`) in **PowerShell**, with the stack up.

---

## 1 — Back up the n8n volume (task 0.15)

**Why first.** Everything else in this project can be rebuilt from git: the
schema from Flyway, the seed data from the seeder, the workflow logic from
`hilotspa-*.workflow.json`. Two things cannot: the **Vertex AI service-account
credential** you pasted into n8n, and the **published state** of the two
workflows. Both live only inside the Docker volume `backend_n8n_data`. Delete
that volume — or run `docker compose down -v` once by reflex — and the assistant
is dead until someone in GCP issues a new key.

The volume is named after the compose project, which is the folder name, so it
is `backend_n8n_data`. Confirm:

```powershell
docker volume ls | Select-String n8n
```

Then take the backup:

```powershell
docker run --rm -v backend_n8n_data:/data -v ${PWD}:/backup alpine tar czf /backup/n8n_data.tgz -C /data .
```

Check it is real — a few hundred KB at least, not 45 bytes:

```powershell
Get-Item n8n_data.tgz | Select-Object Name, Length, LastWriteTime
```

`n8n_data.tgz` and `*.tgz` are already in `.gitignore`. **Leave them there.**
The archive contains the encrypted Vertex credential; committing it would put a
Google service-account key in a public repository.

Keep the file somewhere off this laptop — a flash drive or a private drive
folder. A backup that only exists on the machine that dies with it is not a
backup.

> **STOP.** Do not go on until `n8n_data.tgz` exists and is a plausible size.

### Restoring (do not run this now — this is the recipe for the bad day)

```powershell
docker compose down
docker volume rm backend_n8n_data
docker volume create backend_n8n_data
docker run --rm -v backend_n8n_data:/data -v ${PWD}:/backup alpine sh -c "tar xzf /backup/n8n_data.tgz -C /data"
docker compose up -d
```

The restore only decrypts if `N8N_ENCRYPTION_KEY` in `.env` is the same value it
was when the backup was taken. That key is the other half of the backup — if you
ever rebuild `.env` from `.env.example`, the archive becomes unreadable.

---

## 2 — Pin the n8n image (task 0.13)

`compose.yaml` currently says:

```yaml
image: docker.n8n.io/n8nio/n8n:latest
```

`:latest` means "whatever n8n published most recently". The next time you pull —
which includes any `docker compose up --build` after a cache expiry, and any
rebuild on a different machine — you may get a different major version. n8n 2.x
changed node behaviour and credential handling more than once. Finding that out
the morning of a defence, from a workflow that opens but will not run, is a bad
way to find it out.

Read the version you are actually running now:

```powershell
docker exec hilotspa_n8n n8n --version
```

Take that number — say it prints `2.0.4` — and edit `compose.yaml`, changing
only that one line:

```yaml
image: docker.n8n.io/n8nio/n8n:2.0.4
```

Then:

```powershell
docker compose up -d n8n
```

It should say the container is up to date or recreate it in a second or two. It
must **not** download a new image — if it does, you typed a different version
than the one running.

> **STOP.** Open http://localhost:5678, confirm both workflows are still there
> and still show **Active**. Then submit one assessment from the app end to end.

Upgrading later is now a deliberate act: change the tag, back up first, test.
That is the point.

---

## 3 — Close the webhooks (task 2.17)

Right now anything that can reach port 5678 can drive the assistant and spend
the project's Vertex credits. Your backend already tells you this — it logs
`n8n webhooks are UNAUTHENTICATED` at every startup, and it will keep logging it
until you finish this step.

Full instructions with screenshots-worth of detail are in `SETUP.md`, **Step
15**. The short version, in the safe order:

**3a. Generate the secret on your own machine.** Never type a secret into a chat
window, a screenshot, or a commit.

```powershell
$b = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
[Convert]::ToBase64String($b).TrimEnd('=').Replace('+','-').Replace('/','_')
```

(There is no Python on this machine and there should not be — it is not in the
stack. The line above is the .NET crypto RNG, which is what `secrets` uses on
Windows anyway. If you would rather use Node, which you already have for
Angular: `node -e "console.log(require('crypto').randomBytes(32).toString('base64url'))"`)

**3b. Append it to `.env`** (gitignored) — these two keys are missing from your
`.env` entirely at the moment:

```
N8N_WEBHOOK_AUTH_HEADER=X-HilotSpa-Key
N8N_WEBHOOK_SECRET=<the value you just generated>
```

Then `docker compose up -d --build backend`.

Do this **before** touching n8n. The backend will start sending the header
immediately; n8n ignores a header it is not checking for, so nothing breaks and
there is no window where the two sides disagree. Doing it the other way round
takes the assistant down between the two edits.

**3c. Create the credential in n8n.** Sidebar → **Credentials** → **Add
credential** → **Header Auth**.

- Name: `HilotSpa Webhook Key`
- Header Name: `X-HilotSpa-Key`
- Header Value: the same value

**3d. Attach it to BOTH Webhook nodes.** Open **HilotSpa Recommend** → click the
**Webhook** node → **Authentication: Header Auth** → choose the credential →
**Save** → **Publish**. Repeat for **HilotSpa Chat**.

**Publish is not Save.** A saved-but-unpublished workflow answers 404. This has
cost you an evening before.

**3e. Prove it is closed.** Call the webhook with no header:

```powershell
curl.exe -i -X POST http://localhost:5678/webhook/hilotspa/chat -H "Content-Type: application/json" -d "{}"
```

**403 is the correct answer** and is worth a screenshot for the appendix. n8n
phrases it as `Authorization data is wrong!` — that string is the webhook
refusing an unauthenticated caller, which is the whole point of this step. Use
`-i` so `HTTP/1.1 403 Forbidden` is in the same frame as the message; the status
line is the evidence, the body text is only the explanation.

A 200 means the node is not using the credential. A 404 means you did not
Publish.

**3f. Confirm the app still works.** Submit an assessment and send one chat
message. Then restart the backend and check the startup log: the
`UNAUTHENTICATED` warning should be gone.

> **STOP.** If the assistant now returns FALLBACK, the secret in `.env` and the
> one in the credential do not match. Compare them character by character; a
> trailing space from a copy-paste is the usual culprit.

---

## What this does not fix

Say these out loud in the defence before a panellist says them to you.

- **Port 5678 is still published to the host.** Header auth stops an unauthorised
  caller from *running* a workflow; it does not hide the n8n editor. Before this
  is deployed anywhere with a real address, remove the `ports:` block from the
  `n8n` service entirely — the backend reaches it at `http://n8n:5678` over the
  compose network and does not need the published port. You would then reach the
  editor through an SSH tunnel.
- **The transport is plain HTTP.** On one laptop that is fine. Across a network
  the header is readable in transit; that is a TLS problem, and TLS is task
  0.22 in the deployment block.
- **Prompt injection is unsolved.** The client's free-text complaint reaches the
  model. The mitigations are the system prompt, the absence of tools, and the
  fact that the agent cannot write to anything.
