package org.tuchscherer.viadapp.checkedlist.resolvers

import org.tuchscherer.checkedlist.port.CheckedListCurrentUserProvider
import org.tuchscherer.checkedlist.port.ChecklistPostReader
import org.tuchscherer.viadapp.checkedlist.resolverbases.QueryResolvers
import viaduct.api.resolver.Resolver
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost

/**
 * Resolves [Query.myCheckedListPosts] — returns the authenticated user's checklist posts, newest first.
 */
@Resolver
class MyCheckedListPostsQueryResolver(
    private val currentUserProvider: CheckedListCurrentUserProvider,
    private val checklistPosts: ChecklistPostReader,
) : QueryResolvers.MyCheckedListPosts() {

    override suspend fun resolve(ctx: Context): List<ViaductCheckedListPost> {
        val userId = currentUserProvider.getCurrentUserId(ctx.requestContext)
        return checklistPosts.getCheckedListPostsByAuthorId(userId).map { it.toViaductPost(ctx) }
    }
}
