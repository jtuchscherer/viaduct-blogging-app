package org.tuchscherer.analytics.repositories

import java.util.UUID

/**
 * Repository for post view-count tracking.
 */
interface PostViewRepository {

    /**
     * Increment the view count for the given post.
     * Creates a row with viewCount=1 if none exists yet.
     */
    fun incrementViewCount(postId: UUID)

    /**
     * Fetch view counts for multiple posts in a single query.
     * Posts that have never been viewed are absent from the map (treat as 0 at the call site).
     */
    fun bulkGetViewCounts(postIds: List<UUID>): Map<UUID, Long>

    /**
     * Return up to [limit] post IDs sorted by view count descending.
     */
    fun getMostViewed(limit: Int): List<UUID>

    /**
     * Return the total number of views recorded across all posts.
     */
    fun getTotalViews(): Long
}
