package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.requireAuth
import org.tuchscherer.database.repositories.UserRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.QueryResolvers
import org.tuchscherer.viadapp.resolvers.resolverbases.UserResolvers
import viaduct.api.FieldValue
import viaduct.api.Resolver
import viaduct.api.grts.User as ViaductUser
import java.util.UUID

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

@Resolver(objectValueFragment = "fragment _ on User { id }")
class UserIsAdminResolver(
    private val userRepository: UserRepository
) : UserResolvers.IsAdmin() {
    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<Boolean>> {
        val ids = contexts.map { UUID.fromString(it.objectValue.getId().internalID) }
        val usersById = userRepository.findByIds(ids)
        return ids.map { id -> FieldValue.ofValue(usersById[id]?.isAdmin ?: false) }
    }
}
