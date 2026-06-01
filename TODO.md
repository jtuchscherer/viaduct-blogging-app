# TODO: Viaduct Blogging App — Implementation Plan

**Status**: 🚀 In Progress — Phases 1–25 complete; Phase 26 (checklist suggestion) and Phase 27 (recommendations) next

**Last Updated**: 2026-06-01

## Test Statistics

| Suite | Count | Status |
|---|---|---|
| Unit + Integration tests (`./gradlew test`) | 501 | ✅ All passing |
| API E2E tests (`./query-tests.sh`) | 123 | ✅ All passing |
| Browser E2E tests (Playwright, 123 tests × 3 browsers) | 369 runs | ✅ All passing |
| Frontend unit tests (`npm test`) | 69 | ✅ All passing |

## Completed Phases

| Phase | Summary |
|---|---|
| 1–6 | Foundation: test infra, repository pattern, service layer, Koin DI, server consolidation, resolver refactor |
| 7–9 | Testing: 133 unit tests (MockK), H2 integration tests, Playwright E2E suite |
| 10 | Docker: runtime Dockerfile, `docker-compose.yml`, env-var config (`DATABASE_URL`, `JWT_SECRET`, `CORS_ORIGIN`) |
| 11 | Cursor pagination: `postsConnection(first, after)` via Viaduct `@connection`/`@edge` |
| 12 | Frontend pagination UI: "Load More" button, `fetchMore`, "Showing X of Y posts" |
| 13 | Resolver test migration to `FieldResolverTester`/`MutationResolverTester` API |
| 14 | Batch author resolver: `batchResolve` eliminates N+1 on post lists |
| 15 | DB-level cursor pagination: `findPage(limit, offset)` replaces full table scan in `PostsConnectionResolver` |
| 16 | PostgreSQL + HikariCP: driver, connection pooling, Micrometer pool metrics; Flyway migrations with `V1__create_tables.sql`; H2 tests on `MODE=PostgreSQL` |
| 17 | Production telemetry: structured JSON logging, `CallId`/`CallLogging`, Micrometer + Prometheus `/metrics`, enhanced `/health` |
| 18 | Rich text editor: Lexical on Create/Edit pages; DOMPurify rendering in PostDetailPage |
| 19 | Frontend unit tests: Vitest + jsdom + Testing Library (`npm test`) |
| Admin | Full CRUD over users/posts/comments; dashboard stats; `requireAdmin()` guard; cascading user delete |
| Analytics (Phase 20–22) | `PostViews` table, `viewCount`/`readTimeMinutes` batch resolvers, `recordPostView` mutation, `trending` query; admin stats analytics; homepage New/Trending sort |
| CheckedList (Phase 23) | `CheckedListPost` type with toggleable ordered items; full resolver/repo/test/frontend coverage; unified feed with type-aware post cards; create/edit/detail UI |
| AI foundation (Phase 24) | `:modules:ai` module; `AIService`/`OllamaAIService`/`NoOpAIService`; `OllamaConfig` env vars; Tracy observability; `GET /health/ai`; `useAIHealth` hook |
| AI rephrase (Phase 25) | `rephraseContent` GraphQL mutation; `RephraseControls` component; `useRephrase` hook; tone selector; controls on Create + Edit pages; full test coverage |
| Bug fixes | Dark mode post type toggle; CheckedList like button; author-only item toggle enforcement (backend + frontend) |
| Code quality | Domain exceptions, `requireAuth()`/`optionalAuth()` helpers, `useLikeToggle` hook, `PaginationControls` component, `UserRepository.updateFields()`, `.btn-secondary` CSS class, Ports and Adapters documentation |

## Next Steps

- **Phase 26**: Checklist item auto-suggestion — "Suggest next item" button when ≥ 3 items exist
- **Phase 27**: Post recommendation engine — embedding-based "You might like" panel using `nomic-embed-text`

> See `AI-PLAN.md` for full design, technology choices, and file-by-file breakdown.

---

## Phase 20: Analytics Backend — Schema, Storage, and Resolvers ✅ DONE

