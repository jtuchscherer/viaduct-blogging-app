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
    val html = graphiQLHtml(
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
    // Viaduct rc.1 imports a patch absent from its runtime JAR. Our schema already
    // passes buildClientSchema; use the ordinary fetcher until the template is fixed.
    // Its JSX loader also needs Babel, which the template does not load itself.
    html.replace("import { createPatchedFetcher } from '/js/introspection-patch.js';", "")
        .replace("const fetcher = createPatchedFetcher(baseFetcher);", "const fetcher = baseFetcher;")
        // Pin the CDN packages verified together instead of inheriting future template dependency updates.
        .replace("https://esm.sh/graphiql", "https://esm.sh/graphiql@5.4.0")
        .replace("https://esm.sh/@graphiql/react", "https://esm.sh/@graphiql/react@0.39.1")
        .replace("https://esm.sh/@graphiql/toolkit", "https://esm.sh/@graphiql/toolkit@0.12.1")
        .replace("https://esm.sh/@graphiql/plugin-explorer", "https://esm.sh/@graphiql/plugin-explorer@5.1.5")
        .replace(
            "<script type=\"importmap\">",
            """
                <script src="https://cdn.jsdelivr.net/npm/@babel/standalone@7.28.5/babel.min.js"></script>
                <script type="importmap">
            """.trimIndent(),
        )
}
