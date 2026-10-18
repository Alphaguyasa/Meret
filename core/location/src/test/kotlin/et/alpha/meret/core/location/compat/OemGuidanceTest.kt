package et.alpha.meret.core.location.compat

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import et.alpha.meret.core.location.R
import org.junit.Test

class OemGuidanceTest {
    private val aggressiveFamilies =
        OemFamily.entries - OemFamily.STOCK - OemFamily.OTHER

    @Test
    fun `every aggressive family gets a deep link with a manual fallback`() {
        aggressiveFamilies.forEach { family ->
            val guidance = oemGuidanceFor(family)

            assertWithMessage(
                family.name,
            ).that(guidance).isInstanceOf(OemGuidance.DeepLink::class.java)
            guidance as OemGuidance.DeepLink
            assertWithMessage(family.name).that(guidance.targets).isNotEmpty()
            assertWithMessage(family.name).that(guidance.fallback.steps).isNotEmpty()
        }
    }

    @Test
    fun `deep link targets are explicit components`() {
        aggressiveFamilies
            .flatMap { (oemGuidanceFor(it) as OemGuidance.DeepLink).targets }
            .forEach { target ->
                assertThat(target.packageName).isNotEmpty()
                assertThat(target.className).contains(".")
            }
    }

    @Test
    fun `stock Android needs nothing beyond the battery exemption`() {
        assertThat(oemGuidanceFor("Google")).isEqualTo(OemGuidance.NotRequired)
    }

    /** No guessed component names for a maker we have not looked at. */
    @Test
    fun `an unknown maker gets generic manual steps, not a deep link`() {
        assertThat(oemGuidanceFor("motorola"))
            .isEqualTo(OemGuidance.ManualSteps(listOf(R.string.oem_step_generic_battery)))
    }

    /** HiOS and MIUI both honour a locked recents card; Transsion is most of the field fleet. */
    @Test
    fun `Transsion and Xiaomi fallbacks include locking the app in recents`() {
        listOf("TECNO", "Xiaomi").forEach { maker ->
            val link = oemGuidanceFor(maker) as OemGuidance.DeepLink
            assertWithMessage(
                maker,
            ).that(link.fallback.steps).contains(R.string.oem_step_lock_recents)
        }
    }

    @Test
    fun `Transsion opens Phone Master's auto-start screen`() {
        val link = oemGuidanceFor("Infinix") as OemGuidance.DeepLink

        assertThat(link.targets.first().packageName).isEqualTo("com.transsion.phonemaster")
    }
}
