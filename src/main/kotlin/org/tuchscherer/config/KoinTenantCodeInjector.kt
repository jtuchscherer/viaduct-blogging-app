package org.tuchscherer.config

import org.koin.core.context.GlobalContext
import viaduct.service.api.spi.CodeInjector
import javax.inject.Provider
import kotlin.reflect.KClass

class KoinTenantCodeInjector : CodeInjector {
    @Suppress("UNCHECKED_CAST")
    override fun <T> getProvider(clazz: Class<T>): Provider<T> {
        return Provider {
            val kClass = (clazz as Class<Any>).kotlin as KClass<Any>
            GlobalContext.get().get(kClass, null) as T
        }
    }

    override fun <T> getProvider(clazz: Class<T>, qualifier: Annotation): Provider<T> =
        getProvider(clazz)
}
