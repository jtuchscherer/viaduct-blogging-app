# Admin Section Implementation Plan

**Status**: Complete ✅
**Last Updated**: 2026-05-03

## Overview

Add an admin section where admin users can manage (CRUD) all users, posts, and comments. The admin section lives under `/admin/*` routes with its own layout.

## Key Decisions

1. **Admin identification**: `isAdmin` boolean column on Users table
2. **Bootstrap first admin**: SQL command (`sqlite3 blog.db "UPDATE users SET is_admin = 1 WHERE username = '...';"`)
3. **User deletion**: Cascades to all user's posts, comments, and likes; frontend shows confirmation dialog with counts
4. **Dashboard**: Stats cards showing total users, posts, comments, likes (future: activity graphs)

## Completed Items (2026-04-06)

- [x] Phase 1: Database & Model Changes - `isAdmin` column added to Users table and User model
- [x] Phase 2: GraphQL Schema - All admin types, queries, and mutations defined
- [x] Phase 3: Backend Authorization - `requireAdmin()` helper and resolvers implemented
- [x] Phase 4: Repository Extensions - All repository methods for admin operations
- [x] Phase 5: Frontend Admin Section - Layout, dashboard, users, posts, comments pages
- [x] Auth endpoints updated to include `isAdmin` in response
- [x] Frontend AuthContext updated with `isAdmin` flag
- [x] Admin link in header for admin users
- [x] `me` query resolver implemented

## Remaining Work

- [x] Phase 6: Testing - Backend unit tests for admin resolvers (`AdminMutationResolversTest.kt`, `AdminQueryResolversTest.kt`)
- [x] Phase 6: Testing - Repository integration tests (`UserRepositoryTest.kt`, `PostRepositoryTest.kt`, `CommentRepositoryTest.kt`, `LikeRepositoryTest.kt`)
- [x] Phase 6: Testing - Playwright E2E tests for admin section (`frontend/e2e/admin.spec.ts`, 18 tests × 3 browsers)
- [x] Phase 6: Testing - query-tests.sh additions for admin API

---

## Phase 1: Database & Model Changes

### 1.1 Add `isAdmin` column to Users table

**File**: `src/main/kotlin/org/tuchscherer/database/Tables.kt`

```kotlin
object Users : UUIDTable("users") {
    // ... existing columns ...
    val isAdmin = bool("is_admin").default(false)  // NEW
}
```

**File**: `src/main/kotlin/org/tuchscherer/database/Models.kt`

```kotlin
class User(id: EntityID<UUID>) : UUIDEntity(id) {
    // ... existing properties ...
    var isAdmin by Users.isAdmin  // NEW
}
```

### 1.2 Bootstrapping the first admin

Since we use SQLite file storage (`blog.db`), promote a user via CLI:

```bash
# After registering a user through the app
sqlite3 blog.db "UPDATE users SET is_admin = 1 WHERE username = 'admin';"

# Verify
sqlite3 blog.db "SELECT username, is_admin FROM users;"
```

**Note**: The column will be added automatically by `SchemaUtils.create()` on next startup (SQLite allows adding columns with defaults). For production, this should use a proper migration (Phase 16 work).

---

## Phase 2: GraphQL Schema

**File**: `src/main/viaduct/schema/schema.graphqls`

### 2.1 Extend User type

```graphql
type User {
  # ... existing fields ...
  isAdmin: Boolean! @resolver  # NEW - only visible to admins viewing other users
}
```

### 2.2 Admin queries

```graphql
# Stats for admin dashboard
type AdminStats {
  userCount: Int!
  postCount: Int!
  commentCount: Int!
  likeCount: Int!
}

# User content counts (for delete confirmation dialog)
type UserContentCounts {
  postCount: Int!
  commentCount: Int!
  likeCount: Int!
}

extend type Query {
  # ... existing queries ...

  # Admin dashboard stats
  adminStats: AdminStats! @resolver

  # Admin-only queries
  adminUsers: [User!]! @resolver
  adminUser(id: ID!): User @resolver
  adminUserContentCounts(userId: ID!): UserContentCounts! @resolver  # For delete confirmation
  adminPosts: [Post!]! @resolver
  adminPost(id: ID!): Post @resolver
  adminComments: [Comment!]! @resolver
  adminPostComments(postId: ID!): [Comment!]! @resolver
}
```

### 2.3 Admin mutations

