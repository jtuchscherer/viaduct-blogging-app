package org.tuchscherer.complexity

import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.analysis.MaxQueryDepthInstrumentation
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation
import viaduct.service.ViaductBuilder

/**
 * Caps total query cost at [MAX_COMPLEXITY] and selection-set depth at [MAX_DEPTH].
 * Queries exceeding either limit are aborted by graphql-java before any resolver
 * runs, returning a GraphQL error in the standard `errors` array.
 */
object QueryComplexityInstrumentation {

    const val MAX_COMPLEXITY = 150
    const val MAX_DEPTH = 8

    fun create(calculator: BlogFieldComplexityCalculator): Instrumentation =
        ChainedInstrumentation(
            listOf(
                MaxQueryComplexityInstrumentation(MAX_COMPLEXITY, calculator),
                MaxQueryDepthInstrumentation(MAX_DEPTH),
            )
        )
}

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
