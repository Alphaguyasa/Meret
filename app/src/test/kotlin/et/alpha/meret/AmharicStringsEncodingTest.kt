package et.alpha.meret

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Every `values-am/` file must hold real Ethiopic text, not mojibake.
 *
 * Three modules once shipped Amharic strings that had been saved through a Windows-1252
 * round trip: each Ethiopic letter became three Latin characters ("እ" read as "áŠ¥"). Lint's
 * `MissingTranslation` cannot see this, because every key is still present. An Amharic
 * speaker would have seen nothing but garbage on those screens.
 *
 * The signature is a UTF-8 lead byte decoded as Latin-1 (U+00C2–U+00EF) directly followed by
 * a continuation byte decoded as Windows-1252. Real Amharic text never contains that pair.
 */
class AmharicStringsEncodingTest {
    @Test
    fun `no Amharic resource file is double-encoded`() {
        val files = amharicResourceFiles()

        // Guards against the walk silently finding nothing — a wrong root would pass.
        assertThat(files.size).isAtLeast(MIN_EXPECTED_FILES)

        val violations =
            files.flatMap { file ->
                mojibakeLines(file.readText(Charsets.UTF_8))
                    .map { "${file.relativeTo(repoRoot())}:$it" }
            }
        assertThat(violations).isEmpty()
    }

    @Test
    fun `every Amharic resource file contains Ethiopic script`() {
        val withoutEthiopic =
            amharicResourceFiles()
                .filter { file -> "<string" in file.readText(Charsets.UTF_8) }
                .filterNot { file -> ETHIOPIC.containsMatchIn(file.readText(Charsets.UTF_8)) }
                .map { it.relativeTo(repoRoot()).path }

        assertThat(withoutEthiopic).isEmpty()
    }

    @Test
    fun `the scanner catches mojibake and passes real Amharic`() {
        val broken = """<string name="a">áŠ¥áˆµáŠ«áˆ</string>"""
        val fine = """<string name="a">እስካሁን ምንም መስመር የለም «.mbtiles»</string>"""

        assertThat(mojibakeLines(broken)).hasSize(1)
        assertThat(mojibakeLines(fine)).isEmpty()
    }

    private fun mojibakeLines(text: String): List<String> = text
        .lines()
        .withIndex()
        .filter { (_, line) -> MOJIBAKE.containsMatchIn(line) }
        .map { (index, line) -> "${index + 1}: ${line.trim()}" }

    private fun amharicResourceFiles(): List<File> = repoRoot()
        .walkTopDown()
        .onEnter { it.name != "build" && !it.name.startsWith(".") }
        .filter { it.isFile && it.extension == "xml" && it.parentFile.name == "values-am" }
        .toList()

    // Gradle runs unit tests with the module directory (app/) as the working directory.
    private fun repoRoot(): File = File("..").canonicalFile

    private companion object {
        /** app, core/designsystem, feature/map, feature/tracking, feature/waypoint, feature/tiles. */
        const val MIN_EXPECTED_FILES = 7

        val ETHIOPIC = Regex("[\\u1200-\\u137F]")

        /** A Latin-1 UTF-8 lead byte followed by any Windows-1252 rendering of 0x80–0xBF. */
        val MOJIBAKE =
            Regex(
                "[\\u00C2-\\u00EF]" +
                    "[\\u0080-\\u00BF\\u0152\\u0153\\u0160\\u0161\\u0178\\u017D\\u017E" +
                    "\\u0192\\u02C6\\u02DC\\u2013\\u2014\\u2018-\\u201E\\u2020-\\u2022" +
                    "\\u2026\\u2030\\u2039\\u203A\\u20AC\\u2122]",
            )
    }
}