## Phase 21: Analytics Frontend — View Tracking and Post Detail Page ✅ DONE

## Phase 22: Analytics Frontend — Admin Stats and Homepage Sorting ✅ DONE

**Goal**: Implement the analytics tenant module on the backend: track post views, expose `viewCount`, `readTimeMinutes`, and `trending` via GraphQL.

**Current state**: `ViaductPostTypeLookupPort.kt` exists as a stub; `post_type` column is in the `Posts` table. There is no `PostViews` table, no analytics schema extension, and no resolvers. The `QueryFieldComplexityCalculator` already accounts for `trending` as an unbounded list — the resolver just needs to be built.

### Tasks

#### 1. Add `PostViews` table and repository
- New `PostViews` table: `id`, `post_id` (FK → Posts), `viewed_at` (datetime)
- `PostViewRepository` interface: `recordView(postId: UUID)`, `countViews(postId: UUID): Int`, `topByViews(limit: Int): List<PostView>`
- `ExposedPostViewRepository` implementation; all queries inside `transaction {}` blocks
- Register in `KoinModules.kt`
- Add a Flyway migration `V2__create_post_views.sql`
- `DatabaseFactory`: add `PostViews` to `createMissingTablesAndColumns` call (already switched from `create` in the container fix)

#### 2. Add analytics GraphQL schema extension
- New file `src/main/viaduct/schema/PostAnalytics.graphqls` (Viaduct supports split schema files):
  ```graphql
  # Extends BlogPost and CheckedListPost with analytics fields
  extend type BlogPost {
    viewCount: Int! @resolver
    readTimeMinutes: Int! @resolver
  }

  extend type CheckedListPost {
    viewCount: Int! @resolver
    readTimeMinutes: Int! @resolver
  }

  extend type Mutation {
    recordPostView(postId: ID! @idOf(type: "BlogPost")): Boolean! @resolver
  }

  extend type Query {
    trending(limit: Int = 10): [Post!]! @resolver
  }
  ```
- Run `./gradlew build` to generate resolver base classes

#### 3. Implement resolvers
- `BlogPostViewCountResolver` — extends generated `BlogPostResolvers.ViewCount()`; calls `postViewRepository.countViews(post.id)`
- `BlogPostReadTimeMinutesResolver` — extends `BlogPostResolvers.ReadTimeMinutes()`; computes `max(1, wordCount / 200)` from `post.content` (strips HTML tags first)
- Same two resolvers for `CheckedListPost`
- `RecordPostViewResolver` — extends `MutationResolvers.RecordPostView()`; calls `postViewRepository.recordView(postId)`; no auth required (public)
- `TrendingResolver` — extends `QueryResolvers.Trending()`; calls `postViewRepository.topByViews(limit)`, fetches the corresponding posts via `postRepository.findByIds(ids)`, returns as `List<Post>`
- Register all five in `KoinModules.kt`

#### 4. Unit tests
- `ViewCountResolverTest`, `ReadTimeMinutesResolverTest` — mock `PostViewRepository`; assert correct values
- `RecordPostViewResolverTest` — verify `recordView` is called with correct id; unauthenticated calls succeed
- `TrendingResolverTest` — mock `topByViews` returning N ids; assert resolver returns the right posts in order
- Repository integration tests for `PostViewRepository` using `DatabaseTestHelper` (H2)

**Key files**:
- `src/main/kotlin/org/tuchscherer/database/Tables.kt` — add `PostViews`
- `src/main/kotlin/org/tuchscherer/database/repositories/PostViewRepository.kt` — new
- `src/main/kotlin/org/tuchscherer/database/repositories/ExposedPostViewRepository.kt` — new
- `src/main/viaduct/schema/PostAnalytics.graphqls` — new
- `src/main/kotlin/org/tuchscherer/resolvers/AnalyticsResolvers.kt` — new (all five resolvers)
- `src/main/resources/db/migration/V2__create_post_views.sql` — new
- `src/main/kotlin/org/tuchscherer/config/KoinModules.kt` — register new beans

