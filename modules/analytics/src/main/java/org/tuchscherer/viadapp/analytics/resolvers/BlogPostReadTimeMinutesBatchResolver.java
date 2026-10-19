package org.tuchscherer.viadapp.analytics.resolvers;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.tuchscherer.analytics.AnalyticsUtils;
import org.tuchscherer.viadapp.analytics.resolverbases.BlogPostResolvers;
import viaduct.java.api.annotations.Resolver;

/** Estimates read time from the Kotlin post resolver's content dependency. */
@Resolver(objectValueFragment = "fragment _ on BlogPost { content }")
public final class BlogPostReadTimeMinutesBatchResolver extends BlogPostResolvers.ReadTimeMinutes {
    @Override
    public CompletableFuture<Map<Context, Double>> batchResolve(List<Context> contexts) {
        Map<Context, Double> results = new IdentityHashMap<>();
        for (Context ctx : contexts) {
            results.put(ctx, AnalyticsUtils.estimateReadTime(ctx.getObjectValue().getContentOrThrow()));
        }
        return CompletableFuture.completedFuture(results);
    }
}