```graphql
input AdminUpdateUserInput {
  id: ID!
  name: String
  email: String
  isAdmin: Boolean
}

input AdminUpdatePostInput {
  id: ID!
  title: String
  content: String
}

# Response type for user deletion (cascades to all user content)
type AdminDeleteUserResult {
  success: Boolean!
  postsDeleted: Int!
  commentsDeleted: Int!
  likesDeleted: Int!
}

extend type Mutation {
  # ... existing mutations ...

  # Admin user management
  adminUpdateUser(input: AdminUpdateUserInput!): User! @resolver
  adminDeleteUser(id: ID!): AdminDeleteUserResult! @resolver  # Cascades to posts/comments/likes

  # Admin post management (edit/delete any post)
  adminUpdatePost(input: AdminUpdatePostInput!): Post! @resolver
  adminDeletePost(id: ID!): Boolean! @resolver

  # Admin comment management (delete any comment)
  adminDeleteComment(id: ID!): Boolean! @resolver
}
```

---

## Phase 3: Backend Authorization

### 3.1 Add `requireAdmin()` helper

**File**: `src/main/kotlin/org/tuchscherer/auth/AuthHelpers.kt` (new file)

```kotlin
package org.tuchscherer.auth

import org.tuchscherer.database.User

/**
 * Extract authenticated user from resolver context, throw if not authenticated.
 */
fun requireAuth(ctx: Any): User {
    val requestContext = (ctx as? viaduct.api.context.ExecutionContext)
        ?.let { it.requestContext as? RequestContext }
        ?: throw AuthenticationException("Not authenticated")

    return requestContext.user
        ?: throw AuthenticationException("Not authenticated")
}

/**
 * Extract authenticated admin user from resolver context, throw if not admin.
 */
fun requireAdmin(ctx: Any): User {
    val user = requireAuth(ctx)
    if (!user.isAdmin) {
        throw AuthorizationException("Admin access required")
    }
    return user
}
```

### 3.2 Add `AuthorizationException`

**File**: `src/main/kotlin/org/tuchscherer/auth/Exceptions.kt`

```kotlin
class AuthorizationException(message: String) : RuntimeException(message)
```

### 3.3 Admin resolvers

Create new resolver files following existing patterns:

| File | Resolvers |
|------|-----------|
| `AdminUserResolvers.kt` | `AdminUsersResolver`, `AdminUserResolver`, `AdminUpdateUserResolver`, `AdminDeleteUserResolver` |
| `AdminPostResolvers.kt` | `AdminPostsResolver`, `AdminPostResolver`, `AdminUpdatePostResolver`, `AdminDeletePostResolver` |
| `AdminCommentResolvers.kt` | `AdminCommentsResolver`, `AdminPostCommentsResolver`, `AdminDeleteCommentResolver` |

**Example pattern** (`AdminUsersResolver`):

```kotlin
class AdminUsersResolver(
    private val userRepository: UserRepository
) : QueryResolvers.AdminUsers() {
    override fun resolve(ctx: Context): List<User> {
        requireAdmin(ctx)  // Throws if not admin
        return userRepository.findAll()
    }
}
```

### 3.4 Register resolvers in Koin

**File**: `src/main/kotlin/org/tuchscherer/config/KoinModules.kt`

```kotlin
val resolverModule = module {
    // ... existing resolvers ...

    // Admin resolvers
    singleOf(::AdminUsersResolver)
    singleOf(::AdminUserResolver)
    singleOf(::AdminUpdateUserResolver)
    singleOf(::AdminDeleteUserResolver)
    singleOf(::AdminPostsResolver)
    singleOf(::AdminPostResolver)
    singleOf(::AdminUpdatePostResolver)
    singleOf(::AdminDeletePostResolver)
    singleOf(::AdminCommentsResolver)
    singleOf(::AdminPostCommentsResolver)
    singleOf(::AdminDeleteCommentResolver)
}
```

---

## Phase 4: Repository Extensions

### 4.1 UserRepository additions

```kotlin
interface UserRepository {
    // ... existing methods ...
    fun findAll(): List<User>
    fun update(id: UUID, name: String?, email: String?, isAdmin: Boolean?): User?
    fun deleteWithCascade(id: UUID): DeleteUserResult
}

data class DeleteUserResult(
    val success: Boolean,
    val postsDeleted: Int,
    val commentsDeleted: Int,
    val likesDeleted: Int
)
```

The `deleteWithCascade` method deletes the user and all their associated content (posts, comments, likes) in a single transaction.

### 4.2 CommentRepository additions

```kotlin
interface CommentRepository {
    // ... existing methods ...
    fun findAll(): List<Comment>
    fun countByUserId(userId: UUID): Int
    fun deleteByUserId(userId: UUID): Int  // Returns count deleted
}
```

### 4.3 PostRepository additions

