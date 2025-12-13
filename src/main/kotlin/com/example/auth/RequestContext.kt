package com.example.auth

import com.example.database.User
import viaduct.api.context.ExecutionContext
import viaduct.api.globalid.GlobalID
import viaduct.api.reflect.Type
import viaduct.api.types.NodeObject

/**
 * Request context containing the authenticated user information.
 * This is passed through the GraphQL execution context to resolvers.
 */
data class RequestContext constructor(
    val user: User? = null,
) : ExecutionContext {

    override fun <T : NodeObject> globalIDFor(
        type: Type<T>,
        internalID: String
    ): GlobalID<T> {
        TODO("Not yet implemented")
    }

    override val requestContext: Any?
        get() = TODO("Not yet implemented")
}
