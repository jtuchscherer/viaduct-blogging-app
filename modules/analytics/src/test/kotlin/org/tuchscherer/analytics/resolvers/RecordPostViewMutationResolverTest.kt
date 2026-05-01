package org.tuchscherer.analytics.resolvers

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.tuchscherer.analytics.repositories.PostViewRepository
import org.tuchscherer.viadapp.analytics.resolvers.RecordPostViewMutationResolver
import org.tuchscherer.viadapp.analytics.resolverbases.MutationResolvers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.engine.SchemaFactory
import viaduct.engine.api.ViaductSchema
import viaduct.engine.runtime.execution.DefaultCoroutineInterop
import viaduct.tenant.testing.DefaultAbstractResolverTestBase
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

    @Test
    fun `returns true after recording the view`() = runBlocking {
        justRun { postViewRepository.incrementViewCount(postId) }

        val resolver = RecordPostViewMutationResolver()
        val ctx = mockk<MutationResolvers.RecordPostView.Context>(relaxed = true)
        every { ctx.arguments.postId } returns
            context.globalIDFor(ViaductBlogPost.Reflection, postId.toString())

        val result = resolver.resolve(ctx)

        assertTrue(result)
    }

    @Test
    fun `increments view count for the correct post ID`() = runBlocking {
        val otherId = UUID.randomUUID()
        // Only postId is stubbed; calling incrementViewCount(otherId) would throw,
        // proving the resolver extracted the correct ID from the argument.
        justRun { postViewRepository.incrementViewCount(postId) }

        val resolver = RecordPostViewMutationResolver()
        val ctx = mockk<MutationResolvers.RecordPostView.Context>(relaxed = true)
        every { ctx.arguments.postId } returns
            context.globalIDFor(ViaductBlogPost.Reflection, postId.toString())

        // If the resolver passes the wrong ID, justRun won't match and MockK will throw
        val result = resolver.resolve(ctx)
        assertTrue(result)
    }
}