```kotlin
interface PostRepository {
    // ... existing methods ...
    fun countByAuthorId(authorId: UUID): Int
    fun deleteByAuthorId(authorId: UUID): Int  // Returns count deleted
}
```

### 4.4 LikeRepository additions

```kotlin
interface LikeRepository {
    // ... existing methods ...
    fun countByUserId(userId: UUID): Int
    fun deleteByUserId(userId: UUID): Int  // Returns count deleted
}
```

### 4.5 Stats queries for dashboard

```kotlin
interface UserRepository {
    fun count(): Int
}

interface PostRepository {
    fun count(): Int
}

interface CommentRepository {
    fun count(): Int
}

interface LikeRepository {
    fun count(): Int
}
```

---

## Phase 5: Frontend Admin Section

### 5.1 Route structure

```
/admin                    → AdminDashboard (overview/stats)
/admin/users              → AdminUsersPage (list all users)
/admin/users/:id          → AdminUserDetailPage (view/edit user)
/admin/posts              → AdminPostsPage (list all posts)
/admin/posts/:id          → AdminPostDetailPage (view/edit post)
/admin/comments           → AdminCommentsPage (list all comments)
```

### 5.2 Admin layout

**File**: `frontend/src/layouts/AdminLayout.tsx`

```tsx
// Sidebar navigation with links to Users, Posts, Comments
// Header showing "Admin Panel" + current admin user
// Protected: redirects to /login if not authenticated or not admin
```

### 5.3 Admin route guard

**File**: `frontend/src/components/AdminRoute.tsx`

```tsx
// Wraps admin routes
// Checks localStorage for authUser.isAdmin
// Redirects to / if not admin (with toast message)
```

### 5.4 GraphQL queries

**File**: `frontend/src/graphql/admin.ts`

```graphql
query AdminUsers {
  adminUsers { id username email name isAdmin createdAt }
}

query AdminPosts {
  adminPosts { id title author { username } createdAt }
}

query AdminComments {
  adminComments { id content author { username } post { title } createdAt }
}

mutation AdminDeleteUser($id: ID!) {
  adminDeleteUser(id: $id)
}

# ... etc
```

### 5.5 Pages to create

| Page | Features |
|------|----------|
| `AdminDashboard.tsx` | Stats cards showing total users, posts, comments, likes |
| `AdminUsersPage.tsx` | Table with username, email, name, isAdmin, actions (edit/delete) |
| `AdminUserDetailPage.tsx` | Edit form for name, email, isAdmin toggle |
| `AdminPostsPage.tsx` | Table with title, author, date, actions (view/edit/delete) |
| `AdminPostDetailPage.tsx` | Edit form for title, content |
| `AdminCommentsPage.tsx` | Table with content preview, author, post, date, delete action |

### 5.6 Delete user confirmation dialog

When admin clicks "Delete" on a user, show a confirmation modal:

```
┌─────────────────────────────────────────────────────┐
│  Delete User: johndoe                               │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ⚠️  This will permanently delete the user and     │
│  all their content:                                 │
│                                                     │
│    • 12 posts                                       │
│    • 45 comments                                    │
│    • 89 likes                                       │
│                                                     │
│  This action cannot be undone.                      │
│                                                     │
│            [Cancel]    [Delete User]                │
└─────────────────────────────────────────────────────┘
```

The dialog fetches `adminUserContentCounts(userId)` to show the counts before the admin confirms.

### 5.7 Admin dashboard design

```
┌─────────────────────────────────────────────────────┐
│  Admin Dashboard                                    │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│  │  Users   │  │  Posts   │  │ Comments │  │  Likes   │
│  │   156    │  │   342    │  │  1,247   │  │  5,891   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘
│                                                     │
│  [Future: Activity graph for last 30 days]          │
│                                                     │
└─────────────────────────────────────────────────────┘
```

**Future enhancement** (out of scope for initial implementation): Add a line graph showing posts, comments, likes, and page visits over the past 30 days.

### 5.8 Update User type in frontend

**File**: `frontend/src/types.ts`

```typescript
interface User {
  // ... existing fields ...
  isAdmin: boolean;
}
```

### 5.9 Update auth storage

When login response includes `isAdmin`, store it:

```typescript
localStorage.setItem('authUser', JSON.stringify({ ...user, isAdmin: user.isAdmin }));
```

---

## Phase 6: Testing

### 6.1 Backend unit tests

| Test file | Coverage |
|-----------|----------|
| `AdminStatsResolverTest.kt` | Auth checks, returns correct counts |
| `AdminUserResolversTest.kt` | Auth checks, CRUD operations, can't demote self, content counts |
| `AdminPostResolversTest.kt` | Auth checks, CRUD operations |
| `AdminCommentResolversTest.kt` | Auth checks, delete operations |

