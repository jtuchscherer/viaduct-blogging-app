package org.tuchscherer.viadapp.analytics.resolvers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.tuchscherer.analytics.port.PostStatusLookupPort;
import org.tuchscherer.analytics.port.PostTypeLookupPort;
import org.tuchscherer.analytics.port.PostTypeLookupPort.PostKind;
import org.tuchscherer.analytics.repositories.PostViewRepository;
import org.tuchscherer.viadapp.analytics.resolverbases.QueryResolvers;
import viaduct.java.api.annotations.Resolver;
import viaduct.java.grts.BlogPost;
import viaduct.java.grts.CheckedListPost;
import viaduct.java.grts.Post;

/** Returns mixed node references; the Kotlin tenants supply the ordinary post fields. */
@Resolver
public final class TrendingQueryResolver extends QueryResolvers.Trending {
    private static final int DEFAULT_TRENDING_LIMIT = 10;
    private static final int OVER_FETCH_FACTOR = 3;
    private final PostViewRepository repository;
    private final PostTypeLookupPort typeLookup;
    private final PostStatusLookupPort statusLookup;

    public TrendingQueryResolver(
            PostViewRepository repository, PostTypeLookupPort typeLookup, PostStatusLookupPort statusLookup) {
        this.repository = repository;
        this.typeLookup = typeLookup;
        this.statusLookup = statusLookup;
    }

    @Override
    public CompletableFuture<List<Post>> resolve(Context ctx) {
        Integer requestedLimit = ctx.getArguments().getLimit();
        int limit = requestedLimit == null ? DEFAULT_TRENDING_LIMIT : requestedLimit;
        List<UUID> candidates = repository.getMostViewed(limit * OVER_FETCH_FACTOR);
        if (candidates.isEmpty()) return CompletableFuture.completedFuture(List.of());

        Set<UUID> published = statusLookup.publishedIds(candidates);
        List<UUID> ids = candidates.stream().filter(published::contains).limit(limit).toList();
        if (ids.isEmpty()) return CompletableFuture.completedFuture(List.of());

        Map<UUID, PostKind> types = typeLookup.getPostTypes(ids);
        List<Post> posts = new ArrayList<>();
        for (UUID id : ids) {
            PostKind kind = types.get(id);
            if (kind == PostKind.BLOG_POST) {
                posts.add(ctx.ref(ctx.globalIDFor(BlogPost.Reflection, id.toString())));
            } else if (kind == PostKind.CHECKLIST_POST) {
                posts.add(ctx.ref(ctx.globalIDFor(CheckedListPost.Reflection, id.toString())));
            }
        }
        return CompletableFuture.completedFuture(posts);
    }
}
