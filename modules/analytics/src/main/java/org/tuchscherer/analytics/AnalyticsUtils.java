package org.tuchscherer.analytics;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.tuchscherer.analytics.repositories.PostViewRepository;

/** Shared calculations for both Kotlin-owned post types. */
public final class AnalyticsUtils {
    private static final double WORDS_PER_MINUTE = 200.0;
    private static final double MINIMUM_READ_TIME = 0.5;

    private AnalyticsUtils() {}

    public static double estimateReadTime(String content) {
        String text = trimWhitespace(content);
        int wordCount = text.isEmpty() ? 0 : text.split("\\s+").length;
        return Math.max(MINIMUM_READ_TIME, wordCount / WORDS_PER_MINUTE);
    }

    // Preserve Kotlin String.trim() semantics, including non-breaking Unicode spaces.
    private static String trimWhitespace(String content) {
        int start = 0;
        int end = content.length();
        while (start < end && isWhitespace(content.charAt(start))) start++;
        while (end > start && isWhitespace(content.charAt(end - 1))) end--;
        return content.substring(start, end);
    }

    private static boolean isWhitespace(char character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character);
    }

    /** One bulk lookup, with an independent result for every input including repeated IDs. */
    public static List<Integer> resolveViewCounts(List<UUID> postIds, PostViewRepository repository) {
        Map<UUID, Long> counts = repository.bulkGetViewCounts(postIds);
        return postIds.stream().map(id -> counts.getOrDefault(id, 0L).intValue()).toList();
    }
}
