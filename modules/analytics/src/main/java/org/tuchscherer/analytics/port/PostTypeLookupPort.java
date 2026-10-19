package org.tuchscherer.analytics.port;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Root-provided lookup so analytics can return mixed post references without owning posts. */
public interface PostTypeLookupPort {
    enum PostKind { BLOG_POST, CHECKLIST_POST }

    /** Deleted or unknown IDs are absent from the returned map. */
    Map<UUID, PostKind> getPostTypes(List<UUID> ids);
}
