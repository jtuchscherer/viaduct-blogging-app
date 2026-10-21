package org.tuchscherer.viadapp.checkedlist.resolvers

import org.tuchscherer.checkedlist.port.ChecklistPostReader
import org.tuchscherer.checkedlist.port.PostSocialPort
import org.tuchscherer.checkedlist.port.CheckedListCurrentUserProvider
import org.tuchscherer.checkedlist.repositories.CheckedListItemRepository
import org.tuchscherer.viadapp.checkedlist.resolverbases.CheckedListPostResolvers
import viaduct.api.FieldValue
import viaduct.api.resolver.Resolver
import viaduct.api.grts.CheckedListItem as ViaductCheckedListItem
import viaduct.api.grts.Comment as ViaductComment
import viaduct.api.grts.Like as ViaductLike
import viaduct.api.grts.User as ViaductUser
import java.util.UUID

/**
 * Parent selection shared by every field resolver in this file: spreads [PostIdFragment], the
 * named fragment declared in `CheckedListFragments.kt`.
 *
 * A `const val` because annotation arguments must be compile-time constants, and spreading a
 * named fragment rather than inlining `{ id }` so the selection is schema-validated at build
 * time instead of at execution time.
 */
private const val POST_ID = "fragment _ on CheckedListPost { ...PostIdFragment }"

/**
 * Resolves [CheckedListPost.items] — the ordered list of checklist items.
 *
 */
@Resolver(objectValueFragment = POST_ID)
class CheckedListPostItemsResolver(
    private val itemRepository: CheckedListItemRepository,
) : CheckedListPostResolvers.Items() {

    override suspend fun resolve(ctx: Context): List<ViaductCheckedListItem> {
        val postId = UUID.fromString(ctx.getObjectValue().getIdOrThrow().internalID)
        return itemRepository.getItemsForPost(postId).map { it.toViaductItem(ctx) }
    }
}

/**
 * Batch resolves [CheckedListPost.author]. Uses [ChecklistPostReader] to get author IDs,
 * then delegates to Viaduct's node cache via [ctx.ref].
 */
@Resolver(objectValueFragment = POST_ID)
class CheckedListPostAuthorBatchResolver(
    private val checklistPosts: ChecklistPostReader,
) : CheckedListPostResolvers.Author() {

    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<ViaductUser>> {
        val postIds = contexts.map { UUID.fromString(it.getObjectValue().getIdOrThrow().internalID) }
        val authorIdByPostId = checklistPosts.getAuthorIdsForPosts(postIds)

        return contexts.zip(postIds).map { (ctx, postId) ->
            val authorId = authorIdByPostId[postId]
                ?: return@map FieldValue.ofError(
                    NoSuchElementException("CheckedListPost not found: $postId")
                )
            FieldValue.ofValue(
                ctx.ref(ctx.globalIDFor(ViaductUser.Reflection, authorId.toString()))
            )
        }
    }
}

/**
 * Resolves [CheckedListPost.comments].
 */
@Resolver(objectValueFragment = POST_ID)
class CheckedListPostCommentsResolver(
    private val socialPort: PostSocialPort,
) : CheckedListPostResolvers.Comments() {

    override suspend fun resolve(ctx: Context): List<ViaductComment> {
        val postId = UUID.fromString(ctx.getObjectValue().getIdOrThrow().internalID)
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
class CheckedListPostCommentCountResolver(
    private val socialPort: PostSocialPort,
) : CheckedListPostResolvers.CommentCount() {

    override suspend fun resolve(ctx: Context): Int {
        val postId = UUID.fromString(ctx.getObjectValue().getIdOrThrow().internalID)
        return socialPort.getCommentCountForPost(postId).toCountInt()
    }
}

/**
 * Resolves [CheckedListPost.likes].
 */
@Resolver(objectValueFragment = POST_ID)
class CheckedListPostLikesResolver(
    private val socialPort: PostSocialPort,
) : CheckedListPostResolvers.Likes() {

    override suspend fun resolve(ctx: Context): List<ViaductLike> {
        val postId = UUID.fromString(ctx.getObjectValue().getIdOrThrow().internalID)
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
class CheckedListPostLikeCountResolver(
    private val socialPort: PostSocialPort,
) : CheckedListPostResolvers.LikeCount() {

    override suspend fun resolve(ctx: Context): Int {
        val postId = UUID.fromString(ctx.getObjectValue().getIdOrThrow().internalID)
        return socialPort.getLikeCountForPost(postId).toCountInt()
    }
}

/**
 * Resolves [CheckedListPost.isLikedByMe]. Returns false for unauthenticated requests.
 */
@Resolver(objectValueFragment = POST_ID)
class CheckedListPostIsLikedByMeResolver(
    private val socialPort: PostSocialPort,
    private val currentUserProvider: CheckedListCurrentUserProvider,
) : CheckedListPostResolvers.IsLikedByMe() {

    override suspend fun resolve(ctx: Context): Boolean {
        val postId = UUID.fromString(ctx.getObjectValue().getIdOrThrow().internalID)
        val userId = runCatching { currentUserProvider.getCurrentUserId(ctx.requestContext) }
            .getOrNull() ?: return false
        return socialPort.isLikedByUser(postId, userId)
    }
}
