package org.tuchscherer.resolvers

import viaduct.api.testing.ResolverTester
import java.io.File

/**
 * Shared Viaduct tester configuration for resolver unit tests.
 *
 * The new FieldResolverTester / MutationResolverTester APIs require the full schema SDL
 * (BUILTIN_SCHEMA + app schema) because they use MocksKt.createSchema() which does NOT
 * call DefaultSchemaFactory.addDefaults() programmatically. We therefore concatenate:
 *   - build/viaduct/centralSchema/BUILTIN_SCHEMA.graphqls  (built by the Viaduct Gradle plugin)
 *   - src/main/viaduct/schema/schema.graphqls              (app schema)
 */
object ViaductTestConfig {
    val testerConfig: ResolverTester.TesterConfig by lazy {
        val builtinSdl = File("build/viaduct/centralSchema/BUILTIN_SCHEMA.graphqls").readText()
        val appSdl = File("src/main/viaduct/schema/schema.graphqls").readText()
        ResolverTester.TesterConfig(
            schemaSDL = "$builtinSdl\n$appSdl",
            grtPackage = "viaduct.api.grts",
            classLoader = Thread.currentThread().contextClassLoader
        )
    }
}
