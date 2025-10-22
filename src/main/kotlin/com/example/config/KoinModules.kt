package com.example.config

import com.example.auth.AuthenticationService
import com.example.auth.JwtService
import com.example.auth.PasswordService
import com.example.database.DatabaseFactory
import com.example.database.repositories.*
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
 * Koin module for servers.
 * Provides GraphQLServer and AuthServer instances.
 */
val serverModule = module {
    single { com.example.web.GraphQLServer(get(), get()) }
    single { com.example.web.AuthServer(get(), get(), get(), get()) }
}

/**
 * All application modules combined.
 * Use this list when starting Koin.
 * Note: Viaduct is not included as it has no injectable dependencies.
 */
val allModules = listOf(
    configModule,
    repositoryModule,
    serviceModule,
    serverModule
)
