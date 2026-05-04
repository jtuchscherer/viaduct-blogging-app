package org.tuchscherer.analytics.port

import java.util.UUID

/**
 * Port for resolving the concrete post type (BlogPost vs CheckedListPost) for a set of post IDs.
 *
 * Implemented in the root project by [ViaductPostTypeLookupPort], which reads the Posts table.
 * Analytics resolvers use this at query time to build the correct Viaduct node-refs for
 * mixed-type results (e.g. the [trending] query).
 */
interface PostTypeLookupPort {
    enum class PostKind { BLOG_POST, CHECKLIST_POST }

    /**
     * Returns the [PostKind] for each ID in [ids].
     * IDs not found in any posts table are absent from the returned map.
     */
    fun getPostTypes(ids: List<UUID>): Map<UUID, PostKind>
}
