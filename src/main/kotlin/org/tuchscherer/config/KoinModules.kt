package org.tuchscherer.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.tuchscherer.auth.AuthenticationService
import org.tuchscherer.auth.JwtService
import org.tuchscherer.auth.PasswordService
import org.tuchscherer.database.DatabaseFactory
import org.tuchscherer.web.AuthDependencies
import org.tuchscherer.web.GraphQLServer
import org.tuchscherer.web.ObservabilityDependencies
import org.tuchscherer.database.repositories.*
import org.tuchscherer.viadapp.resolvers.*
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import viaduct.service.BasicViaductFactory
import viaduct.service.TenantRegistrationInfo

/**
 * Koin module for application configuration.
 * Provides AppConfig and its constituent parts (JwtConfig, DatabaseConfig, ServerConfig).
 */
val configModule = module {
    single { AppConfig.load() }
    single { get<AppConfig>().jwt }
    single { get<AppConfig>().database }
    single { get<AppConfig>().server }
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

    // Admin query resolvers
    singleOf(::AdminStatsResolver)
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
    singleOf(::PostNodeResolver)
    singleOf(::CommentNodeResolver)
    singleOf(::LikeNodeResolver)
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
    serverModule,
    resolverModule
)