**Success Criteria**: `trending(limit: 5) { id title }` returns the 5 most-viewed posts; `recordPostView` inserts a row in `post_views`; `viewCount` and `readTimeMinutes` return correct values; `./gradlew test` passes.

---

## Phase 21: Analytics Frontend — View Tracking and Post Detail Page ✅ DONE

**Goal**: Wire `recordPostView` into the post detail page so views are tracked automatically on open, and display `viewCount` and `readTimeMinutes` on the post detail page.

**Depends on**: Phase 20 complete (backend resolvers live).

### Tasks

#### 1. Fire `recordPostView` on post open
- In `PostDetailPage.tsx`, add a `useMutation` for `recordPostView`
- Call it inside a `useEffect` that fires once when the `postId` is known (on mount)
- No loading state or error UI needed — fire-and-forget; log to console on failure only
- Mutation should be skipped if the viewer is the post's author (optional, nice-to-have)

#### 2. Add `viewCount` and `readTimeMinutes` to the post detail query
- Extend the `GET_POST` query in `PostDetailPage.tsx` to also fetch `viewCount` and `readTimeMinutes`
- Display them below the post title / metadata line, e.g.:
  - `👁 142 views  ·  ⏱ 4 min read`
- Match the existing metadata style (author, date)

#### 3. Show `readTimeMinutes` on post cards (HomePage)
- Extend the `postsConnection` query to include `readTimeMinutes` on each post
- Add it to `PostCard` / the post list items on `HomePage`: `⏱ 3 min read`
- Keep it subtle — secondary text colour, small font

#### 4. Unit and E2E tests
- Vitest unit test: `PostDetailPage` fires `recordPostView` mutation on mount (use `MockedProvider`)
- Playwright test: open a post, reload, assert view count incremented (query `viewCount` via API after page load)
- Playwright test: post card shows `readTimeMinutes`; post detail page shows both `viewCount` and `readTimeMinutes`

**Key files**:
- `frontend/src/pages/PostDetailPage.tsx` — mutation + extended query + metadata display
- `frontend/src/pages/HomePage.tsx` — add `readTimeMinutes` to connection query
- `frontend/src/components/PostCard.tsx` (or equivalent list item) — render read time
- `frontend/src/graphql/` (or inline) — extend/add query/mutation fragments
- `frontend/e2e/posts.spec.ts` — add view count + read time E2E tests
- `frontend/test/pages/PostDetailPage.test.tsx` — unit test for mutation on mount

**Success Criteria**: Opening a post increments its `viewCount` in the database; the detail page shows views and read time; post cards show read time; Playwright and Vitest tests pass.

---

## Phase 22: Analytics Frontend — Admin Stats and Homepage Sorting ✅ DONE

**Goal**: Surface analytics data in the admin dashboard and let users sort the homepage post list by trending or creation date.

**Depends on**: Phases 20–21 complete.

### Tasks

#### 1. Add analytics to the Admin dashboard
- Extend the `AdminStats` GraphQL type (or add a separate query) to include:
  - `totalViews: Int!` — sum of all post view counts
  - Top 5 trending posts (id, title, viewCount) for an "Most Viewed Posts" table
- Add a new "Analytics" card/section to the admin stats page showing:
  - Total views across all posts
  - "Most Viewed Posts" table: rank, title (linked to post), view count
- Keep it on the existing admin stats page, not a new route

#### 2. Sort posts on the Homepage
- Add a sort control to `HomePage` — a segmented button or dropdown with two options:
  - **New** (default) — sorted by `createdAt` descending (current behaviour)
  - **Trending** — fetches `trending(limit: 10)` instead of `postsConnection`
- When "Trending" is selected, switch the Apollo query to `trending`; when "New", use `postsConnection` as today
- "Load More" pagination applies only to "New" mode; trending shows a fixed list (no pagination needed)
- Persist the selected sort in component state (no URL param needed)

#### 3. Unit and E2E tests
- Playwright test: admin stats page shows "Most Viewed Posts" table after viewing a post
- Playwright test: homepage sort — default shows newest first; switch to trending, verify the most-viewed post appears at the top
- Vitest unit test: sort control renders both options; clicking "Trending" switches the active label

