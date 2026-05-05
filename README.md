# Viaduct Blogging App

A full-stack blogging application with a Kotlin/Viaduct GraphQL backend and a React/TypeScript frontend.

## Requirements

- Java JDK 21
- Node.js + npm
- (Optional) Podman + `podman compose` — for containerised deployment
- (Optional) `psql` — only needed to run `seed-database.sh` manually

## Quick Start

```bash
./start.sh
```

Then open **http://localhost:5173** in your browser.

---

## Shell Scripts

### `start.sh` — local development

Builds the backend, installs frontend dependencies (if missing), then starts both servers in the background. Logs stream to `server.log` and `frontend-dev.log`. Press **Ctrl+C** to stop everything cleanly.

```
Backend:  http://localhost:8080
  GraphQL:    http://localhost:8080/graphql
  GraphiQL:   http://localhost:8080/graphiql?path=/graphql
  Auth:       http://localhost:8080/auth/*
  Health:     http://localhost:8080/health
  Metrics:    http://localhost:8080/metrics
Frontend: http://localhost:5173
```

### `start-containers.sh` — containerised deployment

Runs the full stack (PostgreSQL + backend + frontend) in containers via Podman Compose. On first run it builds the Gradle distribution (`installDist`), builds both container images, starts all three services, waits for them to be healthy, then seeds the database with sample data. Ctrl+C shuts everything down cleanly.

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
- **4 users** — `alice`, `bob`, `charlie`, `admin` (all with password `password123`; `admin` has admin privileges)
- **12 posts** — 4 per regular user, with rich HTML content
- **15 comments** spread across posts
- **20 likes** spread across posts

If the database already has data it will prompt before clearing it.

---

## Running Tests

```bash
./gradlew test                    # 196 backend unit + integration tests
cd frontend && npm test           # 26 frontend unit tests (Vitest, runs in <1s)
./query-tests.sh                  # 38 API E2E tests (backend must be running)
./e2e.sh                          # 120 Playwright browser tests (starts servers automatically)
cd frontend && npm run test:e2e   # Playwright only (servers must already be running)
```

### Test statistics

| Suite | Count | Status |
|---|---|---|
| Unit + Integration (`./gradlew test`) | 196 | All passing |
| Frontend unit tests (`npm test`) | 26 | All passing |
| API E2E (`./query-tests.sh`) | 38 | All passing |
| Browser E2E (Playwright, 40 tests × 3 browsers) | 120 runs | All passing |

---

## Markdown Files

### `README.md` — this file

Project overview, requirements, script reference, and links to other docs.

### `TODO.md` — feature phases and roadmap

Tracks every implementation phase with task checklists, technical approaches, and success criteria. Phases 1–19 are complete. Currently tracks:

- **Phase 20** (next): Analytics frontend — view tracking UI, trending sort, admin analytics dashboard
- **Phases 21–23**: CheckedList frontend

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
