# CareerPilot OS

CareerPilot is an AI-assisted career platform: profile & resume intelligence, real job discovery,
explainable job matching, application tracking with an auditable state machine, HR-email
understanding, follow-up drafting with approval-gated sending, an AI interview coach, cover
letters, learning plans, analytics and a tool-using Career Copilot.

**Design principles**

- **Real AI, explicit failures.** AI features call a real model (Ollama locally, or OpenAI /
  Anthropic / Gemini). If the model is unavailable the API returns an explicit error
  (`AI_PROVIDER_NOT_CONFIGURED`, `AI_PROVIDER_UNAVAILABLE`, …); nothing is replaced by canned output.
- **AI is not the source of truth.** The model extracts, classifies, explains and drafts. Domain
  rules decide: application state changes go through one lifecycle authority, scores are
  computed deterministically, emails are only sent after explicit approval.
- **No fabricated data.** Unknown values stay unknown ("Not enough data", `null`), extracted facts
  are grounded against their source text, generated text is fact-checked for unsupported claims.
- **Identity from the session.** Every user-scoped operation uses the authenticated principal;
  vector search is filtered per candidate inside Qdrant.

## Architecture

```
Browser ─► Frontend (React 19, Vite, TanStack Query)
             │  /api/v1
             ▼
           Backend (Spring Boot 3, Java 21)  ── PostgreSQL (Flyway V1–V25), Redis (optional cache), MinIO / local disk
             │  service token
             ▼
           AI service (FastAPI)  ── LLM provider (Ollama | OpenAI | Anthropic | Gemini)
                                 └─ embeddings + Qdrant (per-candidate isolation)
```

| Part | Path | Highlights |
|---|---|---|
| Backend | `apps/backend` | auth/JWT, profile, resumes & versions, job discovery connectors, matching engine, applications + timeline (M22.4), HR communications (M22.2–22.4), follow-ups (M22.5–22.7), interview coach, cover letters, learning, analytics, agents, Copilot |
| AI service | `apps/ai-service` | provider gateway with structured output validation, 30+ tasks, prompt-injection guards, grounding, embeddings, Qdrant RAG |
| Frontend | `apps/frontend` | 16 pages, light/dark design system, honest empty/error states |
| Shared | `packages/*` | DTOs, domain events, connector SDK |
| Docs | `docs/` | `API_CONTRACT.md`, `AUDIT_2026-10.md`, architecture notes |

## Running locally on Windows (no Docker)

Prerequisites: Java 21, Node 20+, Python 3.11+ (`py` launcher), [Ollama](https://ollama.com).

```powershell
# one time: portable PostgreSQL, Python venv, frontend deps, backend build, Ollama models, secrets
powershell -ExecutionPolicy Bypass -File scripts\local\setup.ps1
# every time
powershell -ExecutionPolicy Bypass -File scripts\local\start.ps1   # then open http://localhost:5173
powershell -ExecutionPolicy Bypass -File scripts\local\stop.ps1
```

Everything is installed under `%LOCALAPPDATA%\careerpilot` (database, uploaded files, vector
store, logs, build output). Secrets are generated into `.env.local` (git-ignored); edit it to switch
AI providers. Local mode stores files on disk (`storage.type=local`) and runs Qdrant embedded.

> The default local model (`llama3.2`, 3B) runs on CPU. On a laptop CPU (measured: ~30 prompt
> tokens/s, ~5 output tokens/s) a resume or job analysis takes **2–5 minutes** and quality is modest;
> long documents are reduced to their relevant sections (`LLM_MAX_DOCUMENT_CHARS`, 9000 for Ollama).
> Timeouts are sized for this (`LLM_TIMEOUT_SECONDS=600`, backend `CAREERPILOT_AI_TIMEOUT_MS=660000`).
> For fast, better results set `LLM_PROVIDER=anthropic|openai|gemini` and the key.
>
> Job discovery stores thousands of real postings; each is embedded for semantic search on one
> low-priority background thread, and full AI analysis of a posting runs only when you open it.

## Running with Docker

```bash
cp .env.example .env      # fill in the secrets
docker compose up --build # open http://localhost
```

Ollama runs on the host by default (`host.docker.internal:11434`); use
`docker compose --profile ollama up --build` to run it in a container instead. Only the web
server (port 80) is published; MinIO console, Prometheus and Grafana are bound to localhost.

## AI providers

| Variable | Values |
|---|---|
| `LLM_PROVIDER` | `ollama` (default), `openai`, `anthropic`, `gemini` |
| `LLM_MODEL` | defaults: `llama3.2`, `gpt-4o-mini`, `claude-opus-5-5`, `gemini-2.5-flash` |
| `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` / `GEMINI_API_KEY` | provider keys |
| `EMBEDDING_PROVIDER` / `EMBEDDING_MODEL` | `ollama`/`nomic-embed-text` (default), `openai`, `gemini`, `sentence-transformers` |
| `AI_SERVICE_TOKEN` | shared secret between backend and AI service (required) |

## Optional integrations

- **Email sending (M22.7):** set `EMAIL_TOKEN_ENCRYPTION_KEY` and Gmail and/or Outlook OAuth client
  credentials. Users connect their mailbox in Settings (send-only scopes); refresh tokens are stored
  AES-GCM encrypted. Drafts are only sent after explicit approval, to contacts who emailed the user
  about that application.
- **n8n email ingestion:** set `N8N_INGESTION_SHARED_SECRET` to enable
  `POST /api/v1/communications/ingest/n8n`.
- **Job sources:** Greenhouse, Lever, Ashby, Remotive and We Work Remotely work without keys;
  Adzuna (`ADZUNA_APP_ID/KEY`) and Jooble (`JOOBLE_API_KEY`) need keys. Indeed and Wellfound have no
  official public API and are reported as unsupported.

## Tests

```bash
./gradlew :apps:backend:test                       # backend unit + integration tests (H2)
cd apps/ai-service && pytest                       # AI service unit tests (scripted test double)
CAREERPILOT_LIVE_AI=1 pytest -m live               # AI service tests against the real model
cd apps/frontend && npm run build                  # type-check + production build
python scratch/careerpilot_live_e2e.py             # end-to-end over HTTP against a running stack
```

If the repository lives in OneDrive, set `CAREERPILOT_BUILD_ROOT` to a folder outside it
(OneDrive locks files in `build/`).

## Milestones

M1–M21 foundation through career execution; M22 communication foundation; M22.2 n8n ingestion;
M22.3 AI HR classification; **M22.4** timeline & transitions on the single lifecycle authority with
provenance and out-of-order protection; **M22.5** follow-up recommendations; **M22.6** AI drafts;
**M22.7** approval-gated sending via OAuth mailboxes. See `docs/AUDIT_2026-10.md` for the audit that
drove the 2026-10 rework.
