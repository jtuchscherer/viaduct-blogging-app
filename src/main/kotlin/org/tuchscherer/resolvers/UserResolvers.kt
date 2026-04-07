package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.requireAuth
import org.tuchscherer.viadapp.resolvers.resolverbases.QueryResolvers
import org.tuchscherer.viadapp.resolvers.resolverbases.UserResolvers
import viaduct.api.Resolver
import viaduct.api.grts.User as ViaductUser

/**
 * Resolver for the "me" query - returns the currently authenticated user.
 */
@Resolver
class MeResolver : QueryResolvers.Me() {
    override suspend fun resolve(ctx: Context): ViaductUser? {
        val user = requireAuth(ctx.requestContext)

        return ViaductUser.of(ctx) {
            id(ctx.globalIDFor(ViaductUser.Reflection, user.id.value.toString()))
            username(user.username)
            email(user.email)
            name(user.name)
            createdAt(user.createdAt.toString())
        }
    }
}

/**
 * Field resolver for User.isAdmin.
 */
@Resolver(objectValueFragment = "fragment _ on User { id }")
class UserIsAdminResolver : UserResolvers.IsAdmin() {
    override suspend fun resolve(ctx: Context): Boolean {
        // The isAdmin value needs to come from the database User object
        // Since we only have the ID in the object value, we need to look it up
        // However, for efficiency when the User was created from a database User,
        // we can store the isAdmin in a context attribute

        // For now, look up from ID - this can be optimized with batch loading later
        val userId = java.util.UUID.fromString(ctx.objectValue.getId().internalID)
        val userRepository: org.tuchscherer.database.repositories.UserRepository by
            org.koin.java.KoinJavaComponent.inject(org.tuchscherer.database.repositories.UserRepository::class.java)

        return userRepository.findById(userId)?.isAdmin ?: false
    }
}
