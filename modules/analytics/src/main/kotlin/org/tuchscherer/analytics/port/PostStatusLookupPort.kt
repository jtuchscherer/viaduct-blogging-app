package org.tuchscherer.analytics.port

import java.util.UUID

/**
 * Port for asking which posts are currently published.
 *
 * Analytics must not count views on a draft or surface one in trending, but the module cannot
 * read the Posts table directly — it is compiled without a dependency on the root project. The
 * root project implements this and registers it via Koin.
 *
 * Separate from [PostTypeLookupPort] because that port answers a different question, the type
 * discriminator, and the two concerns change for different reasons.
 */
interface PostStatusLookupPort {
    /** Of [ids], the subset whose posts are published. Unknown ids are simply absent. */
    fun publishedIds(ids: List<UUID>): Set<UUID>
}
