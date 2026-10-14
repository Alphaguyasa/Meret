package et.alpha.meret

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Records identify people and describe their health and farms, so production code never
 * writes to Logcat or stdout. Logger *imports* are forbidden by detekt's `ForbiddenImport`
 * (config/detekt/detekt.yml). `println`, `printStackTrace` and `System.out` need no import,
 * so this test scans every module's main sources for them instead.
 *
 * Diagnostics go through `Telemetry`, whose contract forbids personal data in attributes.
 */
class NoLoggingInMainSourcesTest {
    @Test
    fun `no main source writes to stdout, stderr, or Logcat`() {
        val sources = repoRoot()
            .walkTopDown()
            .onEnter { it.name != "build" && !it.name.startsWith(".") }
            .filter { it.isFile && it.extension == "kt" && it.isMainSource() }
            .toList()

        // Guards against the walk silently finding nothing — a wrong root would pass.
        assertThat(sources.size).isGreaterThan(MIN_EXPECTED_SOURCES)

        val violations = sources.flatMap { file ->
            violationsIn(file.readText()).map { "${file.relativeTo(repoRoot())}:$it" }
        }
        assertThat(violations).isEmpty()
    }

    @Test
    fun `the scanner catches each forbidden call`() {
        val bad = """
            fun leak(name: String, e: Exception) {
                println(name)
                print(name)
                System.out.println(name)
                System.err.println(name)
                e.printStackTrace()
                Log.d("tag", name)
            }
        """.trimIndent()

        assertThat(violationsIn(bad)).hasSize(6)
    }

    @Test
    fun `the scanner ignores comments and look-alike names`() {
        val fine = """
            // println("in a comment")
            /* e.printStackTrace() */
            * KDoc mentioning Log.d(
            fun blueprint(x: Int) = reprint(x) + catalog.size
        """.trimIndent()

        assertThat(violationsIn(fine)).isEmpty()
    }

    private fun violationsIn(source: String): List<String> = source
        .lines()
        .withIndex()
        .filterNot { (_, line) -> line.isComment() }
        .filter { (_, line) -> FORBIDDEN.any { it.containsMatchIn(line) } }
        .map { (index, line) -> "${index + 1}: ${line.trim()}" }

    private fun String.isComment(): Boolean =
        trimStart().let { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }

    private fun File.isMainSource(): Boolean = invariantSeparatorsPath.contains("/src/main/")

    // Gradle runs unit tests with the module directory (app/) as the working directory.
    private fun repoRoot(): File = File("..").canonicalFile

    private companion object {
        const val MIN_EXPECTED_SOURCES = 50

        val FORBIDDEN = listOf(
            Regex("""\bprintln\("""),
            Regex("""(?<![\w.])print\("""),
            Regex("""\bSystem\.(out|err)\b"""),
            Regex("""\.printStackTrace\("""),
            Regex("""\bLog\.[vdiwe]\("""),
            Regex("""\bLog\.wtf\("""),
        )
    }
}
