package org.tuchscherer.viadapp.checkedlist.resolvers

import org.tuchscherer.checkedlist.port.PostCreationPort
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

    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<ViaductCheckedListPost>> {
        val ids = contexts.map { UUID.fromString(it.id.internalID) }
        val byId = postCreationPort.getPostsData(ids)

        return contexts.zip(ids).map { (ctx, id) ->
            val data = byId[id]
                ?: return@map FieldValue.ofError(
                    NoSuchElementException("CheckedListPost not found: $id")
                )
            FieldValue.ofValue(data.toViaductPost(ctx))
        }
    }
}
