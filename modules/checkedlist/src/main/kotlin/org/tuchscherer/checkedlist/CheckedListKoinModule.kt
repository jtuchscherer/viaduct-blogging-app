package org.tuchscherer.checkedlist

import org.tuchscherer.checkedlist.repositories.CheckedListItemRepository
import org.tuchscherer.checkedlist.repositories.ExposedCheckedListItemRepository
import org.tuchscherer.viadapp.checkedlist.resolvers.AddCheckedListItemMutationResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.CheckedListItemBatchResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.CheckedListPostAuthorBatchResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.CheckedListPostBatchResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.CheckedListPostCommentCountResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.CheckedListPostCommentsResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.CheckedListPostIsLikedByMeResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.CheckedListPostItemsResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.CheckedListPostLikeCountResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.CheckedListPostLikesResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.CheckedListPostsQueryResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.CreateCheckedListPostMutationResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.DeleteCheckedListItemMutationResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.ToggleCheckedListItemMutationResolver
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Koin module for the checkedlist tenant.
 *
 * The port interfaces ([PostCreationPort], [PostSocialPort], [CheckedListCurrentUserProvider])
 * are registered in the root project's [KoinModules] so the implementations can import
 * root-project types ([CommentRepository], [RequestContext], etc.) without creating a
 * compile-time circular dependency.
 *
 * Add `checkedListKoinModule` to `allModules` in the root project's [KoinModules].
 */
val checkedListKoinModule = module {
    single<CheckedListItemRepository> { ExposedCheckedListItemRepository() }

    singleOf(::CheckedListPostBatchResolver)
    singleOf(::CheckedListItemBatchResolver)
    singleOf(::CheckedListPostItemsResolver)
    singleOf(::CheckedListPostAuthorBatchResolver)
    singleOf(::CheckedListPostCommentsResolver)
    singleOf(::CheckedListPostCommentCountResolver)
    singleOf(::CheckedListPostLikesResolver)
    singleOf(::CheckedListPostLikeCountResolver)
    singleOf(::CheckedListPostIsLikedByMeResolver)
    singleOf(::CheckedListPostsQueryResolver)
    singleOf(::CreateCheckedListPostMutationResolver)
    singleOf(::AddCheckedListItemMutationResolver)
    singleOf(::ToggleCheckedListItemMutationResolver)
    singleOf(::DeleteCheckedListItemMutationResolver)
}
