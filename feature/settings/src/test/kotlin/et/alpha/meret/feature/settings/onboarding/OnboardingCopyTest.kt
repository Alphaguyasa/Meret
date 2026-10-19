package et.alpha.meret.feature.settings.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.domain.onboarding.OnboardingStep
import et.alpha.meret.domain.onboarding.SkippableStep
import et.alpha.meret.feature.settings.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class OnboardingCopyTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every permission step has copy, and the OEM step and Done have none`() {
        PERMISSION_STEPS.forEach { assertThat(onboardingCopyFor(it)).isNotNull() }
        assertThat(onboardingCopyFor(OnboardingStep.OemAutostart)).isNull()
        assertThat(onboardingCopyFor(OnboardingStep.Done)).isNull()
    }

    /** Without location there is no app, so its screens have no "Not now". */
    @Test
    fun `location cannot be skipped and every other step skips itself`() {
        val skips = PERMISSION_STEPS.associateWith { onboardingCopyFor(it)?.skips }

        assertThat(skips[OnboardingStep.Location(false)]).isNull()
        assertThat(skips[OnboardingStep.Location(true)]).isNull()
        assertThat(skips[OnboardingStep.PreciseLocation(false)]).isNull()
        assertThat(skips[OnboardingStep.PreciseLocation(true)]).isNull()
        assertThat(skips[OnboardingStep.Notifications]).isEqualTo(SkippableStep.NOTIFICATIONS)
        assertThat(skips[OnboardingStep.BackgroundLocation(true)])
            .isEqualTo(SkippableStep.BACKGROUND_LOCATION)
        assertThat(skips[OnboardingStep.BatteryOptimisation])
            .isEqualTo(SkippableStep.BATTERY_OPTIMISATION)
    }

    @Test
    fun `a location step that needs settings says so and opens settings`() {
        listOf(
            OnboardingStep.Location(false) to OnboardingStep.Location(true),
            OnboardingStep.PreciseLocation(false) to OnboardingStep.PreciseLocation(true),
        ).forEach { (dialog, settings) ->
            val viaDialog = checkNotNull(onboardingCopyFor(dialog))
            val viaSettings = checkNotNull(onboardingCopyFor(settings))

            assertThat(viaSettings.body).isNotEqualTo(viaDialog.body)
            assertThat(viaDialog.action).isEqualTo(R.string.onboarding_action_allow)
            assertThat(viaSettings.action).isEqualTo(R.string.onboarding_action_open_settings)
        }
    }

    /** From Android 11 the worker must pick "Allow all the time" by hand; the text says so. */
    @Test
    fun `the background rationale names the settings option only when settings are used`() {
        val dialog = checkNotNull(onboardingCopyFor(OnboardingStep.BackgroundLocation(false)))
        val settings = checkNotNull(onboardingCopyFor(OnboardingStep.BackgroundLocation(true)))

        assertThat(context.getString(settings.body)).contains("Allow all the time")
        assertThat(context.getString(dialog.body)).doesNotContain("Allow all the time")
    }

    /**
     * Lint's MissingTranslation checks that every key exists; this checks that the Amharic
     * value really is Amharic, not an English string copied across.
     */
    @Test
    @Config(qualifiers = "am")
    fun `every onboarding string has an Amharic translation`() {
        val names =
            R.string::class.java.fields
                .map { it.name }
                .filter { it.startsWith("onboarding_") && it != "onboarding_oem_step_number" }
        assertThat(names).isNotEmpty()

        val untranslated =
            names.filterNot { name ->
                val id = R.string::class.java.getField(name).getInt(null)
                ETHIOPIC.containsMatchIn(context.getString(id))
            }

        assertThat(untranslated).isEmpty()
    }

    private companion object {
        val ETHIOPIC = Regex("[\\u1200-\\u137F]")

        val PERMISSION_STEPS =
            listOf(
                OnboardingStep.Location(false),
                OnboardingStep.Location(true),
                OnboardingStep.PreciseLocation(false),
                OnboardingStep.PreciseLocation(true),
                OnboardingStep.Notifications,
                OnboardingStep.BackgroundLocation(false),
                OnboardingStep.BackgroundLocation(true),
                OnboardingStep.BatteryOptimisation,
            )
    }
}
