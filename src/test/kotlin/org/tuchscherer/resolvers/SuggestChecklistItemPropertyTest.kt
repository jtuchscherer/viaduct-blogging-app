package org.tuchscherer.resolvers

import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.tuchscherer.ai.NoOpAIService
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.database.User
import org.tuchscherer.viadapp.checkedlist.resolverbases.MutationResolvers
import org.tuchscherer.viadapp.resolvers.SuggestChecklistItemMutationResolver
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import java.util.UUID

/**
 * Property-based coverage for `suggestChecklistItem` input validation.
 *
 * [SuggestChecklistItemResolverTest] pins specific boundary values; these properties assert the
 * *rules* hold across generated inputs, catching gaps a hand-picked list misses — an off-by-one
 * at either bound, or a "blank" that is whitespace rather than empty.
 *
 * Only rejection paths are generated. The success path builds a GRT and needs the Viaduct
 * harness, which does not compose with a property loop; the example test covers it.
 */
class SuggestChecklistItemPropertyTest {

    private lateinit var mockUser: User

    @BeforeEach
    fun setup() {
        mockUser = mockk(relaxed = true)
        every { mockUser.id } returns EntityID(UUID.randomUUID(), mockk())
        every { mockUser.username } returns "testuser"
        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
    }

    /** Authenticated context carrying [items] as the mutation argument. */
    private fun ctxWith(items: List<String>): MutationResolvers.SuggestChecklistItem.Context {
        val ctx = mockk<MutationResolvers.SuggestChecklistItem.Context>(relaxed = true)
        every { ctx.arguments.existingItems } returns items
        every { ctx.requestContext } returns RequestContext(user = mockUser)
        return ctx
    }

    private fun resolve(items: List<String>) = runBlocking {
        SuggestChecklistItemMutationResolver(NoOpAIService()).resolve(ctxWith(items))
    }

    @Test
    fun `any list shorter than 3 items is rejected`() = runBlockingUnit {
        checkAll(Arb.list(validItem, 0..2)) { items ->
            assertThrows<IllegalArgumentException> { resolve(items) }
        }
    }

    @Test
    fun `any list longer than 100 items is rejected`() = runBlockingUnit {
        checkAll(20, Arb.list(validItem, 101..130)) { items ->
            assertThrows<IllegalArgumentException> { resolve(items) }
        }
    }

    @Test
    fun `a blank item anywhere in an otherwise valid list is rejected`() = runBlockingUnit {
        checkAll(Arb.list(validItem, 3..12), blankItem, Arb.int(0..99)) { items, blank, seed ->
            val withBlank = items.toMutableList().also { it[seed % it.size] = blank }
            assertThrows<IllegalArgumentException> { resolve(withBlank) }
        }
    }

    @Test
    fun `an item over 1000 characters anywhere in a valid list is rejected`() = runBlockingUnit {
        checkAll(20, Arb.list(validItem, 3..8), Arb.int(1001..1200), Arb.int(0..99)) { items, len, seed ->
            val withLong = items.toMutableList().also { it[seed % it.size] = "a".repeat(len) }
            assertThrows<IllegalArgumentException> { resolve(withLong) }
        }
    }

    private companion object {
        /** Alphanumeric codepoints, so every generated item is non-blank and within length. */
        val validItem: Arb<String> =
            Arb.string(minSize = 1, maxSize = 20, codepoints = Codepoint.alphanumeric())

        /** Whitespace-only strings, so "blank" covers more than the empty string. */
        val blankItem: Arb<String> = Arb.of("", " ", "   ", "\t", "\n", " \t \n ")
    }
}

/** JUnit requires @Test methods to return void, but `checkAll` returns a PropertyContext. */
private fun runBlockingUnit(block: suspend () -> Unit): Unit = runBlocking { block() }
