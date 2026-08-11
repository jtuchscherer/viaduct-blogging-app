package org.tuchscherer.viadapp.checkedlist.resolvers

import org.tuchscherer.checkedlist.port.PostCreationPort
import org.tuchscherer.resolverkit.batchNodeResolve
import org.tuchscherer.viadapp.checkedlist.resolverbases.NodeResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.FieldValue
import viaduct.api.resolver.Resolver
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost
import java.util.UUID

/**
 * Relay node resolver for [CheckedListPost]. Fetches scalar fields (title, createdAt, updatedAt)
 * from [PostCreationPort], which reads the Posts table in the root project.
 */
@Resolver
class CheckedListPostBatchResolver : NodeResolvers.CheckedListPost() {
    private val postCreationPort: PostCreationPort by inject(PostCreationPort::class.java)

    override suspend fun batchResolve(contexts: List<Context>): Map<Context, FieldValue<ViaductCheckedListPost>> =
        batchNodeResolve(
            contexts = contexts,
            extractId = { UUID.fromString(it.id.internalID) },
            findByIds = postCreationPort::getPostsData,
            transform = { data, ctx -> data.toViaductPost(ctx) },
            notFound = { id -> NoSuchElementException("CheckedListPost not found: $id") },
        )
}
