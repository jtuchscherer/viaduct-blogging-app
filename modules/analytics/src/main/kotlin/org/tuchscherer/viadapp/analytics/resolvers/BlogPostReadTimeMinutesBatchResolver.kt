package org.tuchscherer.viadapp.analytics.resolvers

import org.tuchscherer.analytics.estimateReadTime
import org.tuchscherer.viadapp.analytics.resolverbases.BlogPostResolvers
import viaduct.api.FieldValue
import viaduct.api.resolver.Resolver

/**
 * Batch resolver for BlogPost.readTimeMinutes.
 *
 * Estimates reading time from the post's raw content using the standard 200 WPM heuristic
 * (see [estimateReadTime]). Pure computation — no database access needed.
 */
@Resolver(objectValueFragment = "fragment _ on BlogPost { content }")
class BlogPostReadTimeMinutesBatchResolver : BlogPostResolvers.ReadTimeMinutes() {

    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<Double>> =
        contexts.map { ctx -> FieldValue.ofValue(estimateReadTime(ctx.getObjectValue().getContent())) }
}
