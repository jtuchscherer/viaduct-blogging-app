# AI Integration Plan — Viaduct Blogging App

**Last Updated**: 2026-05-19

## Status: 🚀 In Progress

Implementation is proceeding now with the Kotlin + LangChain4j stack. The remote resolver showcase (Python service communicating over gRPC) remains the long-term vision and will be layered on top once Cetin's upstream work lands:

| Future upgrade | Owner | Status |
|---|---|---|
| Network transport for remote resolvers | Cetin Sahin | PR open: airbnb/treehouse#1038272 |
| `proxyField` transport (proxy mutations/queries, not just node resolvers) | Cetin Sahin | Not yet started |

When both land, the Kotlin AI resolvers can be moved into a Python service and proxied via remote resolvers with minimal changes to the GraphQL schema or frontend — the interface stays identical.

---

## Overview

Three AI features powered by a local LLM running via [Ollama](https://ollama.com/) — no API key, no cost, fully offline:

1. **Rephrase blog post content** — AI button in the post editor rewrites the content in a chosen tone
2. **Checklist item auto-suggestion** — generates the next checklist item based on ≥ 3 existing items
3. **Post recommendation engine** — embedding-based "You might like" feed personalised per user

Implementation spans four phases: **Phase 24** (AI foundation) → **Phase 25** (rephrase) → **Phase 26** (checklist suggestion) → **Phase 27** (recommendations). Phase 26 frontend depends on Phase 23 (CheckedList frontend); its backend can be developed in parallel.

---

## Technology Choices

### LLM Runtime — Ollama

Ollama runs open-weight models locally. No API key, no billing, no data leaving the machine.

Recommended models:
| Model | Use | Size |
|---|---|---|
| `llama3.2` (3B) | Chat completions (rephrase, suggestions) | ~2 GB |
| `nomic-embed-text` | Semantic embeddings (recommendations) | ~270 MB |

### Backend Library — LangChain4j

[LangChain4j](https://docs.langchain4j.dev/) with `langchain4j-ollama`. Chosen over Koog because:
- Koog is a full agentic orchestration framework — overkill for three discrete prompt calls
- LangChain4j has first-class Kotlin support and the strongest Ollama integration
- Mature, well-documented, simple to test with stub implementations

### Observability — Tracy

[Tracy](https://blog.jetbrains.com/kotlin/2026/03/introducing-tracy-the-ai-observability-library-for-kotlin/) — JetBrains' AI observability library for Kotlin. Wraps every LLM call and records:
- Prompt / response content
- Latency
- Token counts
- Model name

Slots naturally into the existing Micrometer + structured JSON logging stack.

### New Module — `:modules:ai`

Follows the same structure as `:modules:analytics` and `:modules:checkedlist`. Keeps AI infrastructure isolated and independently testable.

---

## Developer Setup

```bash
# Install Ollama
brew install ollama       # macOS
# Linux: curl -fsSL https://ollama.com/install.sh | sh

# Pull required models
ollama pull llama3.2            # chat model (~2 GB)
ollama pull nomic-embed-text    # embedding model (~270 MB)

# Start the Ollama server (runs on :11434)
ollama serve
```

The app starts and runs fully without Ollama. AI buttons degrade gracefully via the `/health/ai` endpoint — they are hidden when Ollama is unreachable.

---

## Phase 24 — AI Foundation & Infrastructure

**Goal**: shared plumbing so each feature phase is just "add a resolver + prompt."

### Backend

1. Create `:modules:ai` Gradle module (mirror structure of `:modules:analytics`)
2. Add dependencies to the new module:
   - `dev.langchain4j:langchain4j-ollama`
   - `dev.langchain4j:langchain4j` (core)
   - Tracy AI observability library
3. `OllamaConfig` data class — `baseUrl`, `chatModel`, `embeddingModel`; read from `application.conf` / env vars:
   ```
   OLLAMA_BASE_URL=http://localhost:11434
   OLLAMA_CHAT_MODEL=llama3.2
   OLLAMA_EMBEDDING_MODEL=nomic-embed-text
   ```
4. `AIService` interface:
   ```kotlin
   interface AIService {
       fun rephrase(content: String, tone: RephraseTone): String
       fun suggestNextItem(existingItems: List<String>): String
       fun generateEmbedding(text: String): FloatArray
       fun isReachable(): Boolean
   }
   ```
5. `OllamaAIService` — real implementation via LangChain4j, Tracy-instrumented
6. `NoOpAIService` — deterministic stubs; used in tests so no Ollama daemon is required
7. Wire into Koin `aiModule`; include in `AppConfig`
8. `/health/ai` endpoint:
   ```json
   { "ollamaReachable": true, "chatModel": "llama3.2", "embeddingModel": "nomic-embed-text" }
   ```
   Frontend polls this once on load to decide whether to show AI controls.

### Tests

- Unit tests for `OllamaConfig` env-var parsing
- Unit tests for `NoOpAIService` stubs
- Integration test for `/health/ai` with a mock Ollama HTTP response

### Files

| File | Action |
|---|---|
| `modules/ai/build.gradle.kts` | Create |
| `modules/ai/src/main/kotlin/.../ai/AIService.kt` | Create |
| `modules/ai/src/main/kotlin/.../ai/OllamaAIService.kt` | Create |
| `modules/ai/src/main/kotlin/.../ai/NoOpAIService.kt` | Create |
| `modules/ai/src/main/kotlin/.../ai/OllamaConfig.kt` | Create |
| `src/main/kotlin/.../config/KoinModules.kt` | Add `aiModule` |
| `src/main/kotlin/.../web/GraphQLServer.kt` | Add `/health/ai` route |

---

## Phase 25 — Rephrase Blog Post Content

**Goal**: "Rephrase with AI ✨" button in the blog post editor.

### Schema

```graphql
enum RephraseTone {
  PROFESSIONAL
  CASUAL
  CONCISE
}

type RephraseResult {
  rephrasedContent: String!
}

extend type Mutation {
  rephraseContent(content: String!, tone: RephraseTone): RephraseResult! @resolver
}
```

### Backend

1. `RephraseContentMutationResolver` — auth-required; validates content is non-blank and ≤ 50 000 chars; calls `AIService.rephrase(content, tone)`
2. Prompt template stored as a resource file (not hardcoded):
   > *You are a writing assistant. Rewrite the following blog post content to be {{tone}}. Keep the same meaning and all factual details. Return only the rewritten text, no commentary or preamble.*
   >
   > *{{content}}*
3. Register resolver in Koin `aiModule`

### Frontend (`EditPostPage.tsx`)

- "Rephrase with AI ✨" button above the Lexical editor — only shown when post has content
- Tone selector: Professional (default) / Casual / Concise
- Loading spinner while mutation is in-flight; button disabled during this time
- On success: update Lexical editor state with rephrased content
- Graceful degradation: button hidden (with tooltip "AI unavailable — Ollama offline") when `/health/ai` returns `ollamaReachable: false`

### Tests

- Resolver: blank content → validation error; too-long content → validation error; valid → returns stub
- Auth: unauthenticated call → `AuthenticationException`
- Frontend Playwright: button appears in edit page, spinner shows on click, editor content updates

---

## Phase 26 — Checklist Item Auto-suggestion

**Goal**: "Suggest next item" button when editing a checklist post with ≥ 3 items.

**Note**: Backend can be developed in parallel with Phase 23. Frontend integration waits on Phase 23 (CheckedList frontend) being complete.

### Schema (extends `modules/checkedlist`)

```graphql
type SuggestedChecklistItem {
  suggestedText: String!
}

extend type Mutation {
  suggestChecklistItem(existingItems: [String!]!): SuggestedChecklistItem! @resolver
}
```

### Backend

1. `SuggestChecklistItemMutationResolver` — auth-required; validates `existingItems.size >= 3` (returns `UserError` if fewer); calls `AIService.suggestNextItem(items)`
2. Prompt template:
   > *Here is a checklist:*
   > *{{items}}*
   >
   > *Suggest one more item that fits naturally in this list. Return only the item text, nothing else.*
3. Register in Koin `aiModule`

### Frontend (CheckedList edit UI — Phase 23)

- "+ Suggest item" button below the item list
- Enabled only when `items.size >= 3`; otherwise shows tooltip "Add at least 3 items to enable AI suggestions"
- Clicking calls the mutation; spinner on the button during the call
- On success: appends the suggested item as a new (editable, deletable) row
- Hidden when Ollama is offline

### Tests

- Resolver: 2 items → error; 3 items → returns stub suggestion; 0 items → error
- Auth: unauthenticated call → `AuthenticationException`
- Boundary: exactly 3 items should succeed

---

## Phase 27 — Post Recommendation Engine

**Goal**: personalised "You might like" panel on the home feed.

### Approach: Embedding-based Semantic Similarity

When a post is created or updated, generate a vector embedding of its title + content and store it. When recommendations are requested, find the centroid of embeddings for posts the user has interacted with, rank all other posts by cosine similarity, return the top N.

Falls back to the existing `trending` query for users with no interaction history or when Ollama is offline.

### Database

New Flyway migration — `V_ai_1__post_embeddings.sql`:

```sql
CREATE TABLE post_embeddings (
  post_id     TEXT      PRIMARY KEY,
  embedding   TEXT      NOT NULL,   -- JSON-serialised float array
  model_name  TEXT      NOT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

JSON-serialised floats are portable across SQLite and PostgreSQL. Migrating to `pgvector` for production scale is noted as a future optimisation in `DEVELOPMENT_PLAN.md`.

### Backend

1. `PostEmbeddingRepository` — `upsertEmbedding(postId, vector)`, `getEmbedding(postId)`, `getAllEmbeddings()`
2. `EmbeddingService` — wraps LangChain4j `EmbeddingModel`; Tracy-instrumented; `generateEmbedding(text): FloatArray`
3. **Embedding generation hook**: after `createPost` / `updatePost` succeed, fire `EmbeddingService.generateEmbedding(title + " " + content)` asynchronously (Kotlin coroutine, non-blocking), persist via `PostEmbeddingRepository`
4. `RecommendationService`:
   - Fetch posts the user has liked or viewed (from likes + analytics tables)
   - Load their embeddings; compute centroid
   - Cosine-similarity rank all other post embeddings
   - Exclude posts the user authored or already viewed
   - Return top-N post IDs
5. Schema:
   ```graphql
   extend type Query {
     recommendedPosts(limit: Int): [Post!]! @resolver
   }
   ```
6. `RecommendedPostsQueryResolver` — auth-required; calls `RecommendationService`; falls back to `trending` when user has no history

### Frontend

- `RecommendationsPanel` component on the home feed sidebar: "Recommended for you"
- Shows 3–5 `PostCard` components
- Skeleton loader while fetching
- Falls back to "Trending" label + content when user has no history or Ollama is offline

### Tests

- `RecommendationService` unit test: mock repositories + embedding service; verify cosine-similarity ranking order
- Repository integration test: H2 in-memory; upsert and read embedding round-trip
- Fallback test: user with zero viewed posts → trending posts returned
- Playwright: view several posts as a user, reload homepage → recommended panel appears with at least one of those posts' related content

---

## Implementation Order

```
Phase 24 (foundation)
    │
    ├─→ Phase 25 (rephrase)          ← quickest win, ~2–3 days
    │
    ├─→ Phase 26 backend (suggestion) ← parallel with Phase 23
    │       │
    │   Phase 23 (checklist frontend)
    │       │
    │       └─→ Phase 26 frontend
    │
    └─→ Phase 27 (recommendations)   ← most complex, ~5–7 days
```

---

## Summary of New Files

| File | Phase | Action |
|---|---|---|
| `modules/ai/build.gradle.kts` | 24 | Create |
| `modules/ai/src/.../ai/AIService.kt` | 24 | Create |
| `modules/ai/src/.../ai/OllamaAIService.kt` | 24 | Create |
| `modules/ai/src/.../ai/NoOpAIService.kt` | 24 | Create |
| `modules/ai/src/.../ai/OllamaConfig.kt` | 24 | Create |
| `modules/ai/src/.../ai/EmbeddingService.kt` | 27 | Create |
| `modules/ai/src/.../ai/RecommendationService.kt` | 27 | Create |
| `modules/ai/src/.../ai/PostEmbeddingRepository.kt` | 27 | Create |
| `modules/ai/src/main/viaduct/schema/ai.graphqls` | 25 | Create |
| `modules/ai/src/main/resources/db/migration/V_ai_1__post_embeddings.sql` | 27 | Create |
| `src/main/kotlin/.../config/KoinModules.kt` | 24 | Extend with `aiModule` |
| `src/main/kotlin/.../web/GraphQLServer.kt` | 24 | Add `/health/ai` route |
| `frontend/src/pages/EditPostPage.tsx` | 25 | Add rephrase button + tone selector |
| `frontend/src/components/RecommendationsPanel.tsx` | 27 | Create |
| `frontend/src/hooks/useAIHealth.ts` | 24 | Create — polls `/health/ai` |