**Key test cases:**
- Non-authenticated user gets `AuthenticationException`
- Non-admin user gets `AuthorizationException`
- Admin can list/view/update/delete resources
- Admin cannot delete themselves
- Admin cannot remove their own admin status
- `adminStats` returns correct counts for users, posts, comments, likes
- `adminUserContentCounts` returns correct counts for a specific user
- `adminDeleteUser` cascades to delete all user's posts, comments, and likes

### 6.2 Repository integration tests

- `UserRepositoryTest.kt`: Add tests for `findAll()`, `count()`, `update()`, `deleteWithCascade()`
- `PostRepositoryTest.kt`: Add tests for `count()`, `countByAuthorId()`, `deleteByAuthorId()`
- `CommentRepositoryTest.kt`: Add tests for `findAll()`, `count()`, `countByUserId()`, `deleteByUserId()`
- `LikeRepositoryTest.kt`: Add tests for `count()`, `countByUserId()`, `deleteByUserId()`

### 6.3 API E2E tests

**File**: `query-tests.sh` additions

```bash
# Admin stats
test_admin_stats_requires_admin
test_admin_stats_returns_counts

# Admin user management
test_admin_users_requires_auth
test_admin_users_requires_admin
test_admin_can_list_users
test_admin_can_get_user_content_counts
test_admin_can_update_user
test_admin_can_delete_user_with_cascade
test_admin_cannot_delete_self

# Admin post management
test_admin_can_list_all_posts
test_admin_can_edit_any_post
test_admin_can_delete_any_post

# Admin comment management
test_admin_can_delete_any_comment
```

### 6.4 Playwright E2E tests

**File**: `frontend/e2e/admin.spec.ts`

```typescript
// Setup: Create admin user via SQL or test helper

// Access control
// Test: Admin can access /admin dashboard
// Test: Non-admin redirected from /admin with message

// Dashboard
// Test: Dashboard shows stats cards with correct counts

// User management
// Test: Admin can view user list
// Test: Admin can edit user (name, email, isAdmin toggle)
// Test: Delete user shows confirmation with content counts
// Test: Confirming delete removes user and their content

// Post management
// Test: Admin can delete any post

// Comment management
// Test: Admin can delete any comment
```

---

## Implementation Order

| Step | Description | Estimated Tests |
|------|-------------|-----------------|
| 1 | Add `isAdmin` to DB schema + model | — |
| 2 | Add `requireAdmin()` helper + exception | 2 unit tests |
| 3 | Add `User.isAdmin` field resolver | 2 unit tests |
| 4 | Add repository methods (`findAll`, `count`, `update`, `deleteWithCascade`, etc.) | 10 integration tests |
| 5 | Add admin GraphQL schema (including `AdminStats`, `UserContentCounts`) | — |
| 6 | Implement admin query resolvers (including `adminStats`, `adminUserContentCounts`) | 15 unit tests |
| 7 | Implement admin mutation resolvers (including cascading delete) | 18 unit tests |
| 8 | Add API E2E tests | 15 tests |
| 9 | Frontend: AdminLayout + AdminRoute | — |
| 10 | Frontend: AdminDashboard with stats cards | — |
| 11 | Frontend: AdminUsersPage + detail + delete confirmation modal | — |
| 12 | Frontend: AdminPostsPage + detail | — |
| 13 | Frontend: AdminCommentsPage | — |
| 14 | Playwright E2E tests | 10 tests |

**Total new tests**: ~72

---

## Security Considerations

1. **Authorization at resolver level**: Every admin resolver calls `requireAdmin()` first
2. **Self-protection**: Admin cannot delete themselves or remove their own admin status
3. **Cascading deletes with confirmation**: Deleting a user removes all their posts, comments, and likes; the frontend shows a confirmation dialog with counts before proceeding
4. **Audit logging** (future): Log admin actions for accountability

---

## Out of Scope (Future Work)

- Role-based access control (RBAC) beyond admin/non-admin
- Admin activity audit log
- Soft deletes instead of hard deletes
- Bulk operations (delete multiple users/posts)
- Admin search/filter functionality
- Pagination on admin list views
- Dashboard activity graphs (posts/comments/likes/visits over 30 days)

---

## Success Criteria

1. Admin can log in and access `/admin` routes
2. Non-admin users are redirected away from `/admin`
3. Admin can view, edit, and delete any user (except themselves)
4. Admin can view, edit, and delete any post
5. Admin can view and delete any comment
6. All existing tests still pass
7. New tests cover admin functionality
8. `./gradlew test` passes with all new tests