**Key files**:
- `src/main/viaduct/schema/schema.graphqls` — extend `AdminStats` with `totalViews` and `topPosts`
- `src/main/kotlin/org/tuchscherer/resolvers/AdminResolvers.kt` — update `AdminStatsResolver`
- `frontend/src/pages/AdminStatsPage.tsx` (or equivalent) — add analytics section
- `frontend/src/pages/HomePage.tsx` — sort control + conditional query
- `frontend/e2e/posts.spec.ts` — sorting E2E tests
- `frontend/test/pages/HomePage.test.tsx` — sort control unit tests

**Success Criteria**: Admin dashboard shows total views and a top-5 most-viewed table; homepage sort control switches between newest and trending; all new Playwright and Vitest tests pass; `./gradlew test` still green.

---

## Phase 23: CheckedList Frontend ✅ DONE

**Goal**: Full frontend support for CheckedList posts — creating, viewing, editing items, and rendering alongside BlogPosts in all feeds.

**Depends on**: Phases 20–22 complete (analytics fields `viewCount` and `readTimeMinutes` available on `CheckedListPost`).

**Backend state going in**: The checkedlist module is substantially built. The schema has `CheckedListPost`, `CheckedListItem`, `createCheckedListPost`, `addCheckedListItem`, `toggleCheckedListItem`, `deleteCheckedListItem`, and `checkedListPosts`. Three gaps need closing before the frontend can rely on it:

### Backend gaps to close first

#### 1. Add `description` field to `CheckedListPost`
- Add `description: String!` to `CheckedListItems` table (nullable at DB level, empty string default)
- Add `description: String!` field to `CheckedListPost` in `CheckedList.graphqls` — no `@resolver` needed, resolved inline like `title`
- Add `description` to `CreateCheckedListPostInput` and `PostData`
- Add `updateCheckedListItem` mutation to `CheckedList.graphqls` for editing item text:
  ```graphql
  input UpdateCheckedListItemInput {
    id: ID! @idOf(type: "CheckedListItem")
    text: String!
  }
  extend type Mutation {
    updateCheckedListItem(input: UpdateCheckedListItemInput!): CheckedListItem! @resolver
  }
  ```
- Implement `UpdateCheckedListItemResolver` and register in Koin
- Update `ViaductPostCreationPort.createCheckedListPost` to accept and persist `description`
- Add repository + resolver unit tests for the new mutation

#### 2. Expose CheckedListPosts in the unified feed and `trending`
- The homepage currently queries `postsConnection` which only returns `BlogPost` rows. The `Posts` table already has `post_type`, so `postsConnection` needs to return both types via the `Post` interface.
- Update `PostRepository.findPage` / `findAll` to return all post types (currently filters to `BLOG_POST` only — verify)
- The `trending` resolver (Phase 20) should also pull from `PostViews` regardless of post type — confirm `topByViews` returns both
- GraphQL clients querying the feed need to use inline fragments: `... on BlogPost { content }` / `... on CheckedListPost { items { text checked position } description }`

#### 3. Add `recordPostView` support for `CheckedListPost`
- The Phase 20 `recordPostView` mutation uses `@idOf(type: "BlogPost")` — add an overload or change the arg type to accept the `Post` interface ID so checklist views are also counted

---

### Frontend tasks

#### 4. Unified post feed (HomePage)
- Update the `postsConnection` Apollo query to fetch both post types via inline fragments:
  ```graphql
  ... on BlogPost { content readTimeMinutes viewCount }
  ... on CheckedListPost { description items { id text checked position } readTimeMinutes viewCount }
  ```
- Update `PostCard` / list item component to render differently based on type:
  - **BlogPost**: existing preview (HTML stripped, truncated)
  - **CheckedListPost**: show `description` preview + a compact summary line, e.g. `3 / 7 items checked`
- Add a visual indicator on CheckedListPost cards (e.g. a small checklist icon or a `Checklist` badge) so users can distinguish the two types at a glance
- The sort control from Phase 22 (New / Trending) should work identically for both types

