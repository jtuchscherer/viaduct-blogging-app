package org.tuchscherer.viadapp.checkedlist.resolvers

import org.tuchscherer.checkedlist.port.PostCreationPort
import org.tuchscherer.checkedlist.port.PostSocialPort
import org.tuchscherer.checkedlist.port.CheckedListCurrentUserProvider
import org.tuchscherer.checkedlist.repositories.CheckedListItemRepository
import org.tuchscherer.viadapp.checkedlist.resolverbases.CheckedListPostResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.FieldValue
import viaduct.api.resolver.Resolver
import viaduct.api.grts.CheckedListItem as ViaductCheckedListItem
import viaduct.api.grts.Comment as ViaductComment
import viaduct.api.grts.Like as ViaductLike
import viaduct.api.grts.User as ViaductUser
import java.util.UUID

/**
 * Selection every field resolver in this file needs from its parent post: just the ID.
 *
 * Declared once as a `const val` so the string is not repeated per resolver. Annotation
 * arguments must be compile-time constants, which a top-level `const val` satisfies.
 */
private const val POST_ID = "fragment _ on CheckedListPost { ...PostId }"

/**
 * Resolves [CheckedListPost.items] — the ordered list of checklist items.
 *
 * SPIKE: references the named fragment [PostIdFragment] via a spread instead of inlining
 * the selection, to see whether assembly-time validation resolves cross-references.
 */
@Resolver(objectValueFragment = POST_ID)
class CheckedListPostItemsResolver : CheckedListPostResolvers.Items() {
    private val itemRepository: CheckedListItemRepository by inject(CheckedListItemRepository::class.java)

    override suspend fun resolve(ctx: Context): List<ViaductCheckedListItem> {
        val postId = UUID.fromString(ctx.getObjectValue().getId().internalID)
        return itemRepository.getItemsForPost(postId).map { it.toViaductItem(ctx) }
    }
}

/**
 * Batch resolves [CheckedListPost.author]. Uses [PostCreationPort] to get author IDs,
 * then delegates to Viaduct's node cache via [ctx.nodeRef].
 */
@Resolver(objectValueFragment = POST_ID)
class CheckedListPostAuthorBatchResolver : CheckedListPostResolvers.Author() {
    private val postCreationPort: PostCreationPort by inject(PostCreationPort::class.java)

    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<ViaductUser>> {
        val postIds = contexts.map { UUID.fromString(it.getObjectValue().getId().internalID) }
        val authorIdByPostId = postCreationPort.getAuthorIdsForPosts(postIds)

        return contexts.zip(postIds).map { (ctx, postId) ->
            val authorId = authorIdByPostId[postId]
                ?: return@map FieldValue.ofError(
                    NoSuchElementException("CheckedListPost not found: $postId")
                )
            FieldValue.ofValue(
                ctx.nodeRef(ctx.globalIDFor(ViaductUser.Reflection, authorId.toString()))
            )
        }
    }
}

/**
 * Resolves [CheckedListPost.comments].
 */
@Resolver(objectValueFragment = POST_ID)
class CheckedListPostCommentsResolver : CheckedListPostResolvers.Comments() {
    private val socialPort: PostSocialPort by inject(PostSocialPort::class.java)

    override suspend fun resolve(ctx: Context): List<ViaductComment> {
        val postId = UUID.fromString(ctx.getObjectValue().getId().internalID)
        return socialPort.getCommentsForPost(postId).map { comment ->
            ViaductComment.of(ctx) {
                id(ctx.globalIDFor(ViaductComment.Reflection, comment.id.toString()))
                content(comment.content)
                createdAt(comment.createdAt)
            }
        }
    }
}

/**
 * Resolves [CheckedListPost.commentCount].
 */
@Resolver(objectValueFragment = POST_ID)
class CheckedListPostCommentCountResolver : CheckedListPostResolvers.CommentCount() {
    private val socialPort: PostSocialPort by inject(PostSocialPort::class.java)

    override suspend fun resolve(ctx: Context): Int {
        val postId = UUID.fromString(ctx.getObjectValue().getId().internalID)
        return socialPort.getCommentCountForPost(postId).toCountInt()
    }
}

/**
 * Resolves [CheckedListPost.likes].
 */
@Resolver(objectValueFragment = POST_ID)
class CheckedListPostLikesResolver : CheckedListPostResolvers.Likes() {
    private val socialPort: PostSocialPort by inject(PostSocialPort::class.java)

    override suspend fun resolve(ctx: Context): List<ViaductLike> {
        val postId = UUID.fromString(ctx.getObjectValue().getId().internalID)
        return socialPort.getLikesForPost(postId).map { like ->
            ViaductLike.of(ctx) {
                id(ctx.globalIDFor(ViaductLike.Reflection, like.id.toString()))
                createdAt(like.createdAt)
            }
        }
    }
}

/**
 * Resolves [CheckedListPost.likeCount].
 */
@Resolver(objectValueFragment = POST_ID)
class CheckedListPostLikeCountResolver : CheckedListPostResolvers.LikeCount() {
    private val socialPort: PostSocialPort by inject(PostSocialPort::class.java)

    override suspend fun resolve(ctx: Context): Int {
        val postId = UUID.fromString(ctx.getObjectValue().getId().internalID)
        return socialPort.getLikeCountForPost(postId).toCountInt()
    }
}

/**
 * Resolves [CheckedListPost.isLikedByMe]. Returns false for unauthenticated requests.
 */
@Resolver(objectValueFragment = POST_ID)
class CheckedListPostIsLikedByMeResolver : CheckedListPostResolvers.IsLikedByMe() {
    private val socialPort: PostSocialPort by inject(PostSocialPort::class.java)
    private val currentUserProvider: CheckedListCurrentUserProvider
        by inject(CheckedListCurrentUserProvider::class.java)

    override suspend fun resolve(ctx: Context): Boolean {
        val postId = UUID.fromString(ctx.getObjectValue().getId().internalID)
        val userId = runCatching { currentUserProvider.getCurrentUserId(ctx.requestContext) }
            .getOrNull() ?: return false
        return socialPort.isLikedByUser(postId, userId)
    }
}
