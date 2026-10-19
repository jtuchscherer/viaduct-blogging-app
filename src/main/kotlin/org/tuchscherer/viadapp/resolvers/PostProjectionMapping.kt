package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.database.PostType
import org.tuchscherer.database.repositories.PostField
import org.tuchscherer.database.repositories.PostProjection
import viaduct.api.context.ExecutionContext
import viaduct.api.context.ResolverExecutionContext
import viaduct.api.grts.BlogPost
import viaduct.api.grts.CheckedListPost
import viaduct.api.grts.Post
import viaduct.api.grts.PostStatus

/**
 * rc.1 Java code generation rejects selective resolvers anywhere in the shared schema.
 * Load the small node-owned scalar projection; content has its own batched field resolver.
 */
internal val blogPostNodeFields = setOf(
    PostField.TITLE,
    PostField.STATUS,
    PostField.PUBLISHED_AT,
    PostField.CREATED_AT,
    PostField.UPDATED_AT,
)

internal fun PostProjection.toViaductBlogPost(ctx: ExecutionContext): BlogPost = BlogPost.of(ctx) {
    // Node identity also supports author/analytics resolvers whose required fragments use id.
    id(ctx.globalIDFor(BlogPost.Reflection, id.toString()))
    title(requireNotNull(title))
    status(PostStatus.valueOf(requireNotNull(status)))
    publishedAt(publishedAt?.toString())
    createdAt(requireNotNull(createdAt).toString())
    updatedAt(requireNotNull(updatedAt).toString())
}

internal fun PostProjection.toViaductPost(ctx: ResolverExecutionContext<*>): Post = when (postType) {
    PostType.CHECKED_LIST -> ctx.ref(ctx.globalIDFor(CheckedListPost.Reflection, id.toString()))
    else -> ctx.ref(ctx.globalIDFor(BlogPost.Reflection, id.toString()))
}
