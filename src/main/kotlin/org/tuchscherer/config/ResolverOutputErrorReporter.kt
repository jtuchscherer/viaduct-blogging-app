package org.tuchscherer.config

import org.slf4j.LoggerFactory
import viaduct.engine.runtime.observability.ResolverOutputMissingFieldException
import viaduct.service.api.spi.ErrorReporter

/**
 * Reports incomplete generated objects without enabling client-visible missing-field errors.
 *
 * Viaduct invokes this reporter even with its missing-field error flag disabled. Log only
 * schema coordinates: request contexts, error messages, and returned values may contain
 * credentials or unpublished content. Other resolver failures retain their existing handling.
 */
class ResolverOutputErrorReporter : ErrorReporter {
    private val logger = LoggerFactory.getLogger(ResolverOutputErrorReporter::class.java)

    override fun reportResolverError(exception: Throwable, errorMessage: String, metadata: ErrorReporter.Metadata) {
        if (exception is ResolverOutputMissingFieldException) {
            logger.warn("Incomplete resolver output: {}.{}", metadata.parentType, metadata.fieldName)
        }
    }
}
