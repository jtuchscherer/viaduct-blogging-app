package org.tuchscherer.viadapp.checkedlist.resolvers

import org.tuchscherer.checkedlist.port.ChecklistPostReader
import org.tuchscherer.viadapp.checkedlist.resolverbases.QueryResolvers
import viaduct.api.resolver.Resolver
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost

/**
 * Resolves [Query.checkedListPosts] — returns all checklist posts ordered newest-first.
 */
@Resolver
class CheckedListPostsQueryResolver(
    private val checklistPosts: ChecklistPostReader,
) : QueryResolvers.CheckedListPosts() {

    override suspend fun resolve(ctx: Context): List<ViaductCheckedListPost> =
        checklistPosts.getAllCheckedListPosts().map { it.toViaductPost(ctx) }
}
