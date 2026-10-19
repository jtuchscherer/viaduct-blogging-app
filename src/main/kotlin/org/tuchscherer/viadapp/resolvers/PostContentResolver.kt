package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.database.repositories.PostField
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.BlogPostResolvers
import viaduct.api.FieldValue
import viaduct.api.resolver.Resolver
import java.util.UUID

/**
 * Load large bodies only when clients or cross-module required selections request them.
 * Analytics' content dependency uses this same batch as an explicit client selection.
 */
@Resolver(objectValueFragment = "fragment _ on BlogPost { id }")
class PostContentResolver(
    private val postRepository: PostRepository,
) : BlogPostResolvers.Content() {
    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<String>> {
        if (contexts.isEmpty()) return emptyList()
        val ids = contexts.map { UUID.fromString(it.getObjectValue().getIdOrThrow().internalID) }
        val posts = postRepository.findProjectedByIds(ids.distinct(), setOf(PostField.CONTENT))
        return ids.map { id ->
            posts[id]?.content?.let { FieldValue.ofValue(it) }
                ?: FieldValue.ofError(NotFoundException("Post not found: $id"))
        }
    }
}
