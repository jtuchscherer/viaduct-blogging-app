package org.tuchscherer.viadapp.analytics.resolvers;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.tuchscherer.analytics.AnalyticsUtils;
import org.tuchscherer.analytics.repositories.PostViewRepository;
import org.tuchscherer.viadapp.analytics.resolverbases.CheckedListPostResolvers;
import viaduct.java.api.annotations.Resolver;

/** Resolves all requested counts in one repository round-trip. */
@Resolver(objectValueFragment = "fragment _ on CheckedListPost { id }")
public final class CheckedListPostViewCountBatchResolver extends CheckedListPostResolvers.ViewCount {
    private final PostViewRepository repository;

    public CheckedListPostViewCountBatchResolver(PostViewRepository repository) {
        this.repository = repository;
    }

    @Override
    public CompletableFuture<Map<Context, Integer>> batchResolve(List<Context> contexts) {
        List<UUID> ids = contexts.stream()
                .map(ctx -> UUID.fromString(ctx.getObjectValue().getIdOrThrow().getInternalID()))
                .toList();
        List<Integer> counts = AnalyticsUtils.resolveViewCounts(ids, repository);
        Map<Context, Integer> results = new IdentityHashMap<>();
        for (int index = 0; index < contexts.size(); index++) {
            results.put(contexts.get(index), counts.get(index));
        }
        return CompletableFuture.completedFuture(results);
    }
}
