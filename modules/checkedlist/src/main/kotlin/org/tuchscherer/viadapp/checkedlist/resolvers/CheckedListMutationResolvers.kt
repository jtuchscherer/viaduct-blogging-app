package org.tuchscherer.viadapp.checkedlist.resolvers

import org.tuchscherer.checkedlist.ChecklistService
import org.tuchscherer.checkedlist.port.CheckedListCurrentUserProvider
import org.tuchscherer.viadapp.checkedlist.resolverbases.MutationResolvers
import viaduct.api.resolver.Resolver
import viaduct.api.grts.CheckedListItem as ViaductCheckedListItem
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost
import viaduct.api.grts.PostStatus as ViaductPostStatus
import java.util.UUID

@Resolver
class CreateCheckedListPostMutationResolver(
    private val users: CheckedListCurrentUserProvider,
    private val service: ChecklistService,
) : MutationResolvers.CreateCheckedListPost() {

    override suspend fun resolve(ctx: Context): ViaductCheckedListPost {
        val actorId = users.getCurrentUserId(ctx.requestContext)
        val input = ctx.arguments.input
        val status = if (input.status == ViaductPostStatus.DRAFT) "DRAFT" else "PUBLISHED"
        return service.create(actorId, input.title, input.description ?: "", input.items, status).toViaductPost(ctx)
    }
}

@Resolver
class AddCheckedListItemMutationResolver(
    private val users: CheckedListCurrentUserProvider,
    private val service: ChecklistService,
) : MutationResolvers.AddCheckedListItem() {

    override suspend fun resolve(ctx: Context): ViaductCheckedListItem {
        val actorId = users.getCurrentUserId(ctx.requestContext)
        val input = ctx.arguments.input
        return service.addItem(actorId, UUID.fromString(input.postId.internalID), input.text).toViaductItem(ctx)
    }
}

@Resolver
class ToggleCheckedListItemMutationResolver(
    private val users: CheckedListCurrentUserProvider,
    private val service: ChecklistService,
) : MutationResolvers.ToggleCheckedListItem() {

    override suspend fun resolve(ctx: Context): ViaductCheckedListItem =
        service.toggleItem(users.getCurrentUserId(ctx.requestContext), UUID.fromString(ctx.arguments.id.internalID))
            .toViaductItem(ctx)
}

@Resolver
class DeleteCheckedListItemMutationResolver(
    private val users: CheckedListCurrentUserProvider,
    private val service: ChecklistService,
) : MutationResolvers.DeleteCheckedListItem() {

    override suspend fun resolve(ctx: Context): Boolean =
        service.deleteItem(users.getCurrentUserId(ctx.requestContext), UUID.fromString(ctx.arguments.id.internalID))
}

@Resolver
class UpdateCheckedListPostMutationResolver(
    private val users: CheckedListCurrentUserProvider,
    private val service: ChecklistService,
) : MutationResolvers.UpdateCheckedListPost() {

    override suspend fun resolve(ctx: Context): ViaductCheckedListPost {
        val actorId = users.getCurrentUserId(ctx.requestContext)
        val input = ctx.arguments.input
        return service.update(actorId, UUID.fromString(input.id.internalID), input.title, input.description)
            .toViaductPost(ctx)
    }
}

@Resolver
class DeleteCheckedListPostMutationResolver(
    private val users: CheckedListCurrentUserProvider,
    private val service: ChecklistService,
) : MutationResolvers.DeleteCheckedListPost() {

    override suspend fun resolve(ctx: Context): Boolean =
        service.delete(users.getCurrentUserId(ctx.requestContext), UUID.fromString(ctx.arguments.id.internalID))
}

@Resolver
class UpdateCheckedListItemMutationResolver(
    private val users: CheckedListCurrentUserProvider,
    private val service: ChecklistService,
) : MutationResolvers.UpdateCheckedListItem() {

    override suspend fun resolve(ctx: Context): ViaductCheckedListItem {
        val actorId = users.getCurrentUserId(ctx.requestContext)
        val input = ctx.arguments.input
        return service.updateItem(actorId, UUID.fromString(input.id.internalID), input.text).toViaductItem(ctx)
    }
}
