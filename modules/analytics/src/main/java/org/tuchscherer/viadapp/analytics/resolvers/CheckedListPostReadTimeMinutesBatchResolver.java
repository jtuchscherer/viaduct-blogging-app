package org.tuchscherer.viadapp.analytics.resolvers;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.tuchscherer.analytics.AnalyticsUtils;
import org.tuchscherer.viadapp.analytics.resolverbases.CheckedListPostResolvers;
import viaduct.java.api.annotations.Resolver;
import viaduct.java.grts.CheckedListItem;

/** Reads nested text supplied by the Kotlin checklist tenant through an object fragment. */
@Resolver(objectValueFragment = "fragment _ on CheckedListPost { items { text } }")
public final class CheckedListPostReadTimeMinutesBatchResolver extends CheckedListPostResolvers.ReadTimeMinutes {
    @Override
    public CompletableFuture<Map<Context, Double>> batchResolve(List<Context> contexts) {
        Map<Context, Double> results = new IdentityHashMap<>();
        for (Context ctx : contexts) {
            String text = ctx.getObjectValue().getItemsOrThrow().stream()
                    .map(CheckedListItem::getTextOrThrow)
                    .collect(Collectors.joining(" "));
            results.put(ctx, AnalyticsUtils.estimateReadTime(text));
        }
        return CompletableFuture.completedFuture(results);
    }
}
