package org.tuchscherer.analytics.port;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Root-provided visibility lookup shared by public view recording and trending. */
public interface PostStatusLookupPort {
    /** The subset of IDs whose posts currently exist and are published. */
    Set<UUID> publishedIds(List<UUID> ids);
}
