package et.alpha.meret.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackNamesTest {
    @Test
    fun `a typed name is kept as typed`() {
        assertThat(TrackNames.normalise("Kebele 03")).isEqualTo("Kebele 03")
        assertThat(TrackNames.normalise("የአቶ ከበደ እርሻ")).isEqualTo("የአቶ ከበደ እርሻ")
    }

    @Test
    fun `stray spaces and line breaks become single spaces`() {
        assertThat(TrackNames.normalise("  Kebele\n03 \t farm  ")).isEqualTo("Kebele 03 farm")
    }

    @Test
    fun `nothing but whitespace is no name`() {
        assertThat(TrackNames.normalise("")).isNull()
        assertThat(TrackNames.normalise(" \n\t ")).isNull()
    }

    @Test
    fun `a long name is cut to the limit in characters, not bytes`() {
        val ethiopic = "ቀ".repeat(TrackNames.MAX_LENGTH + 10)

        val kept = checkNotNull(TrackNames.normalise(ethiopic))

        assertThat(kept).isEqualTo("ቀ".repeat(TrackNames.MAX_LENGTH))
    }

    @Test
    fun `a cut never splits a character outside the basic plane`() {
        // U+1F33E (sheaf of rice) is two UTF-16 units; the limit falls on it.
        val name = "a".repeat(TrackNames.MAX_LENGTH - 1) + "🌾" + "tail"

        val kept = checkNotNull(TrackNames.normalise(name))

        assertThat(kept.codePointCount(0, kept.length)).isEqualTo(TrackNames.MAX_LENGTH)
        assertThat(kept).endsWith("🌾")
    }

    @Test
    fun `a cut that ends on a space drops the space`() {
        val name = "a".repeat(TrackNames.MAX_LENGTH - 1) + " more"

        assertThat(TrackNames.normalise(name)).isEqualTo("a".repeat(TrackNames.MAX_LENGTH - 1))
    }
}
