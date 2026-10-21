package org.tuchscherer.checkedlist.port

import java.util.UUID

/**
 * # Ports and Adapters (Hexagonal Architecture)
 *
 * The `checkedlist` module is compiled in isolation — it has no compile-time dependency on the
 * root project and cannot import `Post`, `Posts`, `PostType`, Exposed DAO types, or any other
 * root-project class.
 *
 * The interfaces in this file are **ports**: contracts expressed entirely in the module's own
 * terms (plain data classes and `UUID`s). They define *what* the module needs from the outside
 * world without coupling it to any particular implementation.
 *
 * The **adapters** — concrete classes that implement these ports using the root project's
 * database layer — live in the root project's repositories and checkedlist adapters
 * (e.g. `ExposedChecklistPostRepository`, `ViaductPostSocialAccess`). They are registered via Koin so
 * the module never knows which concrete class it's talking to.
 *
 * This boundary means:
 * - The checkedlist module can be compiled, tested, and reasoned about independently.
 * - The root project can change its persistence layer without touching module code.
 * - Module unit tests can stub any port with a simple mock.
 */

// ── Data views ────────────────────────────────────────────────────────────────

/**
 * Minimal view of a post's scalar fields as seen from the checkedlist module.
 * Populated by [ChecklistPostRepository] from the Posts table in the root project.
 *
 * [description] is persisted in the Posts.content column for CheckedListPost rows.
 */
data class PostData(
    val id: UUID,
    val title: String,
    val description: String,
    val authorId: UUID,
    /** "DRAFT" or "PUBLISHED". A string so the module needs no root-project enum. */
    val status: String,
    /** Null while a draft. */
    val publishedAt: String?,
    val createdAt: String,
    val updatedAt: String,
)

/**
 * Minimal view of a comment needed to build a ViaductComment node reference.
 */
data class CommentView(
    val id: UUID,
    val content: String,
    val createdAt: String,
)

/**
 * Minimal view of a like needed to build a ViaductLike node reference.
 */
data class LikeView(
    val id: UUID,
    val createdAt: String,
)

// ── Port interfaces (implemented by the root project) ─────────────────────────

/**
 * Read-only access to CheckedListPost metadata.
 * Implemented in the root project as [ExposedChecklistPostRepository] so the checkedlist
 * module stays free of root-project compile-time dependencies.
 */
interface ChecklistPostReader {
    fun getPostData(id: UUID): PostData?
    fun getPostsData(ids: List<UUID>): Map<UUID, PostData>
    /** Published checklists, newest publication first. */
    fun getAllCheckedListPosts(): List<PostData>
    /** All checklists by the author, including drafts. */
    fun getCheckedListPostsByAuthorId(authorId: UUID): List<PostData>
    fun getAuthorIdsForPosts(postIds: List<UUID>): Map<UUID, UUID>
}

interface ChecklistPostWriter {
    fun createCheckedListPost(
        title: String,
        authorId: UUID,
        description: String = "",
        status: String = "PUBLISHED",
    ): PostData
    fun updateCheckedListPost(id: UUID, title: String? = null, description: String? = null): PostData?
    /** Removes the post and all social, checklist, and analytics dependents. */
    fun deleteCheckedListPost(id: UUID): Boolean
}

/** Persistence contract for application commands that both read and write metadata. */
interface ChecklistPostRepository : ChecklistPostReader, ChecklistPostWriter

/**
 * Access to comments and likes data on behalf of the checkedlist module.
 * Implemented in the root project so the module avoids circular dependencies.
 */
interface PostSocialPort {
    fun getCommentsForPost(postId: UUID): List<CommentView>
    fun getCommentCountForPost(postId: UUID): Long
    fun getLikesForPost(postId: UUID): List<LikeView>
    fun getLikeCountForPost(postId: UUID): Long
    fun isLikedByUser(postId: UUID, userId: UUID): Boolean
}

/**
 * Provides the authenticated user's UUID from an opaque Viaduct request context.
 * Implemented in the root project by casting to [org.tuchscherer.auth.RequestContext].
 * Throws an application-level exception (mapped to 401) when the user is not authenticated.
 */
interface CheckedListCurrentUserProvider {
    fun getViewer(requestContext: Any?): CheckedListViewer?
    /**
     * Extracts the authenticated user's UUID from the opaque request context.
     * @throws org.tuchscherer.auth.AuthenticationException when the user is not authenticated.
     */
    fun getCurrentUserId(requestContext: Any?): UUID
}

data class CheckedListViewer(val id: UUID, val isAdmin: Boolean)
