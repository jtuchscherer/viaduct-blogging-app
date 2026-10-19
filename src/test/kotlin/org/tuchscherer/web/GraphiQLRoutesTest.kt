package org.tuchscherer.web

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GraphiQLRoutesTest {
    @Test
    fun `GraphiQL serves configured HTML and its local assets`() = testApplication {
        application { routing { graphiqlRoutes() } }

        val response = client.get("/graphiql?path=/graphql")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
        val html = response.bodyAsText()
        assertTrue(html.contains("<title>Blogging App</title>"))
        assertTrue(html.contains("viaduct-blogging-app"))
        assertTrue(html.contains("url: '/graphql'"))

        val localAssets = Regex("(?:src=\"|href=\"|from '|loadJSX\\(')(/[^\"']+)")
            .findAll(html).map { it.groupValues[1] }.toSet()
        assertTrue(localAssets.isNotEmpty())
        for (asset in localAssets) {
            val assetResponse = client.get(asset)
            assertEquals(HttpStatusCode.OK, assetResponse.status, asset)
            assertTrue(assetResponse.bodyAsBytes().isNotEmpty(), asset)
            if (asset.endsWith(".js") || asset.endsWith(".jsx")) {
                assertEquals(ContentType.Text.JavaScript, assetResponse.contentType()?.withoutParameters(), asset)
            }
        }
    }

    @Test
    fun `unrelated resources are not exposed as GraphiQL assets`() = testApplication {
        application { routing { graphiqlRoutes() } }

        assertEquals(HttpStatusCode.NotFound, client.get("/js/missing.js").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/js/logback.xml").status)
    }
}
