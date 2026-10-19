package org.tuchscherer.analytics.repositories;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistence boundary for view counts, implemented by the Kotlin persistence adapter. */
public interface PostViewRepository {
    /** Creates a row with one view when the post has not been viewed before. */
    void incrementViewCount(UUID postId);

    /** One bulk read; unseen posts are absent and consumers treat them as zero. */
    Map<UUID, Long> bulkGetViewCounts(List<UUID> postIds);

    /** Up to limit post IDs ordered by descending view count. */
    List<UUID> getMostViewed(int limit);

    long getTotalViews();
}
