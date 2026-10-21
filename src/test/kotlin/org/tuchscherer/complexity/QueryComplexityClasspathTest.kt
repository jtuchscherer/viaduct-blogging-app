package org.tuchscherer.complexity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class QueryComplexityClasspathTest {
    @Test
    fun `guard works outside the source checkout`(@TempDir directory: Path) {
        val classpath = System.getProperty("java.class.path").split(File.pathSeparator)
            .map { File(it).absolutePath }
            .joinToString(File.pathSeparator)
        val output = directory.resolve("probe.log").toFile()
        val process = ProcessBuilder(
            "${System.getProperty("java.home")}/bin/java",
            "-cp", classpath, ComplexityClasspathProbe::class.java.name,
        ).directory(directory.toFile()).redirectErrorStream(true).redirectOutput(output).start()
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Schema probe timed out")
            assertEquals(0, process.exitValue(), output.readText())
        } finally {
            process.destroyForcibly()
        }
    }
}

/** Runs in a fresh JVM whose working directory has no source or build tree. */
object ComplexityClasspathProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val guard = QueryComplexityGuard(QueryFieldComplexityCalculator())
        check(guard.check("{ posts { id title } }") == null)
        val strict = QueryComplexityGuard(QueryFieldComplexityCalculator(), maxComplexity = 1)
        val query = "{ checkedListPosts { items { text } readTimeMinutes } }"
        check(strict.check(query)?.message?.startsWith("maximum query complexity exceeded") == true)
    }
}
