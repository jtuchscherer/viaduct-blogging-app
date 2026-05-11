package org.tuchscherer.viadapp.checkedlist.resolvers

import org.tuchscherer.checkedlist.port.PostCreationPort
import org.tuchscherer.viadapp.checkedlist.resolverbases.QueryResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.resolver.Resolver
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost

/**
 * Resolves [Query.checkedListPosts] — returns all checklist posts ordered newest-first.
 */
@Resolver
class CheckedListPostsQueryResolver : QueryResolvers.CheckedListPosts() {
    private val postCreationPort: PostCreationPort by inject(PostCreationPort::class.java)

    override suspend fun resolve(ctx: Context): List<ViaductCheckedListPost> =
        postCreationPort.getAllCheckedListPosts().map { it.toViaductPost(ctx) }
}
