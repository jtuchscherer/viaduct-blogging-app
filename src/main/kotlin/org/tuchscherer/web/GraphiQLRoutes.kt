package org.tuchscherer.web

import io.ktor.http.ContentType
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import viaduct.service.wiring.graphiql.GraphiQLHtmlConfig
import viaduct.service.wiring.graphiql.graphiQLHtml

internal fun Route.graphiqlRoutes() {
    get("/graphiql") {
        call.respondText(bloggingGraphiQLHtml, ContentType.Text.Html)
    }
    staticResources("/js", "graphiql/js") {
        contentType { ContentType.Text.JavaScript }
    }
    get("/favicon.svg") { call.respondResource("graphiql/favicon.svg") }
    get("/favicon.ico") { call.respondResource("graphiql/favicon.ico") }
}

private val bloggingGraphiQLHtml by lazy {
    graphiQLHtml(
        GraphiQLHtmlConfig(
            title = "Blogging App",
            defaultQuery = """
                query {
                  posts {
                    id
                    title
                    author { username }
                    likeCount
                  }
                }
            """.trimIndent(),
            storageKey = "viaduct-blogging-app",
        )
    )
}
