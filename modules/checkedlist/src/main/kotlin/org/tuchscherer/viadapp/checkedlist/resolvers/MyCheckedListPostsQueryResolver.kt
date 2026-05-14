package org.tuchscherer.viadapp.checkedlist.resolvers

import org.tuchscherer.checkedlist.port.CheckedListCurrentUserProvider
import org.tuchscherer.checkedlist.port.PostCreationPort
import org.tuchscherer.viadapp.checkedlist.resolverbases.QueryResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.resolver.Resolver
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost

/**
 * Resolves [Query.myCheckedListPosts] — returns the authenticated user's checklist posts, newest first.
 */
@Resolver
class MyCheckedListPostsQueryResolver : QueryResolvers.MyCheckedListPosts() {
    private val currentUserProvider: CheckedListCurrentUserProvider
        by inject(CheckedListCurrentUserProvider::class.java)
    private val postCreationPort: PostCreationPort by inject(PostCreationPort::class.java)

    override suspend fun resolve(ctx: Context): List<ViaductCheckedListPost> {
        val userId = currentUserProvider.getCurrentUserId(ctx.requestContext)
        return postCreationPort.getCheckedListPostsByAuthorId(userId).map { it.toViaductPost(ctx) }
    }
}
