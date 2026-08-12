package org.tuchscherer.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Guards against misspelled scope names in the GraphQL schema.
 *
 * A scope name that matches no declared scope is accepted silently: `@scope(to: ["publicc"])`
 * compiles, assembles, and produces a type that is in *no* served schema — the field simply
 * vanishes from the API. Verified against Viaduct 2.0: introducing that exact typo left
 * `./gradlew assembleViaductCentralSchema` reporting BUILD SUCCESSFUL.
 *
 * These tests fail fast instead, naming the offending file and scope.
 */
class SchemaScopeVocabularyTest {

    private companion object {
        /** Directories holding hand-written schema. Excludes `build/` so generated copies are skipped. */
        val SCHEMA_ROOTS = listOf("src/main/viaduct/schema", "modules")

        /** Matches one `@scope(to: [...])` application and captures the bracketed list. */
        val SCOPE_DIRECTIVE = Regex("""@scope\(\s*to:\s*\[([^]]*)]\s*\)""")

        /** Matches each quoted name inside that list. */
        val QUOTED_NAME = Regex(""""([^"]*)"""")

        /**
         * Wildcard used by Viaduct's own built-in schema (e.g. `PageInfo`) to mean "every scope".
         * Legal, so it is never reported as undeclared.
         */
        const val WILDCARD = "*"
    }

    /** Every hand-written `.graphqls` file under [SCHEMA_ROOTS], excluding build output. */
    private fun schemaFiles(): List<File> =
        SCHEMA_ROOTS
            .map { File(it) }
            .filter { it.exists() }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "graphqls" } }
            .filterNot { it.path.contains("/build/") || it.path.contains("/bin/") }
            .sortedBy { it.path }

    /** Scope names used in [file], paired with the file for error reporting. */
    private fun scopeNamesIn(file: File): List<Pair<String, File>> =
        SCOPE_DIRECTIVE.findAll(file.readText())
            .flatMap { match -> QUOTED_NAME.findAll(match.groupValues[1]) }
            .map { it.groupValues[1] to file }
            .toList()

    @Test
    fun `every scope name in the schema is declared in SchemaScopes`() {
        val files = schemaFiles()
        assertThat(files)
            .describedAs("no .graphqls files found — check SCHEMA_ROOTS and the test working directory")
            .isNotEmpty()

        val undeclared = files
            .flatMap { scopeNamesIn(it) }
            .filterNot { (name, _) -> name == WILDCARD || name in SchemaScopes.declared }
            .map { (name, file) -> "${file.path}: \"$name\"" }
            .distinct()
            .sorted()

        assertThat(undeclared)
            .describedAs(
                "Undeclared scope names would silently drop these types from every served " +
                    "schema. Declared scopes are ${SchemaScopes.declared.sorted()}. " +
                    "Either fix the typo or add the scope to SchemaScopes.servedSchemas."
            )
            .isEmpty()
    }

    @Test
    fun `the schema actually uses every declared scope`() {
        // A declared scope nothing references is dead configuration, and it also means the
        // check above would not notice if that name were dropped from the vocabulary.
        val used = schemaFiles().flatMap { scopeNamesIn(it) }.map { it.first }.toSet()

        assertThat(SchemaScopes.declared)
            .describedAs("declared scopes not referenced by any @scope directive")
            .allSatisfy { scope -> assertThat(used).contains(scope) }
    }

    @Test
    fun `the admin schema is a superset of the public schema`() {
        // Admin clients should see the whole graph. If admin ever stopped including the public
        // scope, admin-only tooling would lose access to ordinary fields.
        val public = SchemaScopes.servedSchemas.getValue(SchemaScopes.PUBLIC)
        val admin = SchemaScopes.servedSchemas.getValue(SchemaScopes.ADMIN)

        assertThat(admin).containsAll(public)
    }
}
