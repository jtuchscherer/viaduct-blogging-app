package org.tuchscherer.viadapp.checkedlist.resolvers

import org.tuchscherer.checkedlist.repositories.CheckedListItemRepository
import org.tuchscherer.checkedlist.port.ChecklistPostReader
import org.tuchscherer.checkedlist.port.CheckedListCurrentUserProvider
import org.tuchscherer.checkedlist.isVisibleTo
import org.tuchscherer.shared.batchNodeResolve
import org.tuchscherer.viadapp.checkedlist.resolverbases.NodeResolvers
import viaduct.api.FieldValue
import viaduct.api.resolver.Resolver
import viaduct.api.grts.CheckedListItem as ViaductCheckedListItem
import java.util.UUID

/**
 * Relay node resolver for [CheckedListItem]. Fetches item scalar fields from
 * [CheckedListItemRepository].
 */
@Resolver
class CheckedListItemBatchResolver(
    private val itemRepository: CheckedListItemRepository,
    private val posts: ChecklistPostReader,
    private val users: CheckedListCurrentUserProvider,
) : NodeResolvers.CheckedListItem() {

    override suspend fun batchResolve(contexts: List<Context>): Map<Context, FieldValue<ViaductCheckedListItem>> =
        batchNodeResolve(
            contexts = contexts,
            extractId = { UUID.fromString(it.id.internalID) },
            findByIds = { ids ->
                val items = itemRepository.findByIds(ids)
                if (items.isEmpty()) emptyMap() else {
                    val parents = posts.getPostsData(items.values.map { it.postId }.distinct())
                    items.filterValues { item ->
                        parents[item.postId]?.isVisibleTo(contexts.firstOrNull()?.requestContext, users) == true
                    }
                }
            },
            transform = { data, ctx ->
                ViaductCheckedListItem.of(ctx) {
                    id(ctx.globalIDFor(ViaductCheckedListItem.Reflection, data.id.toString()))
                    text(data.text)
                    checked(data.checked)
                    position(data.position)
                    createdAt(data.createdAt)
                }
            },
            notFound = { id -> NoSuchElementException("CheckedListItem not found: $id") },
        )
}
