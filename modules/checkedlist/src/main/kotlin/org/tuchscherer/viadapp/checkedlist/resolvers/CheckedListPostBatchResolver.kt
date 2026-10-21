package org.tuchscherer.viadapp.checkedlist.resolvers

import org.tuchscherer.checkedlist.port.ChecklistPostReader
import org.tuchscherer.checkedlist.port.CheckedListCurrentUserProvider
import org.tuchscherer.checkedlist.isVisibleTo
import org.tuchscherer.shared.batchNodeResolve
import org.tuchscherer.viadapp.checkedlist.resolverbases.NodeResolvers
import viaduct.api.FieldValue
import viaduct.api.resolver.Resolver
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost
import java.util.UUID

/**
 * Relay node resolver for [CheckedListPost]. Fetches scalar fields (title, createdAt, updatedAt)
 * from [ChecklistPostReader], which reads the Posts table in the root project.
 */
@Resolver
class CheckedListPostBatchResolver(
    private val checklistPosts: ChecklistPostReader,
    private val users: CheckedListCurrentUserProvider,
) : NodeResolvers.CheckedListPost() {

    override suspend fun batchResolve(contexts: List<Context>): Map<Context, FieldValue<ViaductCheckedListPost>> =
        batchNodeResolve(
            contexts = contexts,
            extractId = { UUID.fromString(it.id.internalID) },
            findByIds = { ids ->
                checklistPosts.getPostsData(ids).filterValues {
                    it.isVisibleTo(contexts.firstOrNull()?.requestContext, users)
                }
            },
            transform = { data, ctx -> data.toViaductPost(ctx) },
            notFound = { id -> NoSuchElementException("CheckedListPost not found: $id") },
        )
}