#### 5. New Post page — post type selector
- When the user navigates to `/posts/new`, show a radio button at the top: **Blog Post** (default) | **Checklist**
- Switching radio reveals the appropriate form below; the other form is hidden but its state is preserved in `localStorage`
  - `localStorage` key `draft:blogpost` stores `{ title, content }` (existing behaviour, just make it explicit)
  - `localStorage` key `draft:checkedlist` stores `{ title, description, items: string[] }`
- Each form reads from its own localStorage key on mount and writes on every change
- Switching the radio does NOT clear the other form's localStorage slot
- On successful submission, clear only the submitted form's localStorage key

**Blog Post form** (unchanged from today): title + Lexical rich text editor

**CheckedList form**:
- Title field (text input, max 500 chars, same validation as BlogPost)
- Description field (plain textarea or smaller Lexical instance, max ~2000 chars)
- Items list: each item is a text input row with a drag handle (or up/down arrows) for reordering and a delete (×) button
- "Add item" button appends a new empty row and focuses it
- Items are submitted in the order shown; `position` is derived from array index on submit
- Minimum 1 item required to submit

#### 6. CheckedList detail page (`/posts/:id` for CheckedListPost)
- Reuse the same route as BlogPost detail — detect type from the GraphQL response and render accordingly
- Layout mirrors BlogPost detail: title, author, date, `viewCount · readTimeMinutes`, then the body
- Body for CheckedListPost: `description` paragraph (if non-empty), then the items list
- Each item renders as a checkbox + text label
- Checking/unchecking calls `toggleCheckedListItem` — only enabled if the viewer is the post author; otherwise rendered as read-only
- Author also sees **Add item** (inline text input + confirm), **Edit** (click text to edit inline), and **Delete** (× button) on each item
- After toggling/adding/editing/deleting, refetch or optimistically update the items list
- Comments and likes section identical to BlogPost detail

#### 7. Edit page for CheckedListPost
- On the CheckedList detail page, the author sees an **Edit** button (same position as on BlogPost detail)
- Edit page at `/posts/:id/edit` detects post type and shows the CheckedList form pre-populated with current title, description, and items
- Saving calls a new `updateCheckedListPost(input: UpdateCheckedListPostInput!)` mutation (add to schema):
  ```graphql
  input UpdateCheckedListPostInput {
    id: ID! @idOf(type: "CheckedListPost")
    title: String
    description: String
  }
  ```
- Item-level edits (add/edit/delete individual items) are done via the existing item mutations, not bundled into the post update

#### 8. Playwright E2E tests
- Create a checklist post via the New Post page: select Checklist radio, fill title + description + 3 items, submit — verify post appears in homepage feed with checklist badge and `0 / 3 items checked`
- Draft persistence: fill checklist form fields, switch to Blog Post radio, switch back — verify checklist fields restored from localStorage
- Checklist detail: open the post, toggle an item as the author — verify checkbox state updates; reload — verify state persists
- Non-author view: open the post as a different user — verify checkboxes are read-only
- Edit checklist post: change title and description, add a new item — verify changes reflected on detail page
- Trending/homepage sort: view a checklist post several times, verify it appears in trending results alongside blog posts

#### 9. Vitest unit tests
- `PostCard` renders checklist badge and `N / M items checked` summary for a `CheckedListPost`
- `NewPostPage` sort control: selecting Checklist radio shows checklist form; switching back to Blog Post shows blog form
- localStorage draft: simulate typing in checklist form, switch radio, switch back — assert field values restored
- `CreateCheckedListForm` disables submit when items list is empty

