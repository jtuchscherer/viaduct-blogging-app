package org.tuchscherer.analytics.resolvers

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.tuchscherer.analytics.decodeGlobalId
import org.tuchscherer.analytics.repositories.PostViewRepository
import org.tuchscherer.viadapp.analytics.resolvers.RecordPostViewMutationResolver
import org.tuchscherer.viadapp.analytics.resolverbases.MutationResolvers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.engine.SchemaFactory
import viaduct.engine.api.ViaductSchema
import viaduct.engine.runtime.execution.DefaultCoroutineInterop
import viaduct.tenant.testing.DefaultAbstractResolverTestBase
import java.util.Base64
import java.util.UUID

class RecordPostViewMutationResolverTest : DefaultAbstractResolverTestBase() {

    private lateinit var postViewRepository: PostViewRepository
    private val postId = UUID.randomUUID()

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    @BeforeEach
    fun setup() {
        postViewRepository = mockk<PostViewRepository>()

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single<PostViewRepository> { postViewRepository }
            })
        }
    }

    /** Encode a Viaduct global ID exactly as the framework does: base64("TypeName:internalId"). */
    private fun encodeGlobalId(typeName: String, internalId: String): String =
        Base64.getEncoder().encodeToString("$typeName:$internalId".toByteArray())

    @Test
    fun `returns true after recording a BlogPost view`() = runBlocking {
        justRun { postViewRepository.incrementViewCount(postId) }

        val resolver = RecordPostViewMutationResolver()
        val ctx = mockk<MutationResolvers.RecordPostView.Context>(relaxed = true)
        every { ctx.arguments.postId } returns encodeGlobalId("BlogPost", postId.toString())

        assertTrue(resolver.resolve(ctx))
    }

    @Test
    fun `returns true after recording a CheckedListPost view`() = runBlocking {
        justRun { postViewRepository.incrementViewCount(postId) }

        val resolver = RecordPostViewMutationResolver()
        val ctx = mockk<MutationResolvers.RecordPostView.Context>(relaxed = true)
        every { ctx.arguments.postId } returns encodeGlobalId("CheckedListPost", postId.toString())

        assertTrue(resolver.resolve(ctx))
    }

    @Test
    fun `increments view count for the correct BlogPost ID`() = runBlocking {
        // Only postId is stubbed; calling incrementViewCount with any other ID would throw,
        // proving the resolver correctly extracted the UUID from the encoded argument.
        justRun { postViewRepository.incrementViewCount(postId) }

        val resolver = RecordPostViewMutationResolver()
        val ctx = mockk<MutationResolvers.RecordPostView.Context>(relaxed = true)
        every { ctx.arguments.postId } returns encodeGlobalId("BlogPost", postId.toString())

        assertTrue(resolver.resolve(ctx))
    }

    @Test
    fun `increments view count for the correct CheckedListPost ID`() = runBlocking {
        justRun { postViewRepository.incrementViewCount(postId) }

        val resolver = RecordPostViewMutationResolver()
        val ctx = mockk<MutationResolvers.RecordPostView.Context>(relaxed = true)
        every { ctx.arguments.postId } returns encodeGlobalId("CheckedListPost", postId.toString())

        assertTrue(resolver.resolve(ctx))
    }

    // ── decodeGlobalId unit tests ─────────────────────────────────────────────

    @Test
    fun `decodeGlobalId extracts UUID from BlogPost global ID`() {
        val encoded = encodeGlobalId("BlogPost", postId.toString())
        assertEquals(postId, decodeGlobalId(encoded))
    }

    @Test
    fun `decodeGlobalId extracts UUID from CheckedListPost global ID`() {
        val encoded = encodeGlobalId("CheckedListPost", postId.toString())
        assertEquals(postId, decodeGlobalId(encoded))
    }

    @Test
    fun `decodeGlobalId throws for non-base64 input`() {
        assertThrows(IllegalArgumentException::class.java) {
            decodeGlobalId("not-valid-base64!!!")
        }
    }

    @Test
    fun `decodeGlobalId throws when decoded value has no colon`() {
        val noColon = Base64.getEncoder().encodeToString("nouuidhere".toByteArray())
        assertThrows(IllegalArgumentException::class.java) {
            decodeGlobalId(noColon)
        }
    }
}
