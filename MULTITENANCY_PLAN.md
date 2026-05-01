# Multi-Tenancy Implementation Plan

**Last Updated**: 2026-05-01

This plan implements Viaduct's multi-tenancy concept in the blogging app: separate Gradle
subprojects ("tenant modules") where each module owns its own schema fragments, resolvers,
and data — demonstrating that a new team can extend the graph without touching existing code.

Reference: [Star Wars demo](https://github.com/viaduct-dev/starwars) for the module pattern.

---

## Implementation Order

```
Phase 0  →  Phase 1  →  Phase 2
Refactor    Analytics   CheckedList
(prereq)    module      module
```

**Phase 0** is a prerequisite: both modules depend on the `Post` interface and the `postType`
discriminator column. **Phase 1** (analytics) is simpler and validates the multi-module Gradle
+ Koin wiring before the more complex Phase 2. **Phase 2** bundles a small analytics extension
(CheckedListPost.viewCount) in the same PR.

---

## Phase 0 — Core Refactor: `Post` → `BlogPost` + `Post` Interface ✅ DONE (323 tests, 0 failures)

Pure rename + schema extraction. **No behavior changes.** All existing tests must pass after
this phase.

**Delivered:**
- `interface Post` with all shared fields including `readTimeMinutes: Float! @resolver(isBatching: true)`
- `type BlogPost implements Post & Node` with `content: String!` as its only additional field
- `PostEdge.node: Post`, `Comment.post: Post`, `Like.post: Post` all use the interface
- `postType VARCHAR(50) DEFAULT 'BLOG_POST'` column added to `Posts` table
- `PostType` object in `Tables.kt` with `BLOG_POST` and `CHECKED_LIST` constants
- All resolver imports updated; `BlogPostNodeResolver` replaces `PostNodeResolver`
- `BlogFieldComplexityCalculator` updated: parent `"BlogPost"` instead of `"Post"`
- Unused `ViaductBlogPost` import removed from `CommentFieldResolvers` and `LikeObjectFieldResolvers`

### Schema changes (`src/main/viaduct/schema/schema.graphqls`)

1. Add `interface Post` with shared fields:
   ```graphql
   interface Post {
     id: ID!
     title: String!
     author: User! @resolver(isBatching: true)
     comments: [Comment!]! @resolver
     commentCount: Int! @resolver
     likes: [Like!]! @resolver
     likeCount: Int! @resolver
     isLikedByMe: Boolean! @resolver
     createdAt: String!
     updatedAt: String!
     readTimeMinutes: Float! @resolver(isBatching: true)
   }
   ```
   `readTimeMinutes` is declared here as an interface contract; implementations are
   provided by tenant modules (analytics for `BlogPost`, checkedlist for `CheckedListPost`).

2. Rename `type Post` → `type BlogPost implements Post & Node`; `content: String!` stays on
   `BlogPost` only.

3. `PostEdge.node` becomes `Post` (interface) so the connection is forward-compatible with
   new post types.

4. All existing queries/mutations that returned `Post` now return `BlogPost`:
   `posts`, `myPosts`, `post`, `createPost`, `updatePost`, `admin.post`, `admin.posts`.

5. `Comment.post: Post!` and `Like.post: Post!` remain typed as the `Post` interface.

### Database changes

Add `post_type VARCHAR(50) NOT NULL DEFAULT 'BLOG_POST'` to the `Posts` table.
This discriminator lets `CommentPostResolver` and `LikePostResolver` return the correct
concrete type when `CheckedListPost` is introduced in Phase 2.

### Code changes

| File | Change |
|---|---|
| `Tables.kt` | Add `postType` column |
| `Models.kt` | Add `postType` field to `Post` DAO entity |
| All resolver files | `viaduct.api.grts.Post` import → `viaduct.api.grts.BlogPost` |
| `NodeResolvers.kt` | `PostNodeResolver` → `BlogPostNodeResolver`, extends `NodeResolvers.BlogPost()` |
| `CommentFieldResolvers.kt` | Return type `BlogPost` (implements `Post`; no logic change) |
| `LikeObjectFieldResolvers.kt` | Same |
| `KoinModules.kt` | Rename `PostNodeResolver` → `BlogPostNodeResolver` |

### Complexity calculator changes

`BlogFieldComplexityCalculator.isUnboundedListResolver` checks `parent == "Post"`. After
the rename the parent type name becomes `"BlogPost"`, silently breaking the list multiplier
for `BlogPost.comments` and `BlogPost.likes`. Fix:

```kotlin
// Before
"Post" -> name in UNBOUNDED_POST_LISTS
// After
"BlogPost" -> name in UNBOUNDED_POST_LISTS
```

Update `BlogFieldComplexityCalculatorTest` accordingly: all assertions using `parent = "Post"`
for list resolver cases must be updated to `parent = "BlogPost"`.

### Tests

Run `./gradlew test` — all existing tests pass. No new tests needed.

---

## Phase 1 — Analytics Module

A new Gradle subproject that adds `viewCount`, `readTimeMinutes`, and a `trending` query to
the graph. **Zero changes to existing resolver or schema files** (except one line added to
`allModules` in `KoinModules.kt`).

### Gradle setup

- Create `modules/analytics/build.gradle.kts`:
  ```kotlin
  plugins {
      `java-library`
      alias(libs.plugins.kotlin.jvm)
      alias(libs.plugins.viaduct.module)
  }
  viaductModule { modulePackageSuffix.set("analytics") }
  dependencies {
      api(libs.viaduct.tenant.api)
      implementation(libs.viaduct.runtime)
      implementation(libs.exposed.core)
      implementation(libs.exposed.dao)
      implementation(libs.exposed.jdbc)
      implementation(libs.exposed.java.time)
      implementation(libs.koin.core)
  }
  ```
- `settings.gradle.kts`: add `include(":modules:analytics")`
- Root `build.gradle.kts`: add `implementation(project(":modules:analytics"))`

### Schema (`modules/analytics/src/main/viaduct/schema/PostAnalytics.graphqls`)

```graphql
extend type BlogPost @scope(to: ["public", "admin"]) {
  viewCount: Int! @resolver(isBatching: true)
}

extend type Query @scope(to: ["public", "admin"]) {
  trending(limit: Int = 10): [BlogPost!]! @resolver
}

extend type Mutation @scope(to: ["public", "admin"]) {
  recordPostView(postId: ID! @idOf(type: "BlogPost")): Boolean! @resolver
}
```

`BlogPost.readTimeMinutes` is declared on the `Post` interface (Phase 0); the analytics
module provides the `BlogPostResolvers.ReadTimeMinutes()` implementation here.

### Database

New `PostViews` table, created in the analytics module's own init:
```
postId    UUID  FK → Posts  UNIQUE
viewCount LONG  DEFAULT 0
```
`DatabaseFactory` is untouched.

### Repository (`modules/analytics`)

`PostViewRepository` interface + `ExposedPostViewRepository`:

| Method | Purpose |
|---|---|
| `incrementViewCount(postId: UUID)` | Upsert — insert or increment |
| `bulkGetViewCounts(postIds: List<UUID>): Map<UUID, Long>` | Batch resolver support |
| `getMostViewed(limit: Int): List<UUID>` | Sorted by viewCount desc |

### Resolvers (`modules/analytics`)

| Resolver | Extends | Logic |
|---|---|---|
| `BlogPostViewCountBatchResolver` | `BlogPostResolvers.ViewCount()` | Batch lookup from `PostViews`; returns 0 for unseen posts |
| `BlogPostReadTimeMinutesBatchResolver` | `BlogPostResolvers.ReadTimeMinutes()` | `max(0.5, wordCount / 200.0)` from `content`; pure computation, no DB |
| `TrendingQueryResolver` | `QueryResolvers.Trending()` | `getMostViewed(limit)` → fetch BlogPosts by ID |
| `RecordPostViewMutationResolver` | `MutationResolvers.RecordPostView()` | Upserts `PostViews`; no auth required |

### DI wiring

Analytics module exports:
```kotlin
val analyticsKoinModule = module {
    single<PostViewRepository> { ExposedPostViewRepository() }
    singleOf(::BlogPostViewCountBatchResolver)
    singleOf(::BlogPostReadTimeMinutesBatchResolver)
    singleOf(::TrendingQueryResolver)
    singleOf(::RecordPostViewMutationResolver)
}
```

Root `KoinModules.kt`: add `analyticsKoinModule` to `allModules`. **One line added, nothing
else changed.**

### Complexity calculator changes

Two updates to `BlogFieldComplexityCalculator`:

1. Add `"trending"` to `UNBOUNDED_QUERY_LISTS` — returns an unbounded `[BlogPost!]!` so
   the ×10 multiplier must apply.

2. Update schema loading in `QueryComplexityGuard` and `QueryComplexityIntegrationTest` to
   load all module schema files. Replace the single hardcoded app schema path with an
   explicit list filtered by existence:
   ```kotlin
   private fun loadAppSchema(): String = listOf(
       "src/main/viaduct/schema/schema.graphqls",
       "modules/analytics/src/main/viaduct/schema/PostAnalytics.graphqls",
       "modules/checkedlist/src/main/viaduct/schema/CheckedList.graphqls",
   ).filter { File(it).exists() }
    .joinToString("\n") { File(it).readText() }
   ```
   The `exists()` filter means the guard works correctly before all modules are present. New
   modules just add one path to this list.

### Tests

**Repository (H2) — `PostViewRepositoryTest`:**
- First increment creates a row with viewCount=1
- Second increment upserts to viewCount=2
- `bulkGetViewCounts` returns correct map; posts with no row return 0
- `getMostViewed` returns posts in descending order and respects limit

**Resolver unit (MockK) — `modules/analytics/src/test/`:**
- `BlogPostViewCountBatchResolverTest` — mock repo; assert correct `FieldValue` per post; unknown post returns 0
- `BlogPostReadTimeMinutesBatchResolverTest` — no mock; empty content → 0.5; "one two three" → 0.5 floor; 400-word content → 2.0
- `TrendingQueryResolverTest` — mock repo ordering; assert result order matches view count ranking
- `RecordPostViewMutationResolverTest` — mock repo; assert increment called; returns true

**Complexity calculator unit — `BlogFieldComplexityCalculatorTest`:**
- `trending` with no limit arg applies ×10 multiplier
- `trending` with explicit `limit` arg uses that value as multiplier

**Complexity integration — `QueryComplexityIntegrationTest`:**
- Realistic `trending(limit: 10) { id title }` scores under threshold
- `trending` with deeply nested fields scores over threshold

**Query tests (`query-tests.sh`):**
- `recordPostView` returns true
- `viewCount` on a post starts at 0; increments to 1 after `recordPostView`
- `readTimeMinutes` is non-zero for a post with content
- `trending` returns posts; most-viewed appears first after multiple `recordPostView` calls

**E2e (`frontend/e2e/analytics.spec.ts`):**
- `viewCount` and `readTimeMinutes` displayed on post detail page
- `/trending` route shows posts ordered by views
- Viewing a post increments its view count (visible after reload)

**Frontend changes:**
- Add `viewCount` and `readTimeMinutes` to post card and detail GraphQL queries
- Add `/trending` page
- Display both fields on post cards and detail view

---

## Phase 2 — CheckedList Module + Analytics Extension

A new Gradle subproject that introduces `CheckedListPost implements Post & Node`. The
analytics module is extended in the same PR to support `CheckedListPost.viewCount`.

### Gradle setup

- Create `modules/checkedlist/build.gradle.kts` (same structure as analytics, suffix `"checkedlist"`)
- `settings.gradle.kts`: add `include(":modules:checkedlist")`
- Root `build.gradle.kts`: add `implementation(project(":modules:checkedlist"))`

### Schema (`modules/checkedlist/src/main/viaduct/schema/CheckedList.graphqls`)

```graphql
type CheckedListPost implements Post & Node
    @resolver(isBatching: true) @scope(to: ["public", "admin"]) {
  id: ID!
  title: String!
  author: User! @resolver(isBatching: true)
  items: [CheckedListItem!]! @resolver
  comments: [Comment!]! @resolver
  commentCount: Int! @resolver
  likes: [Like!]! @resolver
  likeCount: Int! @resolver
  isLikedByMe: Boolean! @resolver
  createdAt: String!
  updatedAt: String!
}

type CheckedListItem implements Node
    @resolver(isBatching: true) @scope(to: ["public", "admin"]) {
  id: ID!
  text: String!
  isChecked: Boolean!
  position: Int!
}

extend type Query @scope(to: ["public", "admin"]) {
  checkedListPosts: [CheckedListPost!]! @resolver
}

extend type Mutation @scope(to: ["public", "admin"]) {
  createCheckedListPost(input: CreateCheckedListPostInput!): CheckedListPost! @resolver
  toggleCheckedListItem(itemId: ID! @idOf(type: "CheckedListItem"), isChecked: Boolean!): CheckedListItem! @resolver
  addCheckedListItem(postId: ID! @idOf(type: "CheckedListPost"), text: String!): CheckedListItem! @resolver
  deleteCheckedListItem(itemId: ID! @idOf(type: "CheckedListItem")): Boolean! @resolver
}

input CreateCheckedListPostInput @scope(to: ["public", "admin"]) {
  title: String!
  items: [String!]!
}
```

`CheckedListPost.readTimeMinutes` is declared on the `Post` interface; this module provides
`CheckedListPostResolvers.ReadTimeMinutes()`.

### Analytics extension schema (`modules/analytics/src/main/viaduct/schema/PostAnalytics.graphqls`)

Add to the existing analytics schema file:
```graphql
extend type CheckedListPost @scope(to: ["public", "admin"]) {
  viewCount: Int! @resolver(isBatching: true)
}
```

### Database

`CheckedListPost` creates a row in the existing `Posts` table with `post_type='CHECKED_LIST'`
and `content=''`. No new post-level table needed.

New `CheckedListItems` table, created in the checkedlist module's own init:
```
id        UUID      PK
postId    UUID      FK → Posts
text      TEXT
isChecked BOOLEAN   DEFAULT false
position  INT
createdAt TIMESTAMP
```

### Repository (`modules/checkedlist`)

`CheckedListRepository` interface + `ExposedCheckedListRepository`:

| Method | Purpose |
|---|---|
| `createPost(authorId, title): UUID` | Insert into `Posts` with `post_type='CHECKED_LIST'` |
| `bulkGetItems(postIds: List<UUID>): Map<UUID, List<CheckedListItem>>` | Batch resolver |
| `addItem(postId, text, position): CheckedListItem` | Append new item |
| `toggleItem(itemId, isChecked): CheckedListItem` | Update isChecked; throws NotFoundException |
| `deleteItem(itemId): Boolean` | Remove; returns false if not found |
| `getCheckedListPostIds(): List<UUID>` | For the `checkedListPosts` query |
| `findById(postId): CheckedListPost?` | For node resolver |

### Resolvers (`modules/checkedlist`)

| Resolver | Extends | Notes |
|---|---|---|
| `CheckedListPostNodeResolver` | `NodeResolvers.CheckedListPost()` | Relay refetch |
| `CheckedListItemNodeResolver` | `NodeResolvers.CheckedListItem()` | Relay refetch |
| `CheckedListPostsQueryResolver` | `QueryResolvers.CheckedListPosts()` | Queries `getCheckedListPostIds()` |
| `CreateCheckedListPostResolver` | `MutationResolvers.CreateCheckedListPost()` | Requires auth; creates post + initial items |
| `ToggleCheckedListItemResolver` | `MutationResolvers.ToggleCheckedListItem()` | Requires auth; validates item ownership |
| `AddCheckedListItemResolver` | `MutationResolvers.AddCheckedListItem()` | Requires auth |
| `DeleteCheckedListItemResolver` | `MutationResolvers.DeleteCheckedListItem()` | Requires auth; validates ownership |
| `CheckedListPostItemsBatchResolver` | `CheckedListPostResolvers.Items()` | Batch |
| `CheckedListPostReadTimeMinutesResolver` | `CheckedListPostResolvers.ReadTimeMinutes()` | Concatenate item texts; `max(0.5, wordCount / 200.0)` |
| `CheckedListPostAuthorBatchResolver` | `CheckedListPostResolvers.Author()` | Delegates to `UserRepository` |
| `CheckedListPostCommentsResolver` | `CheckedListPostResolvers.Comments()` | Delegates to `CommentRepository` |
| `CheckedListPostCommentCountResolver` | `CheckedListPostResolvers.CommentCount()` | |
| `CheckedListPostLikesResolver` | `CheckedListPostResolvers.Likes()` | Delegates to `LikeRepository` |
| `CheckedListPostLikeCountResolver` | `CheckedListPostResolvers.LikeCount()` | |
| `CheckedListPostIsLikedByMeResolver` | `CheckedListPostResolvers.IsLikedByMe()` | |
| `CheckedListPostViewCountBatchResolver` | `CheckedListPostResolvers.ViewCount()` | In analytics module; same `PostViews` table |

### Core resolver updates (targeted, minimal)

Two existing resolvers need to handle the new type. Both look up a post by ID, check the
`post_type` column, and return the correct concrete Viaduct type:

- `CommentPostResolver` — was `BlogPost`, now `BlogPost` or `CheckedListPost`
- `LikePostResolver` — same

These are the **only changes to existing resolver files** in Phase 2.

### DI wiring

Checkedlist module exports `checkedListKoinModule`. Analytics module's `analyticsKoinModule`
gains one new line for `CheckedListPostViewCountBatchResolver`. Root `KoinModules.kt` adds
`checkedListKoinModule` to `allModules`. **Two lines added to existing files total.**

### Complexity calculator changes

Three updates to `BlogFieldComplexityCalculator`:

1. Add `"checkedListPosts"` to `UNBOUNDED_QUERY_LISTS`.

2. Extend the post-type branch to cover `CheckedListPost` alongside `BlogPost`:
   ```kotlin
   "BlogPost", "CheckedListPost" -> name in UNBOUNDED_POST_LISTS
   ```
   `UNBOUNDED_POST_LISTS` already contains `"comments"` and `"likes"` — no change needed there.

3. Add a new case for `CheckedListPost.items` — unbounded list of `CheckedListItem`:
   ```kotlin
   "CheckedListPost" -> name == "items" || name in UNBOUNDED_POST_LISTS
   ```

Schema loading in `QueryComplexityGuard` / `QueryComplexityIntegrationTest` already handles
the checkedlist schema file via the `exists()`-filtered list added in Phase 1 — **no further
changes needed**.

### Tests

**Repository (H2) — `CheckedListRepositoryTest`:**
- `createPost` inserts into `Posts` with `post_type='CHECKED_LIST'`
- `bulkGetItems` returns correct items per post; empty list for blog posts
- `addItem` appends at correct position
- `toggleItem` flips `isChecked`; throws `NotFoundException` for unknown ID
- `deleteItem` removes item; returns false for unknown ID
- `getCheckedListPostIds` returns only checklist post IDs

**Resolver unit (MockK):**
- `CreateCheckedListPostResolverTest` — requires auth; creates post + items; returns `CheckedListPost`
- `ToggleCheckedListItemResolverTest` — requires auth; validates ownership; throws `AuthorizationException` for wrong user; returns updated item
- `AddCheckedListItemResolverTest` — requires auth; appended at end
- `DeleteCheckedListItemResolverTest` — requires auth; ownership check
- `CheckedListPostReadTimeMinutesResolverTest` — empty items → 0.5; items summing to 400 words → 2.0; mixed short items → floor applies
- `CheckedListPostViewCountResolverTest` — mock PostViewRepository; returns correct count
- `CommentPostResolverTest` — updated to cover `post_type='BLOG_POST'` → returns `BlogPost` and `post_type='CHECKED_LIST'` → returns `CheckedListPost`
- `LikePostResolverTest` — same

**Complexity calculator unit — `BlogFieldComplexityCalculatorTest`:**
- `CheckedListPost.comments` applies ×10 multiplier
- `CheckedListPost.likes` applies ×10 multiplier
- `CheckedListPost.items` applies ×10 multiplier
- `checkedListPosts` query applies ×10 multiplier
- `CheckedListPost.title` and other scalars cost 1 + child

**Complexity integration — `QueryComplexityIntegrationTest`:**
- Realistic `checkedListPosts { id title items { text isChecked } }` scores under threshold
- `checkedListPosts { comments { author { posts { id } } } }` scores over threshold
- New assertion pins that `CheckedListPost.items` in the schema is recognised (catches schema loading regression)

**Query tests (`query-tests.sh`):**
- `createCheckedListPost` with items → returns post with items array
- `checkedListPosts` → returns list
- `toggleCheckedListItem` → `isChecked` flips
- `addCheckedListItem` → new item appears on post
- `deleteCheckedListItem` → item removed
- `checkedListPost { readTimeMinutes }` → non-zero for items with text; minimum 0.5 for empty list
- `checkedListPost { viewCount }` → 0 initially, 1 after `recordPostView`
- Comment on checklist post → `comment { post { ... on CheckedListPost { title } } }` works
- Like on checklist post works

**E2e (`frontend/e2e/checkedlist.spec.ts`):**
- Create a checklist post; redirected to checklist detail page
- Detail page renders items as checkboxes, not a text body
- Toggle a checkbox; reload; state persists
- Add a new item; appears at end of list
- Delete an item; removed from list
- Like and comment on a checklist post
- Main feed shows both post types with visual distinction
- `/checklists` route shows only checklist posts

**Frontend changes:**
- Post type selector on create page (Blog Post vs Checklist)
- `/create-checklist` route or unified `/create` with type toggle
- Checklist detail page with interactive checkboxes
- Main feed distinguishes post types visually
- `/checklists` route

---

## Change surface summary

| Phase | Existing files changed | New files |
|---|---|---|
| 0 | `schema.graphqls`, `Tables.kt`, `Models.kt`, all resolver files (imports), `NodeResolvers.kt`, `KoinModules.kt`, `BlogFieldComplexityCalculator.kt`, `BlogFieldComplexityCalculatorTest.kt` | — |
| 1 | `settings.gradle.kts`, root `build.gradle.kts`, `KoinModules.kt` (+1 line), `BlogFieldComplexityCalculator.kt` (+1 entry), `QueryComplexityGuard.kt` (schema loading), `QueryComplexityIntegrationTest.kt` (schema loading) | Everything under `modules/analytics/` |
| 2 | `settings.gradle.kts`, root `build.gradle.kts`, `KoinModules.kt` (+1 line), `BlogFieldComplexityCalculator.kt` (+new cases), `CommentFieldResolvers.kt`, `LikeObjectFieldResolvers.kt`, `PostAnalytics.graphqls` (+3 lines), `analyticsKoinModule` (+1 resolver) | Everything under `modules/checkedlist/` |