**Key files**:
- `modules/checkedlist/src/main/viaduct/schema/CheckedList.graphqls` — add `description`, `updateCheckedListItem`, `updateCheckedListPost`
- `modules/checkedlist/src/main/kotlin/org/tuchscherer/checkedlist/database/CheckedListTables.kt` — add `description` column
- `modules/checkedlist/src/main/kotlin/org/tuchscherer/viadapp/checkedlist/resolvers/CheckedListMutationResolvers.kt` — add `UpdateCheckedListItem`, `UpdateCheckedListPost` resolvers
- `src/main/kotlin/org/tuchscherer/checkedlist/ViaductPostCreationPort.kt` — accept `description` in `createCheckedListPost`
- `frontend/src/pages/NewPostPage.tsx` (rename from `CreatePostPage.tsx`) — radio selector + dual form
- `frontend/src/components/CreateBlogPostForm.tsx` — extracted from current create page
- `frontend/src/components/CreateCheckedListForm.tsx` — new
- `frontend/src/pages/PostDetailPage.tsx` — branch on post type for body rendering
- `frontend/src/pages/EditPostPage.tsx` — branch on post type
- `frontend/src/components/PostCard.tsx` — checklist card variant
- `frontend/src/hooks/usePostDraft.ts` — localStorage read/write abstraction, one slot per post type
- `frontend/e2e/checkedlist.spec.ts` — new E2E spec file
- `frontend/test/components/PostCard.test.tsx` — checklist card unit tests
- `frontend/test/pages/NewPostPage.test.tsx` — radio selector + draft persistence unit tests

**Success Criteria**:
- A user can create a CheckedList post and a Blog Post from the same New Post page without losing draft content when switching types
- CheckedList posts appear in the homepage feed (New and Trending) alongside Blog Posts with a visual distinction
- The author can toggle, add, edit, and delete items on the detail page; non-authors see a read-only view
- `viewCount` and `readTimeMinutes` are shown on CheckedList detail pages (same as BlogPost)
- All Phase 23 Playwright and Vitest tests pass; `./gradlew test` remains green

---

## Testing Strategy

```
        /\
       /  \     Browser E2E (40 tests × 3 browsers via ./e2e.sh)
      /____\    ↑ Real UI flows through Chromium, Firefox, WebKit
     /      \
    /        \   API E2E (38 tests via ./query-tests.sh)
   /__________\  ↑ GraphQL + REST contracts via curl
  /            \
 /  Integration \ H2 in-memory integration tests
/________________\↑ Real services + real DB, no HTTP overhead
/                  \
/    Unit Tests     \ MockK unit tests
/____________________\↑ Isolated, fast, no DB
```

**Run commands:**
- `./gradlew test` — all unit + integration tests
- `./query-tests.sh` — API e2e tests (backend must be running)
- `./e2e.sh` — starts servers + runs Playwright browser tests
- `cd frontend && npm test` — frontend Vitest unit tests

---

## Phase 24: AI Foundation & Infrastructure ✅ DONE

**Goal**: shared AI plumbing — module, LangChain4j + Ollama client, Tracy observability, `AIService` abstraction, health endpoint.

**What was built**:
- `:modules:ai` Gradle module with `langchain4j-ollama`, `langchain4j` core, Tracy (`runtimeOnly`)
- `OllamaConfig` reading `OLLAMA_BASE_URL`, `OLLAMA_CHAT_MODEL`, `OLLAMA_EMBEDDING_MODEL` from env
- `AIService` interface: `rephrase`, `suggestNextItem`, `generateEmbedding`, `isReachable`, `modelConfig`
- `OllamaAIService` (production) and `NoOpAIService` (test stub) implementations
- `GET /health/ai` returns `{ ollamaReachable, chatModel, embeddingModel }` with HTTP 200/503
- `useAIHealth` hook (cached per session via module-level Promise — single fetch)
- `start.sh` launches Ollama natively for Metal GPU acceleration

---

## Phase 25: Rephrase Blog Post Content ✅ DONE

**Goal**: "Rephrase with AI ✨" button in the blog post editor with tone selector.

**What was built**:
- `RephraseTone` enum, `RephraseResult` type, `rephraseContent(content, tone)` GraphQL mutation
- `RephraseContentResolver` — auth-required; validates non-blank + ≤ 50 000 chars; exhaustive `when` on tone (null → PROFESSIONAL); `AIServiceException` propagates directly
- `useRephrase` hook (tone state, contentKey, mutation, handleRephrase) shared by Create and Edit pages
- `RephraseControls` component (tone selector + Rephrase button + "Ollama offline" label)
- Controls shown on Create Post and Edit Post pages; absent on Checklist pages
- `OllamaAIService.isReachable()` uses lightweight `GET /api/tags` probe (not model inference)
- `GraphQLServer` decoupled from `OllamaConfig` via `AIService.modelConfig()`
- Tests: `RephraseContentResolverTest` (8 cases), `OllamaAIServiceTest` (4 cases), 9 Playwright E2E tests across Create + Edit pages

