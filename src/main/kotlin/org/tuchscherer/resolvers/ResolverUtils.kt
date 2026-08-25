package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.PostVisibility

import org.tuchscherer.database.PostType
import viaduct.api.context.ExecutionContext
import viaduct.api.context.ResolverExecutionContext
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.grts.PostStatus as ViaductPostStatus
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost
import viaduct.api.grts.Comment as ViaductComment
import viaduct.api.grts.Like as ViaductLike
import viaduct.api.grts.Post as ViaductPost
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

/**
 * Extract the UUID from a Viaduct global ID (base64 `TypeName:uuid`).
 *
 * Used by mutations whose argument is a bare `ID!` rather than `@idOf(type: ...)`, so that one
 * mutation can accept either post type. The type prefix is discarded because both post types
 * live in the same table.
 *
 * The analytics module has its own copy: it is compiled in isolation and cannot depend on the
 * root project.
 */
/**
 * Whether [viewer] may read this post, delegating to [PostVisibility].
 *
 * An extension so the single-post read paths cannot accidentally disagree about how the
 * viewer's id and admin flag are pulled out of the entity.
 */
internal fun org.tuchscherer.database.Post.isVisibleTo(viewer: org.tuchscherer.database.User?): Boolean =
    PostVisibility.canView(
        status = status,
        authorId = authorId.value,
        viewerId = viewer?.id?.value,
        viewerIsAdmin = viewer?.isAdmin ?: false,
    )

/**
 * Rejects engagement with a draft.
 *
 * A draft has no audience, so it cannot be commented on, liked or counted as viewed — not even
 * by its author. Editing a draft is a different thing and stays allowed.
 */
internal fun org.tuchscherer.database.Post.requirePublished(action: String) {
    if (status != org.tuchscherer.database.PostStatus.PUBLISHED) {
        throw org.tuchscherer.auth.AuthorizationException("Cannot $action a draft post")
    }
}

internal fun decodePostGlobalId(encodedId: String): java.util.UUID {
    val decoded = runCatching { String(java.util.Base64.getDecoder().decode(encodedId)) }
        .getOrElse { throw IllegalArgumentException("Invalid post ID: $encodedId") }
    val colonIdx = decoded.indexOf(String.format(":"))
    require(colonIdx > 0) { "Invalid post ID format: $encodedId" }
    val internalId = decoded.substring(colonIdx + 1)
    return runCatching { java.util.UUID.fromString(internalId) }
        .getOrElse { throw IllegalArgumentException("Invalid UUID in post ID: $internalId") }
}

internal fun org.tuchscherer.database.User.toViaductUser(ctx: ExecutionContext) =
    ViaductUser.of(ctx) {
        id(ctx.globalIDFor(ViaductUser.Reflection, id.value.toString()))
        username(username)
        email(email)
        name(name)
        createdAt(createdAt.toString())
    }

internal fun org.tuchscherer.database.Post.toViaductPost(ctx: ResolverExecutionContext<*>): ViaductPost =
    when (postType) {
        PostType.CHECKED_LIST ->
            ctx.nodeRef(ctx.globalIDFor(ViaductCheckedListPost.Reflection, id.value.toString()))
        else -> toViaductBlogPost(ctx)
    }

internal fun org.tuchscherer.database.Post.toViaductBlogPost(ctx: ExecutionContext) =
    ViaductBlogPost.of(ctx) {
        id(ctx.globalIDFor(ViaductBlogPost.Reflection, id.value.toString()))
        title(title)
        content(content)
        status(ViaductPostStatus.valueOf(status))
        publishedAt(publishedAt?.toString())
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
