package et.alpha.meret.core.location.compat

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class OemFamilyTest {
    /** `Build.MANUFACTURER` values as real firmware reports them, unnormalised. */
    @Test
    fun `real manufacturer strings map to their family`() {
        val cases =
            mapOf(
                "TECNO" to OemFamily.TRANSSION,
                "TECNO MOBILE LIMITED" to OemFamily.TRANSSION,
                "INFINIX MOBILITY LIMITED" to OemFamily.TRANSSION,
                "Infinix" to OemFamily.TRANSSION,
                "itel" to OemFamily.TRANSSION,
                "Xiaomi" to OemFamily.XIAOMI,
                "OPPO" to OemFamily.OPPO,
                "realme" to OemFamily.OPPO,
                "vivo" to OemFamily.VIVO,
                "HUAWEI" to OemFamily.HUAWEI,
                "HONOR" to OemFamily.HUAWEI,
                "samsung" to OemFamily.SAMSUNG,
                "OnePlus" to OemFamily.ONEPLUS,
                "Google" to OemFamily.STOCK,
            )

        cases.forEach { (manufacturer, family) ->
            assertWithMessage(manufacturer).that(oemFamilyFor(manufacturer)).isEqualTo(family)
        }
    }

    @Test
    fun `matching ignores case and surrounding whitespace`() {
        assertThat(oemFamilyFor("  tEcNo \n")).isEqualTo(OemFamily.TRANSSION)
        assertThat(oemFamilyFor("\tXIAOMI ")).isEqualTo(OemFamily.XIAOMI)
    }

    @Test
    fun `sub-brands are found through the brand when the manufacturer is unhelpful`() {
        assertThat(oemFamilyFor(manufacturer = "", brand = "Redmi")).isEqualTo(OemFamily.XIAOMI)
        assertThat(oemFamilyFor(manufacturer = "", brand = "POCO")).isEqualTo(OemFamily.XIAOMI)
        assertThat(oemFamilyFor(manufacturer = "unknown", brand = "iQOO")).isEqualTo(OemFamily.VIVO)
    }

    /** OnePlus builds are ColorOS underneath but keep their own settings layout. */
    @Test
    fun `OnePlus is not folded into Oppo`() {
        assertThat(oemFamilyFor("OnePlus", brand = "OnePlus")).isEqualTo(OemFamily.ONEPLUS)
    }

    @Test
    fun `unknown and empty makers fall back to OTHER`() {
        assertThat(oemFamilyFor("motorola")).isEqualTo(OemFamily.OTHER)
        assertThat(oemFamilyFor("")).isEqualTo(OemFamily.OTHER)
        assertThat(oemFamilyFor("   ", brand = " ")).isEqualTo(OemFamily.OTHER)
    }
}
