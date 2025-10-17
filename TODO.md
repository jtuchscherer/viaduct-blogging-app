# TODO List

## For Tomorrow

### Refactor Request Context Structure

**Current Implementation** (`GraphQLServer.kt:60-61`):
```kotlin
val user = token?.let { jwtService.getUserFromToken(it) }

val executionInput = ExecutionInput.create(
    operationText = graphqlRequest.query,
    variables = graphqlRequest.variables ?: emptyMap(),
    requestContext = user  // Currently passing user directly
)
```

**Proposed Change**:
Instead of passing the user object directly as the `requestContext`, create a map structure:

```kotlin
val user = token?.let { jwtService.getUserFromToken(it) }

// Create a map with string keys and object values
val contextMap = mapOf<String, Any?>(
    "authenticatedUser" to user
)

val executionInput = ExecutionInput.create(
    operationText = graphqlRequest.query,
    variables = graphqlRequest.variables ?: emptyMap(),
    requestContext = contextMap  // Pass map instead of user directly
)
```

**Reason**: This provides a more extensible structure for the request context, allowing us to add additional context data in the future (e.g., request metadata, feature flags, tenant info, etc.) without breaking the API.

**Required Changes**:
1. Update `GraphQLServer.kt` to create and pass the context map
2. Update all resolvers to access user via: `(ctx.requestContext as? Map<String, Any?>)?.get("authenticatedUser") as? DatabaseUser`
3. Consider creating a helper function to extract the authenticated user from context
4. Test all authenticated operations still work correctly

**Files to Update**:
- `src/main/kotlin/com/example/web/GraphQLServer.kt` (line ~60)
- `src/main/kotlin/com/example/resolvers/PostMutationResolvers.kt`
- `src/main/kotlin/com/example/resolvers/CommentResolvers.kt`
- `src/main/kotlin/com/example/resolvers/LikeResolvers.kt`
- `src/main/kotlin/com/example/resolvers/PostQueryResolvers.kt`
- `src/main/kotlin/com/example/resolvers/LikeFieldResolvers.kt`
- Any other resolvers that access `ctx.requestContext`

**Optional Enhancement**:
Create a context helper class or extension function:
```kotlin
fun FieldExecutionContext<*, *, *, *>.getAuthenticatedUser(): DatabaseUser? {
    return (requestContext as? Map<String, Any?>)?.get("authenticatedUser") as? DatabaseUser
}
```

This would make resolver code cleaner:
```kotlin
val user = ctx.getAuthenticatedUser()
    ?: throw RuntimeException("Authentication required")
```
