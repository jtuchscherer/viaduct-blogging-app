package org.tuchscherer.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.tuchscherer.ai.aiKoinModule
import org.tuchscherer.analytics.ViaductPostTypeLookupPort
import org.tuchscherer.analytics.analyticsKoinModule
import org.tuchscherer.analytics.port.PostTypeLookupPort
import org.tuchscherer.auth.AuthenticationService
import org.tuchscherer.checkedlist.ViaductCheckedListCurrentUserProvider
import org.tuchscherer.checkedlist.ViaductPostCreationPort
import org.tuchscherer.checkedlist.ViaductPostSocialAccess
import org.tuchscherer.checkedlist.checkedListKoinModule
import org.tuchscherer.checkedlist.port.CheckedListCurrentUserProvider
import org.tuchscherer.checkedlist.port.PostCreationPort
import org.tuchscherer.checkedlist.port.PostSocialPort
import org.tuchscherer.auth.JwtService
import org.tuchscherer.auth.PasswordService
import org.tuchscherer.complexity.QueryFieldComplexityCalculator
import org.tuchscherer.complexity.GuardedViaduct
import org.tuchscherer.complexity.QueryComplexityGuard
import org.tuchscherer.database.DatabaseFactory
import org.tuchscherer.web.AuthDependencies
import org.tuchscherer.web.GraphQLServer
import org.tuchscherer.web.ObservabilityDependencies
import org.tuchscherer.database.repositories.*
import org.tuchscherer.viadapp.resolvers.*
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import viaduct.service.SchemaScopeInfo
import viaduct.service.ViaductBuilder
import viaduct.service.api.Viaduct
import viaduct.service.api.spi.SharedTenantModuleBootstrapper

/**
 * Koin module for application configuration.
 * Provides AppConfig and its constituent parts (JwtConfig, DatabaseConfig, ServerConfig).
 */
val configModule = module {
    single { AppConfig.load() }
    single { get<AppConfig>().jwt }
    single { get<AppConfig>().database }
    single { get<AppConfig>().server }
    single { get<AppConfig>().ollama }
    single { DatabaseFactory(get(), get()) }
}

/**
 * Koin module for repository implementations.
 * Provides all repository interfaces with their Exposed implementations.
 */
val repositoryModule = module {
    single<UserRepository> { ExposedUserRepository() }
    single<PostRepository> { ExposedPostRepository() }
    single<CommentRepository> { ExposedCommentRepository() }
    single<LikeRepository> { ExposedLikeRepository() }
}

/**
 * Koin module for service layer.
 * Provides authentication and JWT services with their dependencies.
 */
val serviceModule = module {
    single { PasswordService() }
    single { JwtService(get(), get()) }
    single { AuthenticationService(get(), get()) }
}

/**
 * Koin module for metrics.
 * Provides a MeterRegistry for Micrometer instrumentation.
 */
val metricsModule = module {
    single<MeterRegistry> { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }
}

/**
 * Koin module for the Viaduct instance and the pre-execution complexity guard
 * that wraps it. The exposed [Viaduct] is a [GuardedViaduct] that scores incoming
 * queries above Viaduct entirely — no graphql-java types appear in Viaduct's API.
 */
val viaductModule = module {
    singleOf(::QueryFieldComplexityCalculator)
    single { QueryComplexityGuard(get()) }
    single<Viaduct> {
        val underlying = ViaductBuilder()
            .withScopedSchemas(listOf(
                SchemaScopeInfo("public", setOf("public")),
                SchemaScopeInfo("admin", setOf("public", "admin")),
            ))
            .withTenantModuleBootstrapper(SharedTenantModuleBootstrapper(KoinTenantCodeInjector()))
            .withMeterRegistry(get())
            .build()
        GuardedViaduct(underlying, get())
    }
}

/**
 * Koin module for server.
 * Provides GraphQLServer instance with all dependencies for both GraphQL and Auth endpoints.
 */
