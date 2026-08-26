# Viaduct Blogging App

A full-stack blogging application with a Kotlin/Viaduct GraphQL backend and a React/TypeScript frontend.

## Requirements

- Java JDK 21
- Node.js + npm
- `jq` — used by `query-tests.sh` to read JSON responses
- (Optional) Podman + `podman compose` — for containerised deployment
- (Optional) `psql` — only needed to run `seed-database.sh` manually
- (Optional) Ollama — for AI features (rephrase, content suggestions)

## Quick Start

```bash
./start.sh
```

Then open **http://localhost:5173** in your browser.

> **AI features** (rephrase, content suggestions) require Ollama to be installed — see [Ollama Setup](#ollama-setup) below. The app runs fine without it; the AI controls simply become unavailable.

---

## Ollama Setup

[Ollama](https://ollama.com) runs LLMs locally and powers the AI features in the app: rephrasing blog post content in different tones, and suggesting the next item on a checklist. Without it the app works normally — the rephrase controls stay visible but disabled with an "Ollama offline" label, and the checklist "✨ Suggest" button is hidden entirely.

### Install

**macOS (Homebrew):**
```bash
brew install ollama
```

**macOS (app):** Download from **https://ollama.com/download** and drag to Applications.

**Linux:**
```bash
curl -fsSL https://ollama.com/install.sh | sh
```

### Pull the required models

```bash
ollama pull llama3.2           # chat model — used for rephrase
ollama pull nomic-embed-text   # embedding model — used for recommendations
```

> **Note:** `start.sh` pulls these automatically on first run if they are not already present, so you can skip this step.

### Verify Ollama is working

```bash
ollama list                    # should show llama3.2 and nomic-embed-text
curl http://localhost:11434/api/tags   # should return JSON with the model list
```

### Environment variables

| Variable | Default | Description |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL (override if running remotely) |
| `OLLAMA_CHAT_MODEL` | `llama3.2` | Model used for rephrase and suggestions |
| `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text` | Model used for embeddings |

> **Containerised deployment:** When using `start-containers.sh`, Ollama runs as a container automatically — no local install needed. The models are pulled inside the container on first run.

---

## Shell Scripts

### `start.sh` — local development

Builds the backend, installs frontend dependencies (if missing), then starts both servers in the background. If [Ollama](#ollama-setup) is installed, it also starts `ollama serve` (if not already running) and pulls the required models on first run. Logs stream to `server.log`, `frontend-dev.log`, and (if Ollama was started by the script) `ollama.log`. Press **Ctrl+C** to stop everything cleanly.

```
Backend:  http://localhost:8080
  GraphQL:    http://localhost:8080/graphql
  GraphiQL:   http://localhost:8080/graphiql?path=/graphql
  Auth:       http://localhost:8080/auth/*
  Health:     http://localhost:8080/health
  AI Health:  http://localhost:8080/health/ai
  Metrics:    http://localhost:8080/metrics
Frontend: http://localhost:5173
Ollama:   http://localhost:11434  (if installed)
```

### `start-containers.sh` — containerised deployment

Runs the full stack (PostgreSQL + Ollama + backend + frontend) in containers via Podman Compose. On first run it pulls the PostgreSQL and Ollama base images (~1.5 GB for Ollama), builds the Gradle distribution (`installDist`), builds both application images, starts all four services, waits for them to be healthy, pulls the Ollama models, then seeds the database with sample data. Ctrl+C shuts everything down cleanly.

> **First run takes a while.** The base-image pull happens before the health check starts, so a slow download can't trip the timeout. The backend wait allows 10 minutes by default — override with `BACKEND_TIMEOUT_SECS` if your machine needs longer.

**Prerequisites:** Podman must be installed and its VM must be running (`podman machine start`). The script will start the VM automatically if it is stopped. `psql` is required only if you run `seed-database.sh` manually — the startup script handles seeding automatically.

#### First-time setup

```bash
cp .env.example .env
# Edit .env — set strong values for POSTGRES_PASSWORD and JWT_SECRET
```

| Variable | Default | Notes |
|---|---|---|
| `POSTGRES_DB` | `blog` | Database name |
| `POSTGRES_USER` | `blog` | Database user |
| `POSTGRES_PASSWORD` | *(required)* | Set a strong password |
| `JWT_SECRET` | *(required)* | Set a random secret for signing JWTs |
| `CORS_ORIGIN` | `localhost:5173` | Allowed CORS origin for the backend |

> **Note:** `VITE_API_URL` (`http://localhost:8080`) is baked into the frontend JS bundle at image build time. If you expose the backend on a different host or port, rebuild the frontend image with `--build-arg VITE_API_URL=<url>`.

#### Start everything

```bash
./start-containers.sh
```

Once healthy, services are available at:

| Service | URL |
|---|---|
| Frontend | http://localhost:5173 |
| Backend / GraphQL | http://localhost:8080/graphql |
| GraphiQL | http://localhost:8080/graphiql?path=/graphql |
| Health check | http://localhost:8080/health |
| Prometheus metrics | http://localhost:8080/metrics |
| PostgreSQL | localhost:5432 |

#### Viewing logs

```bash
tail -f docker-compose.log          # all container output
docker compose logs -f app          # backend only
docker compose logs -f frontend     # frontend only
```

#### Rebuilding after code changes

```bash
./gradlew installDist -x test       # rebuild the backend distribution
docker compose up --build app       # rebuild and restart the backend container only
docker compose up --build frontend  # rebuild and restart the frontend container only
```

#### Stopping

Ctrl+C in the `start-containers.sh` terminal calls `docker compose down` automatically. To stop without the script:

```bash
docker compose down           # stop and remove containers (data volume is preserved)
docker compose down -v        # also remove the postgres data volume (wipes the database)
```

### `e2e.sh` — full browser E2E test suite

Starts the backend and frontend, waits for them to be ready, runs the full Playwright test suite across Chromium, Firefox, and WebKit (120 test runs), then shuts everything down. Suitable for CI and pre-push checks.

```bash
./e2e.sh
```

### `query-tests.sh` — API E2E tests

Runs 38 curl-based tests against the live GraphQL and auth endpoints. The backend must already be running before calling this script.

```bash
./gradlew run &   # start backend first
./query-tests.sh
```

### `seed-database.sh` — populate the database

Inserts sample data into the PostgreSQL database. The app must have been started at least once so the schema exists (it is created automatically on startup). Connection is configured via environment variables (defaults match `.env.example`):

```bash
# When running via start-containers.sh, this is called automatically.
# To run manually against the docker-compose postgres container:
source .env
PGHOST=localhost PGPORT=5432 \
    PGDATABASE="${POSTGRES_DB}" PGUSER="${POSTGRES_USER}" PGPASSWORD="${POSTGRES_PASSWORD}" \
    ./seed-database.sh
```

Creates:
- **5 users** — `alice`, `bob`, `charlie`, `admin` (password `password123`; `admin` has admin privileges) and `e2e_admin` (password `e2eAdminPass1`)
- **15 blog posts** — 12 published (4 per regular user, with rich HTML content) and 3 drafts, one per regular user
- **4 checklists** — 3 published (4, 7 and 9 items) and 1 draft (3 items)
- **17 comments** and **22 likes** spread across posts
- **9 post-view rows**, so `trending` has something to rank

Drafts are visible only to their author and to admins, so `alice`, `bob` and `charlie` each have
one waiting in **My Posts**. Bob's is the interesting case: it was published, gathered comments,
likes and views, and was then unpublished — unpublishing hides engagement rather than deleting it,
so that state is worth being able to see locally.

Published posts get staggered publication times an hour apart. The feed orders by `published_at`,
and identical timestamps would leave that `ORDER BY` without a tiebreaker, so a paged feed could
repeat or skip a post.

If the database already has data it will prompt before clearing it.

---

## Running Tests

```bash
./gradlew test                    # backend unit + integration tests
cd frontend && npm test           # frontend unit tests (Vitest)
./query-tests.sh                  # API E2E tests (starts its own server)
./e2e.sh                          # Playwright browser tests (starts its own servers)
cd frontend && npm run test:e2e   # Playwright only (servers must already be running)
```

Current counts live in `TODO.md`, which is updated as part of finishing a phase.

---

## Markdown Files

### `README.md` — this file

Project overview, requirements, script reference, and links to other docs.

### `TODO.md` — feature phases and roadmap

Tracks every implementation phase with task checklists, technical approaches, and success criteria, along with the current test counts. Which phases are done and which is next is recorded there rather than repeated here.

### `CLAUDE.md` — AI assistant instructions

Instructions for Claude Code describing project conventions, coding standards, testing requirements, and architecture decisions. Not intended for human readers.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│  Frontend (React 19 + TypeScript + Apollo Client)   │
│  Port 5173                                          │
└───────────────────────┬─────────────────────────────┘
                        │ HTTP
┌───────────────────────▼─────────────────────────────┐
│  Backend (Kotlin + Viaduct + Ktor)  Port 8080       │
│  /graphql  — GraphQL API                            │
│  /auth/*   — REST auth endpoints (register, login)  │
│  /health   — health check with DB connectivity      │
│  /metrics  — Prometheus metrics                     │
└───────────────────────┬─────────────────────────────┘
                        │ Exposed ORM
┌───────────────────────▼─────────────────────────────┐
│  SQLite (dev) / PostgreSQL (prod)                   │
└─────────────────────────────────────────────────────┘
```

**Key decisions:**
- Single Ktor server on port 8080 — auth routes and GraphQL share one process, no CORS complexity
- Schema-first: `src/main/viaduct/schema/schema.graphqls` is the source of truth; Viaduct generates resolver base classes
- Repository pattern — all database access is behind interfaces; resolvers never touch the DB directly
- Koin dependency injection — `KoinTenantCodeInjector` bridges Viaduct's resolver instantiation with Koin
- JWT stored in `localStorage` as `authToken` + `authUser`
- Relay-style cursor pagination via `postsConnection(first, after)` with DB-level `LIMIT/OFFSET`
