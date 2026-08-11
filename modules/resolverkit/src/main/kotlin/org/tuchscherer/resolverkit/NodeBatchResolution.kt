package org.tuchscherer.resolverkit

import viaduct.api.FieldValue
import java.util.UUID

/**
 * Shared shape for Viaduct `Node` batch resolvers: extract each context's id, look every
 * id up in a single batched call, then either transform the found entity into the GRT type
 * or report a not-found error for that context.
 *
 * Every `NodeResolvers.X.batchResolve` override across the app and its tenant modules
 * (`:modules:checkedlist` among them) does exactly this lookup-transform-or-error shape;
 * this helper exists so those overrides don't each re-derive it by hand. It lives in its
 * own module, rather than as a private helper on one of the resolvers that use it, because
 * the app and `:modules:checkedlist` are siblings — the app depends on the tenant modules,
 * never the other way — so a helper private to one side isn't visible to the other.
 *
 * Callers supply their own not-found exception via [notFound] rather than a fixed type,
 * since each side already has its own domain exception for "no such id" and this module
 * intentionally has no dependency on either.
 *
 * @param contexts the per-field resolver contexts passed to `batchResolve`
 * @param extractId pulls the lookup id out of a context
 * @param findByIds a single batched lookup, keyed by id, backing every context in [contexts]
 * @param transform builds the GRT type from a found entity and its context
 * @param notFound builds the exception to report for an id [findByIds] didn't return
 */
fun <C, E, G> batchNodeResolve(
    contexts: List<C>,
    extractId: (C) -> UUID,
    findByIds: (List<UUID>) -> Map<UUID, E>,
    transform: (E, C) -> G,
    notFound: (UUID) -> Exception,
): Map<C, FieldValue<G>> {
    val ids = contexts.map(extractId)
    val byId = findByIds(ids)
    return contexts.zip(ids).associate { (ctx, id) ->
        ctx to (
            byId[id]?.let { FieldValue.ofValue(transform(it, ctx)) }
                ?: FieldValue.ofError(notFound(id))
        )
    }
}
