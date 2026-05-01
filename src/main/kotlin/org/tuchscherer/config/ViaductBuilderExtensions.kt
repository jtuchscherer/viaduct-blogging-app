package org.tuchscherer.config

import graphql.execution.instrumentation.Instrumentation
import viaduct.service.ViaductBuilder

/**
 * Bridges graphql-java's Instrumentation onto Viaduct's @StableApi builder. ViaductBuilder
 * does not expose withInstrumentation directly, so we delegate to the underlying
 * StandardViaduct.Builder. The @Suppress lives here, at the deprecated call site, instead
 * of leaking into every caller.
 */
fun ViaductBuilder.withInstrumentation(
    instrumentation: Instrumentation,
    chainInstrumentationWithDefaults: Boolean = false,
): ViaductBuilder = apply {
    @Suppress("DEPRECATION")
    builder.withInstrumentation(instrumentation, chainInstrumentationWithDefaults)
}
