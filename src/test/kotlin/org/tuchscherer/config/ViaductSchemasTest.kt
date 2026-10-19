package org.tuchscherer.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import viaduct.service.SchemaScopeInfo

class ViaductSchemasTest {
    private val views = ViaductSchemas.all
        .filterIsInstance<SchemaScopeInfo.Scoped>()
        .associate { it.id to it.scopesToApply }

    @Test
    fun `generated manifest preserves the public and admin schema contract`() {
        assertThat(views).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                ViaductSchemas.PUBLIC to setOf("public"),
                ViaductSchemas.ADMIN to setOf("public", "admin"),
            )
        )
        assertThat(ViaductSchemas.all).hasSize(views.size)
    }

    @Test
    fun `every declared schema can be routed using its generated id`() {
        ViaductSchemas.all.forEach { view ->
            assertThat(ViaductSchemas.schemaIdFor(view.schemaId.id)).isEqualTo(view.schemaId)
        }
    }

    @Test
    fun `undeclared schema ids are rejected`() {
        assertThrows<NoSuchElementException> {
            ViaductSchemas.schemaIdFor("unknown")
        }
    }
}
