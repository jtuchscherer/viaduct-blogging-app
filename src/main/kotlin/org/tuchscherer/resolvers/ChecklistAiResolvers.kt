package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.ai.AIService
import org.tuchscherer.auth.requireAuth
import org.tuchscherer.viadapp.checkedlist.resolverbases.MutationResolvers
import viaduct.api.grts.SuggestedChecklistItem
import viaduct.api.resolver.Resolver

/** Minimum number of existing items before the AI can suggest a next one. */
private const val MIN_ITEMS_FOR_SUGGESTION = 3

/** Upper bound on items accepted, mirroring the limit enforced when creating a checklist. */
private const val MAX_ITEMS = 100

/** Maximum length of a single item's text, mirroring `validateItemText` in the checkedlist module. */
private const val MAX_ITEM_LENGTH = 1000

/**
 * Suggests the next checklist item via AI, given the texts of the existing items.
 * Requires authentication and between [MIN_ITEMS_FOR_SUGGESTION] and [MAX_ITEMS] items,
 * each non-blank and at most [MAX_ITEM_LENGTH] characters.
 */
@Resolver
class SuggestChecklistItemMutationResolver(
    private val aiService: AIService
) : MutationResolvers.SuggestChecklistItem() {
    override suspend fun resolve(ctx: Context): SuggestedChecklistItem {
        requireAuth(ctx.requestContext)

        val existingItems = ctx.arguments.existingItems
        require(existingItems.size >= MIN_ITEMS_FOR_SUGGESTION) {
            "At least $MIN_ITEMS_FOR_SUGGESTION existing items are required to get a suggestion"
        }
        require(existingItems.size <= MAX_ITEMS) { "A checklist may have at most $MAX_ITEMS items" }
        existingItems.forEach { text ->
            require(text.isNotBlank()) { "Item text must not be blank" }
            require(text.length <= MAX_ITEM_LENGTH) {
                "Item text must be $MAX_ITEM_LENGTH characters or fewer"
            }
        }

        val suggestion = aiService.suggestNextItem(existingItems)

        return SuggestedChecklistItem.of(ctx) {
            suggestedText(suggestion)
        }
    }
}
