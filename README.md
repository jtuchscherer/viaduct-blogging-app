# Viaduct Blogging App

A full-stack blogging application with a Kotlin/Viaduct GraphQL backend and a React/TypeScript frontend.

## Requirements

- Java JDK 21
- Node.js + npm
- (Optional) Docker + Docker Compose — for containerised deployment

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

### `start-containers.sh` — Docker deployment

Builds the Gradle distribution (`installDist`), starts all three containers (postgres, app, frontend) via `docker compose`, waits for both to be healthy, then seeds the database. Ctrl+C calls `docker compose down`.

Before running for the first time, create your `.env` file from the example:

```bash
cp .env.example .env
# edit .env and set strong values for POSTGRES_PASSWORD and JWT_SECRET
```

Then start everything:

```bash
./start-containers.sh
```

Useful commands while running:
```bash
tail -f docker-compose.log          # all container output
docker compose logs -f app          # backend only
docker compose logs -f frontend     # frontend only
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
./query-tests.sh                  # 38 API E2E tests (backend must be running)
./e2e.sh                          # 120 Playwright browser tests (starts servers automatically)
cd frontend && npm run test:e2e   # Playwright only (servers must already be running)
```

### Test statistics

| Suite | Count | Status |
|---|---|---|
| Unit + Integration (`./gradlew test`) | 196 | All passing |
| API E2E (`./query-tests.sh`) | 38 | All passing |
| Browser E2E (Playwright, 40 tests × 3 browsers) | 120 runs | All passing |

---

## Markdown Files

### `README.md` — this file

Project overview, requirements, script reference, and links to other docs.

### `DEVELOPMENT_PLAN.md` — architecture and design

High-level design document covering:
- Database schema (Users, Posts, Comments, Likes tables)
- Full GraphQL schema (types, queries, mutations)
- React frontend structure (pages, components, routing)
- Authentication system design (JWT, REST auth endpoints, Apollo middleware)
- Implementation roadmap (phases 1–6, all complete)
- Current status summary for backend and frontend

This is the reference document for understanding the overall architecture.

### `TODO.md` — feature phases and roadmap

Tracks every implementation phase with task checklists, technical approaches, and success criteria. Phases 1–15, 17–18, and 10 are complete. Currently tracks:

- **Phase 16** (next): PostgreSQL/RDS support, HikariCP connection pooling, Flyway migrations
- UI bug fix status
- Tech debt items

### `CODE_QUALITY_PLAN.md` — tech debt and code quality

Findings from a Clean Code / SOLID / security review of the codebase, organised by priority:
- **Bugs** (all resolved): duplicate username check, crash on deleted user, raw HTML in post excerpts
- **SOLID & Clean Code** (all resolved): SRP auth route extraction, DRY auth helper, domain exceptions, shared TypeScript interfaces
- **Test quality** (all resolved): behavior-based assertions, removed relaxed mocks, edge case coverage
- **Security**: input validation and length limits done; rate limiting on auth endpoints still open

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
