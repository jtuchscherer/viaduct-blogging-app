package org.tuchscherer.auth

import org.tuchscherer.database.PostStatus
import java.util.UUID

/**
 * Who may read a post.
 *
 * Every read path funnels through here — `post(id)`, `node(id)` for both post types, and the
 * admin surface — so the rule exists once rather than being re-derived per resolver. Missing one
 * path is how a draft leaks, and `node(id)` is the easy one to miss: it is how the post detail
 * and edit pages fetch a post, and it bypasses the list queries entirely.
 *
 * List queries do not use this. They filter in SQL instead, so a draft never leaves the database.
 */
object PostVisibility {

    /**
     * True when a viewer may read a post.
     *
     * @param status the post's [PostStatus]
     * @param authorId the post's author
     * @param viewerId the authenticated viewer, or null when unauthenticated
     * @param viewerIsAdmin whether that viewer is an admin
     */
    fun canView(
        status: String,
        authorId: UUID,
        viewerId: UUID?,
        viewerIsAdmin: Boolean = false,
    ): Boolean {
        if (status == PostStatus.PUBLISHED) return true
        if (viewerId == null) return false
        return viewerId == authorId || viewerIsAdmin
    }

    /** True when the viewer may change a post's publication state: the author, and only them. */
    fun canChangeStatus(authorId: UUID, viewerId: UUID?): Boolean =
        viewerId != null && viewerId == authorId
}
