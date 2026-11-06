package org.tuchscherer.web

import graphql.parser.Parser
import graphql.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import viaduct.engine.SchemaFactory
import java.util.Locale

/**
 * Guards the one GraphiQL regression that can actually reach a user: [BLOGGING_DEFAULT_QUERY]
 * drifting away from the schema. The query is a hand-written string, so renaming or removing a
 * field it selects turns every visit to /graphiql into a validation error.
 *
 * This replaces the browser assertions that used to cover the same ground. Those drove the real
 * GraphiQL UI, which is loaded unpinned from esm.sh, so they broke on upstream cosmetic changes
 * while never checking the query itself. Schema validation needs no browser and no network, and
 * it names the offending field instead of reporting a missing button.
 */
class GraphiQLDefaultQueryTest {
    @Test
    fun `the default GraphiQL query is valid against the served schema`() {
        // The same indexed resources the engine serves, so this sees the merged, scope-filtered schema.
        val schema = SchemaFactory().fromResources().schema
        val document = Parser.parse(BLOGGING_DEFAULT_QUERY)

        val errors = Validator().validateDocument(schema, document, Locale.ROOT)

        assertEquals(emptyList<String>(), errors.map { it.message })
    }
}
