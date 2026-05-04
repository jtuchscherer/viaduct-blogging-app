package org.tuchscherer.analytics

import org.tuchscherer.analytics.port.PostTypeLookupPort
import org.tuchscherer.analytics.port.PostTypeLookupPort.PostKind
import org.tuchscherer.database.PostType
import org.tuchscherer.database.repositories.PostRepository
import java.util.UUID

/**
 * Root-project implementation of [PostTypeLookupPort].
 *
 * Queries the [Posts] table (via [PostRepository]) to determine whether each UUID belongs to a
 * [BlogPost] or [CheckedListPost]. IDs that are not found in the Posts table are absent from
 * the returned map.
 */
class ViaductPostTypeLookupPort(
    private val postRepository: PostRepository
) : PostTypeLookupPort {

    override fun getPostTypes(ids: List<UUID>): Map<UUID, PostKind> {
        if (ids.isEmpty()) return emptyMap()
        return postRepository.findByIds(ids).mapValues { (_, post) ->
            when (post.postType) {
                PostType.CHECKED_LIST -> PostKind.CHECKLIST_POST
                else -> PostKind.BLOG_POST
            }
        }
    }
}
