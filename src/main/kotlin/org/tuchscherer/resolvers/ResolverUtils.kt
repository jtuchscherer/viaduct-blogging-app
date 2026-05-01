package org.tuchscherer.viadapp.resolvers

import viaduct.api.context.ExecutionContext
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.grts.Comment as ViaductComment
import viaduct.api.grts.Like as ViaductLike
import viaduct.api.grts.User as ViaductUser

/**
 * Safe Long→Int conversion for GraphQL count fields.
 * GraphQL Int is 32-bit; repository counts are Long. Throws if the value exceeds Int.MAX_VALUE
 * so overflow is caught explicitly rather than wrapping silently.
 */
internal fun Long.toCountInt(): Int {
    require(this <= Int.MAX_VALUE) { "Count value $this exceeds GraphQL Int range (${Int.MAX_VALUE})" }
    return toInt()
}

internal fun org.tuchscherer.database.User.toViaductUser(ctx: ExecutionContext) =
    ViaductUser.of(ctx) {
        id(ctx.globalIDFor(ViaductUser.Reflection, id.value.toString()))
        username(username)
        email(email)
        name(name)
        createdAt(createdAt.toString())
    }

internal fun org.tuchscherer.database.Post.toViaductBlogPost(ctx: ExecutionContext) =
    ViaductBlogPost.of(ctx) {
        id(ctx.globalIDFor(ViaductBlogPost.Reflection, id.value.toString()))
        title(title)
        content(content)
        createdAt(createdAt.toString())
        updatedAt(updatedAt.toString())
    }

internal fun org.tuchscherer.database.Comment.toViaductComment(ctx: ExecutionContext) =
    ViaductComment.of(ctx) {
        id(ctx.globalIDFor(ViaductComment.Reflection, id.value.toString()))
        content(content)
        createdAt(createdAt.toString())
    }

internal fun org.tuchscherer.database.Like.toViaductLike(ctx: ExecutionContext) =
    ViaductLike.of(ctx) {
        id(ctx.globalIDFor(ViaductLike.Reflection, id.value.toString()))
        createdAt(createdAt.toString())
    }
