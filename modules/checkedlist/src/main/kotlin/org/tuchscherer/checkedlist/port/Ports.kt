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
 * database layer — live in `src/main/kotlin/org/tuchscherer/checkedlist/` in the root project
 * (e.g. `ViaductPostCreationPort`, `ViaductPostSocialPort`). They are registered via Koin so
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
 * Populated by [PostCreationPort] from the Posts table in the root project.
 *
 * [description] is persisted in the Posts.content column for CheckedListPost rows.
 */
data class PostData(
    val id: UUID,
    val title: String,
    val description: String,
    val authorId: UUID,
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
 * Access to the Posts table for creating and reading CheckedListPost metadata.
 * Implemented in the root project as [ViaductPostCreationPort] so the checkedlist
 * module stays free of root-project compile-time dependencies.
 */
interface PostCreationPort {
    /** Creates a new post row with postType=CHECKED_LIST, returning its scalar data. */
    fun createCheckedListPost(title: String, authorId: UUID, description: String = ""): PostData

    /** Fetches scalar fields for a single CheckedListPost by ID, or null if not found. */
    fun getPostData(id: UUID): PostData?

    /** Batch-fetches scalar fields for multiple CheckedListPosts. Missing IDs are absent. */
    fun getPostsData(ids: List<UUID>): Map<UUID, PostData>

    /** Returns all CheckedListPost rows ordered by createdAt DESC. */
    fun getAllCheckedListPosts(): List<PostData>

    /** Returns authorId for each requested post ID. Missing IDs are absent. */
    fun getAuthorIdsForPosts(postIds: List<UUID>): Map<UUID, UUID>

    /**
     * Updates the title and/or description of an existing CheckedListPost.
     * Returns the updated [PostData], or null if not found.
     */
    fun updateCheckedListPost(id: UUID, title: String? = null, description: String? = null): PostData?

    /**
     * Deletes a CheckedListPost row (and its dependent comments/likes) from the Posts table.
     * The caller must delete checklist items beforehand.
     * Returns true if the post existed and was removed.
     */
    fun deleteCheckedListPost(id: UUID): Boolean
}

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
    /**
     * Extracts the authenticated user's UUID from the opaque request context.
     * @throws RuntimeException (or a domain exception) when the user is not authenticated.
     */
    fun getCurrentUserId(requestContext: Any?): UUID
}
