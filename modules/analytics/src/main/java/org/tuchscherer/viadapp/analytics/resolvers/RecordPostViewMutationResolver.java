package org.tuchscherer.viadapp.analytics.resolvers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.tuchscherer.analytics.port.PostStatusLookupPort;
import org.tuchscherer.analytics.repositories.PostViewRepository;
import org.tuchscherer.shared.GlobalIdsKt;
import org.tuchscherer.viadapp.analytics.resolverbases.MutationResolvers;
import viaduct.java.api.annotations.Resolver;

/** Anonymous view recording for published posts of either concrete type. */
@Resolver
public final class RecordPostViewMutationResolver extends MutationResolvers.RecordPostView {
    private final PostViewRepository repository;
    private final PostStatusLookupPort statusLookup;

    public RecordPostViewMutationResolver(PostViewRepository repository, PostStatusLookupPort statusLookup) {
        this.repository = repository;
        this.statusLookup = statusLookup;
    }

    @Override
    public CompletableFuture<Boolean> resolve(Context ctx) {
        UUID postId = GlobalIdsKt.decodeGlobalId(ctx.getArguments().getPostId());
        if (!statusLookup.publishedIds(List.of(postId)).contains(postId)) {
            return CompletableFuture.completedFuture(false);
        }
        repository.incrementViewCount(postId);
        return CompletableFuture.completedFuture(true);
    }
}
