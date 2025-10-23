package com.example.config

import com.example.auth.AuthenticationService
import com.example.auth.JwtService
import com.example.auth.PasswordService
import com.example.database.DatabaseFactory
import com.example.database.repositories.*
import com.example.viadapp.resolvers.*
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
    single { DatabaseFactory(get()) }
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
 * Koin module for server.
 * Provides GraphQLServer instance with all dependencies for both GraphQL and Auth endpoints.
 */
val serverModule = module {
    single { com.example.web.GraphQLServer(get(), get(), get(), get()) }
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

    // Post field resolvers
    singleOf(::PostAuthorResolver)
    singleOf(::PostCommentsFieldResolver)
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
}

/**
 * All application modules combined.
 * Use this list when starting Koin.
 */
val allModules = listOf(
    configModule,
    repositoryModule,
    serviceModule,
    serverModule,
    resolverModule
)
