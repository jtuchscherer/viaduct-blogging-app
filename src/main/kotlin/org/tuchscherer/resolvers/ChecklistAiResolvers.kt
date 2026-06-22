package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.ai.AIService
import org.tuchscherer.auth.requireAuth
import org.tuchscherer.viadapp.checkedlist.resolverbases.MutationResolvers
import viaduct.api.grts.SuggestedChecklistItem
import viaduct.api.resolver.Resolver

@Resolver
class SuggestChecklistItemMutationResolver(
    private val aiService: AIService
) : MutationResolvers.SuggestChecklistItem() {
    override suspend fun resolve(ctx: Context): SuggestedChecklistItem {
        requireAuth(ctx.requestContext)

        val existingItems = ctx.arguments.existingItems
        require(existingItems.size >= 3) { "At least 3 existing items are required to get a suggestion" }

        val suggestion = aiService.suggestNextItem(existingItems)

        return SuggestedChecklistItem.of(ctx) {
            suggestedText(suggestion)
        }
    }
}
