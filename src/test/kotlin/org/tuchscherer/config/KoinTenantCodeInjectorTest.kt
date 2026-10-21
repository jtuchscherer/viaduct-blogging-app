package org.tuchscherer.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class KoinTenantCodeInjectorTest {
    data class Dependency(val name: String)

    @Test
    fun `providers resolve from their own container without starting global Koin`() {
        val first = koinApplication { modules(module { single { Dependency("first") } }) }
        val second = koinApplication { modules(module { single { Dependency("second") } }) }
        try {
            val firstProvider = KoinTenantCodeInjector(first.koin).getProvider(Dependency::class.java)
            val secondProvider = KoinTenantCodeInjector(second.koin).getProvider(Dependency::class.java)
            assertEquals("first", firstProvider.get().name)
            assertEquals("second", secondProvider.get().name)
            assertEquals("first", firstProvider.get().name)
        } finally {
            first.close()
            second.close()
        }
    }
}
