package org.tuchscherer.config

import org.koin.core.Koin
import viaduct.service.api.spi.CodeInjector
import javax.inject.Provider

/** Uses the application's explicit container; providers never consult global state. */
class KoinTenantCodeInjector(private val koin: Koin) : CodeInjector {
    @Suppress("UNCHECKED_CAST")
    override fun <T> getProvider(clazz: Class<T>): Provider<T> = Provider {
        koin.get((clazz as Class<Any>).kotlin) as T
    }
}
