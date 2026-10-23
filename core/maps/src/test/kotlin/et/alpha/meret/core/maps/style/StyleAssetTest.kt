package et.alpha.meret.core.maps.style

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/** The build copies `tools/style/meret-style.json` into the module's assets, unchanged. */
@RunWith(RobolectricTestRunner::class)
class StyleAssetTest {
    @Test
    fun `the bundled template is the tools style`() {
        val bundled =
            RuntimeEnvironment
                .getApplication()
                .assets
                .open(StyleProvider.TEMPLATE_ASSET)
                .use { it.readBytes().decodeToString() }
        val styleDir = File(requireNotNull(System.getProperty("meret.styleDir")))
        val source = File(styleDir, "meret-style.json").readText()

        assertThat(bundled).isEqualTo(source)
    }
}
