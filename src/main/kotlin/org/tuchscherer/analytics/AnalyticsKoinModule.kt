package org.tuchscherer.analytics

import org.tuchscherer.analytics.repositories.ExposedPostViewRepository
import org.tuchscherer.analytics.repositories.PostViewRepository
import org.tuchscherer.viadapp.analytics.resolvers.BlogPostReadTimeMinutesBatchResolver
import org.tuchscherer.viadapp.analytics.resolvers.BlogPostViewCountBatchResolver
import org.tuchscherer.viadapp.analytics.resolvers.CheckedListPostReadTimeMinutesBatchResolver
import org.tuchscherer.viadapp.analytics.resolvers.CheckedListPostViewCountBatchResolver
import org.tuchscherer.viadapp.analytics.resolvers.RecordPostViewMutationResolver
import org.tuchscherer.viadapp.analytics.resolvers.TrendingQueryResolver
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Composes the Java analytics tenant and Kotlin persistence adapter in the application.
 * Lookup ports are registered by `analyticsPortModule` because they depend on core posts.
 */
val analyticsKoinModule = module {
    single<PostViewRepository> { ExposedPostViewRepository() }
    singleOf(::BlogPostViewCountBatchResolver)
    singleOf(::BlogPostReadTimeMinutesBatchResolver)
    singleOf(::CheckedListPostViewCountBatchResolver)
    singleOf(::CheckedListPostReadTimeMinutesBatchResolver)
    singleOf(::TrendingQueryResolver)
    singleOf(::RecordPostViewMutationResolver)
}
