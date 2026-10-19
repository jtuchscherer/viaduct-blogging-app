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
import viaduct.api.select.SelectionSet
import viaduct.api.types.CompositeOutput

/**
 * Node-owned scalar coordinates use schema field names, so aliases/fragments share one
 * projection. Content is owned by its separate batched field resolver.
 */
internal fun SelectionSet<out CompositeOutput>.postFields(): Set<PostField> =
    selectedFieldCoordinates().mapNotNull { coordinate ->
        when (coordinate.fieldName) {
            "title" -> PostField.TITLE
            "status" -> PostField.STATUS
            "publishedAt" -> PostField.PUBLISHED_AT
            "createdAt" -> PostField.CREATED_AT
            "updatedAt" -> PostField.UPDATED_AT
            else -> null
        }
    }.toSet()

internal fun PostProjection.toViaductBlogPost(ctx: ExecutionContext): BlogPost = BlogPost.of(ctx) {
    // Node identity also supports author/analytics resolvers whose required fragments use id.
    id(ctx.globalIDFor(BlogPost.Reflection, id.toString()))
    if (PostField.TITLE in fields) title(requireNotNull(title))
    if (PostField.STATUS in fields) status(PostStatus.valueOf(requireNotNull(status)))
    if (PostField.PUBLISHED_AT in fields) publishedAt(publishedAt?.toString())
    if (PostField.CREATED_AT in fields) createdAt(requireNotNull(createdAt).toString())
    if (PostField.UPDATED_AT in fields) updatedAt(requireNotNull(updatedAt).toString())
}

internal fun PostProjection.toViaductPost(ctx: ResolverExecutionContext<*>): Post = when (postType) {
    PostType.CHECKED_LIST -> ctx.ref(ctx.globalIDFor(CheckedListPost.Reflection, id.toString()))
    else -> ctx.ref(ctx.globalIDFor(BlogPost.Reflection, id.toString()))
}
