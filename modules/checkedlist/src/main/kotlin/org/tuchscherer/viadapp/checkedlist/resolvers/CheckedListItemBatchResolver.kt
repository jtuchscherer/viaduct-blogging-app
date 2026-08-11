package org.tuchscherer.viadapp.checkedlist.resolvers

import org.tuchscherer.checkedlist.repositories.CheckedListItemRepository
import org.tuchscherer.resolverkit.batchNodeResolve
import org.tuchscherer.viadapp.checkedlist.resolverbases.NodeResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.FieldValue
import viaduct.api.resolver.Resolver
import viaduct.api.grts.CheckedListItem as ViaductCheckedListItem
import java.util.UUID

/**
 * Relay node resolver for [CheckedListItem]. Fetches item scalar fields from
 * [CheckedListItemRepository].
 */
@Resolver
class CheckedListItemBatchResolver : NodeResolvers.CheckedListItem() {
    private val itemRepository: CheckedListItemRepository by inject(CheckedListItemRepository::class.java)

    override suspend fun batchResolve(contexts: List<Context>): Map<Context, FieldValue<ViaductCheckedListItem>> =
        batchNodeResolve(
            contexts = contexts,
            extractId = { UUID.fromString(it.id.internalID) },
            findByIds = itemRepository::findByIds,
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
