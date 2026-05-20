package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.ai.AIService
import org.tuchscherer.ai.RephraseTone as ServiceTone
import org.tuchscherer.auth.requireAuth
import org.tuchscherer.viadapp.resolvers.resolverbases.MutationResolvers
import viaduct.api.grts.RephraseTone as ViaductTone
import viaduct.api.grts.RephraseResult
import viaduct.api.resolver.Resolver

@Resolver
class RephraseContentResolver(
    private val aiService: AIService
) : MutationResolvers.RephraseContent() {
    override suspend fun resolve(ctx: Context): RephraseResult {
        requireAuth(ctx.requestContext)

        val content = ctx.arguments.content
        val tone = ctx.arguments.tone

        if (content.isBlank()) {
            throw IllegalArgumentException("Content must not be blank")
        }
        if (content.length > 50_000) {
            throw IllegalArgumentException("Content is too long (max 50,000 characters)")
        }

        val serviceTone = when (tone) {
            null, ViaductTone.PROFESSIONAL -> ServiceTone.PROFESSIONAL
            ViaductTone.CASUAL -> ServiceTone.CASUAL
            ViaductTone.CONCISE -> ServiceTone.CONCISE
        }

        val rephrased = aiService.rephrase(content, serviceTone)

        return RephraseResult.of(ctx) {
            rephrasedContent(rephrased)
        }
    }
}