---

## Phase 26: Checklist Item Auto-suggestion ⏳ TODO

**Goal**: "Suggest next item" button in the checklist editor when ≥ 3 items exist.

**Depends on**: Phase 24 (backend); Phase 23 + Phase 24 (frontend).

### Tasks

1. Schema (checkedlist module): add `SuggestedChecklistItem` type, `suggestChecklistItem(existingItems)` mutation
2. `SuggestChecklistItemMutationResolver` — auth-required; validates `existingItems.size >= 3`; calls `AIService.suggestNextItem`
3. Prompt template resource file
4. Register in Koin; unit tests covering < 3 items error and ≥ 3 items success
5. Frontend (Phase 23 checklist edit UI): "+ Suggest item" button; enabled only at ≥ 3 items; appends suggestion as editable row

**Key files**: `CheckedList.graphqls`, new resolver in checkedlist module, checklist edit component

**Success criteria**: mutation enforces 3-item minimum; returned suggestion appended to list; button tooltip shown when < 3 items.

---

## Phase 27: Post Recommendation Engine ⏳ TODO

**Goal**: personalised "You might like" panel on the home feed using semantic embeddings.

**Depends on**: Phase 24, Phase 25 (for end-to-end AI stack validation), Phase 23 (checklist posts in unified feed).

### Tasks

1. Flyway migration `V_ai_1__post_embeddings.sql` — `post_embeddings` table (post_id PK, embedding as JSON float array, model_name, timestamps)
2. `PostEmbeddingRepository` — upsert and fetch embeddings
3. `EmbeddingService` — wraps LangChain4j `EmbeddingModel` (nomic-embed-text), Tracy-instrumented
4. Async embedding generation hook in `createPost` / `updatePost` resolvers (Kotlin coroutine, non-blocking)
5. `RecommendationService` — centroid of user-interacted-post embeddings → cosine-similarity rank → top-N post IDs; fallback to trending
6. Schema: `recommendedPosts(limit: Int): [Post!]!` query
7. `RecommendedPostsQueryResolver` — auth-required; delegates to `RecommendationService`
8. Frontend: `RecommendationsPanel` component with skeleton loader; falls back to "Trending" section when no history or Ollama offline
9. Unit tests: cosine-similarity ranking, fallback behaviour, embedding round-trip; Playwright test: view posts → panel shows related content

**Key files**: migration SQL, `PostEmbeddingRepository`, `EmbeddingService`, `RecommendationService`, resolver, `RecommendationsPanel.tsx`

**Success criteria**: recommendations panel shows ≥ 1 relevant post after a user views several posts; unauthenticated / no-history users see trending posts instead; `./gradlew test` green.

---

## Tech Debt

### Migrate Koin modules from DSL to annotation-based API for compile-time safety

**Context**: Koin's compiler plugin (`koin-annotations` + `koin-ksp-compiler`) validates the full dependency graph at compile time — "if it compiles, it works" — with no test code required. Currently the project uses DSL-only modules (`val xModule = module { }` / `singleOf(::Resolver)`), which means dependency graph errors are only caught at test time via the `KoinModulesTest.verify()` test added alongside this note.

**What to do**:
1. Add `koin-annotations` and `koin-ksp-compiler` (KSP) dependencies to `build.gradle.kts`
2. Annotate each module class with `@Module` and each binding with `@Single` / `@Factory` as appropriate
3. Replace `val allModules = listOf(...)` startup with annotation-based `startKoin<AppModule>()`
4. Remove the `verify()` test in `KoinModulesTest` once compile-time safety covers it

**Reference**: https://insert-koin.io/docs/reference/koin-compiler/compile-safety

**Why it's deferred**: non-trivial mechanical migration with no user-visible impact; the `verify()` test covers the same gap in the interim.
