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

/**
 * The query GraphiQL opens with. Every visitor to /graphiql runs this, so a schema change that
 * leaves it behind greets them with a validation error instead of results. Hoisted out of the
 * config so `GraphiQLDefaultQueryTest` can validate it against the real schema.
 */
internal val BLOGGING_DEFAULT_QUERY = """
    query {
      posts {
        id
        title
        author { username }
        likeCount
      }
    }
""".trimIndent()

private val bloggingGraphiQLHtml by lazy {
    graphiQLHtml(
        GraphiQLHtmlConfig(
            title = "Blogging App",
            defaultQuery = BLOGGING_DEFAULT_QUERY,
            storageKey = "viaduct-blogging-app",
        )
    )
}
