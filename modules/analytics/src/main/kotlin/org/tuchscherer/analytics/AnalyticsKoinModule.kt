package org.tuchscherer.analytics

import org.tuchscherer.analytics.repositories.ExposedPostViewRepository
import org.tuchscherer.analytics.repositories.PostViewRepository
import org.tuchscherer.viadapp.analytics.resolvers.BlogPostReadTimeMinutesBatchResolver
import org.tuchscherer.viadapp.analytics.resolvers.BlogPostViewCountBatchResolver
import org.tuchscherer.viadapp.analytics.resolvers.RecordPostViewMutationResolver
import org.tuchscherer.viadapp.analytics.resolvers.TrendingQueryResolver
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Koin module for the analytics tenant.
 *
 * Add `analyticsKoinModule` to `allModules` in the root project's [KoinModules] — that is the
 * only change needed in the root project to wire this module in.
 */
val analyticsKoinModule = module {
    single<PostViewRepository> { ExposedPostViewRepository() }
    singleOf(::BlogPostViewCountBatchResolver)
    singleOf(::BlogPostReadTimeMinutesBatchResolver)
    singleOf(::TrendingQueryResolver)
    singleOf(::RecordPostViewMutationResolver)
}
