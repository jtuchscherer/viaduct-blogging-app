package org.tuchscherer.ai

import org.koin.dsl.module

/**
 * Koin module for the AI tenant.
 *
 * Add `aiKoinModule` to `allModules` in the root project's KoinModules — that is the
 * only change needed in the root project to wire this module in.
 *
 * Note: [OllamaConfig] is NOT registered here — it is provided by the root project's
 * `configModule`, because config loading follows the application-level environment
 * strategy (dev/test/prod).
 */
val aiKoinModule = module {
    single<AIService> { OllamaAIService(get()) }
}
