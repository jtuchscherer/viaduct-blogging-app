package org.tuchscherer.viadapp.checkedlist.resolvers

import org.tuchscherer.checkedlist.ChecklistService
import org.tuchscherer.checkedlist.port.CheckedListCurrentUserProvider
import org.tuchscherer.viadapp.checkedlist.resolverbases.MutationResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.resolver.Resolver
import viaduct.api.grts.CheckedListItem as ViaductCheckedListItem
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost
import viaduct.api.grts.PostStatus as ViaductPostStatus
import java.util.UUID

@Resolver
class CreateCheckedListPostMutationResolver : MutationResolvers.CreateCheckedListPost() {
    private val users: CheckedListCurrentUserProvider by inject(CheckedListCurrentUserProvider::class.java)
    private val service: ChecklistService by inject(ChecklistService::class.java)

    override suspend fun resolve(ctx: Context): ViaductCheckedListPost {
        val actorId = users.getCurrentUserId(ctx.requestContext)
        val input = ctx.arguments.input
        val status = if (input.status == ViaductPostStatus.DRAFT) "DRAFT" else "PUBLISHED"
        return service.create(actorId, input.title, input.description ?: "", input.items, status).toViaductPost(ctx)
    }
}

@Resolver
class AddCheckedListItemMutationResolver : MutationResolvers.AddCheckedListItem() {
    private val users: CheckedListCurrentUserProvider by inject(CheckedListCurrentUserProvider::class.java)
    private val service: ChecklistService by inject(ChecklistService::class.java)

    override suspend fun resolve(ctx: Context): ViaductCheckedListItem {
        val actorId = users.getCurrentUserId(ctx.requestContext)
        val input = ctx.arguments.input
        return service.addItem(actorId, UUID.fromString(input.postId.internalID), input.text).toViaductItem(ctx)
    }
}

@Resolver
class ToggleCheckedListItemMutationResolver : MutationResolvers.ToggleCheckedListItem() {
    private val users: CheckedListCurrentUserProvider by inject(CheckedListCurrentUserProvider::class.java)
    private val service: ChecklistService by inject(ChecklistService::class.java)

    override suspend fun resolve(ctx: Context): ViaductCheckedListItem =
        service.toggleItem(users.getCurrentUserId(ctx.requestContext), UUID.fromString(ctx.arguments.id.internalID))
            .toViaductItem(ctx)
}

@Resolver
class DeleteCheckedListItemMutationResolver : MutationResolvers.DeleteCheckedListItem() {
    private val users: CheckedListCurrentUserProvider by inject(CheckedListCurrentUserProvider::class.java)
    private val service: ChecklistService by inject(ChecklistService::class.java)

    override suspend fun resolve(ctx: Context): Boolean =
        service.deleteItem(users.getCurrentUserId(ctx.requestContext), UUID.fromString(ctx.arguments.id.internalID))
}

@Resolver
class UpdateCheckedListPostMutationResolver : MutationResolvers.UpdateCheckedListPost() {
    private val users: CheckedListCurrentUserProvider by inject(CheckedListCurrentUserProvider::class.java)
    private val service: ChecklistService by inject(ChecklistService::class.java)

    override suspend fun resolve(ctx: Context): ViaductCheckedListPost {
        val actorId = users.getCurrentUserId(ctx.requestContext)
        val input = ctx.arguments.input
        return service.update(actorId, UUID.fromString(input.id.internalID), input.title, input.description).toViaductPost(ctx)
    }
}

@Resolver
class DeleteCheckedListPostMutationResolver : MutationResolvers.DeleteCheckedListPost() {
    private val users: CheckedListCurrentUserProvider by inject(CheckedListCurrentUserProvider::class.java)
    private val service: ChecklistService by inject(ChecklistService::class.java)

    override suspend fun resolve(ctx: Context): Boolean =
        service.delete(users.getCurrentUserId(ctx.requestContext), UUID.fromString(ctx.arguments.id.internalID))
}

@Resolver
class UpdateCheckedListItemMutationResolver : MutationResolvers.UpdateCheckedListItem() {
    private val users: CheckedListCurrentUserProvider by inject(CheckedListCurrentUserProvider::class.java)
    private val service: ChecklistService by inject(ChecklistService::class.java)

    override suspend fun resolve(ctx: Context): ViaductCheckedListItem {
        val actorId = users.getCurrentUserId(ctx.requestContext)
        val input = ctx.arguments.input
        return service.updateItem(actorId, UUID.fromString(input.id.internalID), input.text).toViaductItem(ctx)
    }
}
