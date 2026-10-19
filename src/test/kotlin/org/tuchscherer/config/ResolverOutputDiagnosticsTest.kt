package org.tuchscherer.config

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.tuchscherer.viadapp.resolvers.PostsConnectionResolver
import org.tuchscherer.viadapp.resolvers.resolverbases.QueryResolvers
import viaduct.api.grts.PostsConnection
import viaduct.engine.runtime.observability.ResolverOutputMissingFieldException
import viaduct.service.ViaductBuilder
import viaduct.service.api.ExecutionInput
import viaduct.service.api.ExecutionResult
import viaduct.service.api.spi.CodeInjector
import viaduct.service.api.spi.ErrorReporter
import viaduct.service.api.spi.FlagManager
import viaduct.service.api.spi.SharedTenantModuleInjectorFactory
import javax.inject.Provider

/** Exercises the actual engine's distinction between omitted output and an explicit null. */
class ResolverOutputDiagnosticsTest {
    @Test
    fun `omitted nullable output is reported without changing the default response`() = runBlocking<Unit> {
        val reports = mutableListOf<Throwable>()
        val result = execute(reports = reports)

        assertEquals(mapOf("postsConnection" to mapOf("totalCount" to null)), result.getData(), result.toSpecification().toString())
        assertTrue(result.errors.isEmpty(), result.errors.toString())
        assertEquals(1, reports.size)
        assertTrue(reports.single() is ResolverOutputMissingFieldException)
    }

    @Test
    fun `strict test flag exposes incomplete output as a field error`() = runBlocking<Unit> {
        val reports = mutableListOf<Throwable>()
        val result = execute(strict = true, reports = reports)

        assertEquals(mapOf("postsConnection" to mapOf("totalCount" to null)), result.getData(), result.toSpecification().toString())
        assertEquals(1, reports.size)
        val error = result.errors.single()
        assertEquals("VIADUCT_RESOLVER_OUTPUT_MISSING_FIELD", error.extensions["code"])
        assertEquals(listOf("postsConnection", "totalCount"), error.path)
    }

    @Test
    fun `explicit null is valid output even with strict test diagnostics`() = runBlocking<Unit> {
        val reports = mutableListOf<Throwable>()
        val result = execute(strict = true, explicitNull = true, reports = reports)

        assertEquals(mapOf("postsConnection" to mapOf("totalCount" to null)), result.getData(), result.toSpecification().toString())
        assertTrue(result.errors.isEmpty(), result.errors.toString())
        assertTrue(reports.isEmpty())
    }

    @Test
    fun `unselected omitted fields do not produce diagnostics`() = runBlocking<Unit> {
        val reports = mutableListOf<Throwable>()
        val result = execute(strict = true, query = "{ postsConnection { __typename } }", reports = reports)

        assertEquals(mapOf("postsConnection" to mapOf("__typename" to "PostsConnection")), result.getData(), result.toSpecification().toString())
        assertTrue(result.errors.isEmpty(), result.errors.toString())
        assertTrue(reports.isEmpty())
    }

    @Test
    fun `reporter logs field coordinates without request context or error details`() {
        val logger = LoggerFactory.getLogger(ResolverOutputErrorReporter::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            val reporter = ResolverOutputErrorReporter()
            val metadata = ErrorReporter.Metadata(
                fieldName = "totalCount",
                parentType = "PostsConnection",
                requestContext = "private request context",
            )
            reporter.reportResolverError(
                ResolverOutputMissingFieldException("PostsConnection", "totalCount"),
                "private error message",
                metadata,
            )
            reporter.reportResolverError(IllegalArgumentException("private unrelated error"), "private", metadata)

            val event = appender.list.single()
            assertEquals("Incomplete resolver output: PostsConnection.totalCount", event.formattedMessage)
            assertNull(event.throwableProxy)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    private suspend fun execute(
        strict: Boolean = false,
        explicitNull: Boolean = false,
        query: String = "{ postsConnection { totalCount } }",
        reports: MutableList<Throwable>,
    ): ExecutionResult {
        val resolver = spyk(PostsConnectionResolver(mockk()))
        coEvery { resolver.resolve(any()) } answers {
            PostsConnection.Builder(firstArg<QueryResolvers.PostsConnection.Context>()).apply {
                if (explicitNull) totalCount(null)
            }.build()
        }
        val injector = object : CodeInjector {
            override fun <T> getProvider(clazz: Class<T>): Provider<T> = Provider {
                check(clazz == PostsConnectionResolver::class.java) { "Unexpected resolver: ${clazz.name}" }
                clazz.cast(resolver)
            }
        }
        val diagnosticReporter = ResolverOutputErrorReporter()
        val reporter = ErrorReporter { error, message, metadata ->
            reports.add(error)
            diagnosticReporter.reportResolverError(error, message, metadata)
        }
        val flags = object : FlagManager {
            override fun isEnabled(flag: FlagManager.Flag): Boolean =
                flag == FlagManager.Flags.ENABLE_RESOLVER_OUTPUT_MISSING_FIELD_ERRORS || FlagManager.Default.isEnabled(flag)
        }
        return ViaductBuilder()
            .withScopedSchemas(ViaductSchemas.all)
            .withTenantModuleInjectorFactory(SharedTenantModuleInjectorFactory(injector))
            .withResolverErrorReporter(reporter)
            .withFlagManager(if (strict) flags else FlagManager.Default)
            .build()
            .execute(ExecutionInput.create(query), ViaductSchemas.schemaIdFor("public"))
    }
}