val serverModule = module {
    singleOf(::AuthDependencies)
    singleOf(::ObservabilityDependencies)
    singleOf(::GraphQLServer)
}

/**
 * Koin module for resolvers.
 * Provides all GraphQL resolver instances with their dependencies.
 */
val resolverModule = module {
    // Post resolvers
    singleOf(::CreatePostResolver)
    singleOf(::UpdatePostResolver)
    singleOf(::DeletePostResolver)
    singleOf(::PostsResolver)
    singleOf(::PostResolver)
    singleOf(::MyPostsResolver)
    singleOf(::PostsConnectionResolver)

    // Post field resolvers
    singleOf(::PostAuthorResolver)
    singleOf(::PostCommentsFieldResolver)
    singleOf(::PostCommentCountResolver)
    singleOf(::PostLikesResolver)
    singleOf(::PostLikeCountResolver)
    singleOf(::PostIsLikedByMeResolver)

    // Comment resolvers
    singleOf(::CreateCommentResolver)
    singleOf(::DeleteCommentResolver)
    singleOf(::PostCommentsResolver)

    // Comment field resolvers
    singleOf(::CommentAuthorResolver)
    singleOf(::CommentPostResolver)

    // Like resolvers
    singleOf(::LikePostMutationResolver)
    singleOf(::UnlikePostResolver)

    // Like field resolvers
    singleOf(::LikeUserResolver)
    singleOf(::LikePostResolver)

    // User resolvers
    singleOf(::MeResolver)
    singleOf(::UserIsAdminResolver)
    singleOf(::UserPostsResolver)

    // Admin query resolvers
    singleOf(::AdminStatsResolver)
    singleOf(::AdminStatsTotalViewsResolver)
    singleOf(::AdminStatsTopPostsResolver)
    singleOf(::AdminUsersResolver)
    singleOf(::AdminUserResolver)
    singleOf(::AdminUserContentCountsResolver)
    singleOf(::AdminPostsResolver)
    singleOf(::AdminPostResolver)
    singleOf(::AdminCommentsResolver)

    // Admin mutation resolvers
    singleOf(::AdminUpdateUserResolver)
    singleOf(::AdminDeleteUserResolver)
    singleOf(::AdminUpdatePostResolver)
    singleOf(::AdminDeletePostResolver)
    singleOf(::AdminDeleteCommentResolver)

    // Node resolvers (Relay refetch via node(id))
    singleOf(::UserNodeResolver)
    singleOf(::BlogPostNodeResolver)
    singleOf(::CommentNodeResolver)
    singleOf(::LikeNodeResolver)

    // AI resolvers
    singleOf(::RephraseContentResolver)
    singleOf(::SuggestChecklistItemMutationResolver)
}

/**
 * Koin module for port implementations bridging checkedlist tenant module to root project.
 * Registered separately so the checkedlist module's own Koin module stays free of root-project
 * compile-time imports.
 */
val checkedListPortModule = module {
    single<PostCreationPort> { ViaductPostCreationPort() }
    single<PostSocialPort> { ViaductPostSocialAccess(get(), get()) }
    single<CheckedListCurrentUserProvider> { ViaductCheckedListCurrentUserProvider() }
}

/**
 * Koin module for port implementations bridging the analytics tenant module to the root project.
 * Provides [PostTypeLookupPort] so the analytics module can resolve post types without a
 * compile-time dependency on the root project's repositories.
 */
val analyticsPortModule = module {
    single<PostTypeLookupPort> { ViaductPostTypeLookupPort(get()) }
}

/**
 * All application modules combined.
 * Use this list when starting Koin.
 */
val allModules = listOf(
    configModule,
    repositoryModule,
    serviceModule,
    metricsModule,
    viaductModule,
    serverModule,
    resolverModule,
    analyticsKoinModule,
    analyticsPortModule,
    checkedListPortModule,
    checkedListKoinModule,
    aiKoinModule,
)
