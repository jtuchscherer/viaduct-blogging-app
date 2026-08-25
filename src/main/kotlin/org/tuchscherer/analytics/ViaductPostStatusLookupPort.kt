package org.tuchscherer.analytics

import org.tuchscherer.analytics.port.PostStatusLookupPort
import org.tuchscherer.database.repositories.PostRepository
import java.util.UUID

/**
 * Root-project implementation of [PostStatusLookupPort], delegating to [PostRepository] so the
 * status filter stays in the repository layer.
 */
class ViaductPostStatusLookupPort(
    private val postRepository: PostRepository,
) : PostStatusLookupPort {
    override fun publishedIds(ids: List<UUID>): Set<UUID> = postRepository.publishedIds(ids)
}
