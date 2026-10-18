package et.alpha.meret.domain.onboarding

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OnboardingStepPickerTest {
    private val picker = OnboardingStepPicker()

    @Test
    fun `nothing granted starts with location on every API level`() {
        listOf(26, 28, 29, 30, 31, 33, 34, 36).forEach { sdk ->
            assertThat(picker.next(snapshot(sdk)))
                .isEqualTo(OnboardingStep.Location(viaSettings = false))
        }
    }

    @Test
    fun `a permanently denied location goes through settings`() {
        val denied = snapshot(34).copy(locationPermanentlyDenied = true)

        assertThat(picker.next(denied)).isEqualTo(OnboardingStep.Location(viaSettings = true))
    }

    /** "Approximate" exists from Android 12. It is 1–3 km, useless for a field boundary. */
    @Test
    fun `coarse only on API 31 and above asks for precise location`() {
        listOf(31, 33, 36).forEach { sdk ->
            val coarseOnly = snapshot(sdk).copy(coarseLocation = true)
            assertThat(picker.next(coarseOnly))
                .isEqualTo(OnboardingStep.PreciseLocation(viaSettings = false))
        }
    }

    @Test
    fun `coarse only below API 31 asks for location again`() {
        val coarseOnly = snapshot(30).copy(coarseLocation = true)

        assertThat(picker.next(coarseOnly)).isEqualTo(OnboardingStep.Location(viaSettings = false))
    }

    @Test
    fun `notifications are asked on API 33 and above only`() {
        assertThat(picker.next(located(33))).isEqualTo(OnboardingStep.Notifications)
        assertThat(picker.next(located(36))).isEqualTo(OnboardingStep.Notifications)
        assertThat(picker.next(located(32))).isNotEqualTo(OnboardingStep.Notifications)
    }

    @Test
    fun `background location is a dialog on API 29 and a settings trip from API 30`() {
        assertThat(picker.next(located(29)))
            .isEqualTo(OnboardingStep.BackgroundLocation(viaSettings = false))
        assertThat(picker.next(located(30)))
            .isEqualTo(OnboardingStep.BackgroundLocation(viaSettings = true))
        assertThat(picker.next(located(34).copy(notifications = true)))
            .isEqualTo(OnboardingStep.BackgroundLocation(viaSettings = true))
    }

    @Test
    fun `below API 29 there is no background step and battery comes next`() {
        assertThat(picker.next(located(26))).isEqualTo(OnboardingStep.BatteryOptimisation)
        assertThat(picker.next(located(28))).isEqualTo(OnboardingStep.BatteryOptimisation)
    }

    @Test
    fun `the full order on API 34`() {
        var state = located(34).copy(oemStepRequired = true)
        val seen = mutableListOf<OnboardingStep>()
        val oemDone = mutableSetOf<SkippableStep>()

        while (true) {
            val step = picker.next(state, oemDone)
            seen += step
            state =
                when (step) {
                    OnboardingStep.Notifications -> {
                        state.copy(notifications = true)
                    }

                    is OnboardingStep.BackgroundLocation -> {
                        state.copy(backgroundLocation = true)
                    }

                    OnboardingStep.BatteryOptimisation -> {
                        state.copy(ignoringBatteryOptimisations = true)
                    }

                    OnboardingStep.OemAutostart -> {
                        state.also {
                            oemDone +=
                                SkippableStep.OEM_AUTOSTART
                        }
                    }

                    else -> {
                        break
                    }
                }
        }

        assertThat(seen)
            .containsExactly(
                OnboardingStep.Notifications,
                OnboardingStep.BackgroundLocation(viaSettings = true),
                OnboardingStep.BatteryOptimisation,
                OnboardingStep.OemAutostart,
                OnboardingStep.Done,
            ).inOrder()
    }

    @Test
    fun `skipped steps are not asked again`() {
        val skipped =
            setOf(
                SkippableStep.NOTIFICATIONS,
                SkippableStep.BACKGROUND_LOCATION,
                SkippableStep.BATTERY_OPTIMISATION,
                SkippableStep.OEM_AUTOSTART,
            )

        val step = picker.next(located(34).copy(oemStepRequired = true), skipped)

        assertThat(step).isEqualTo(OnboardingStep.Done)
    }

    /** Skipping never gets past location: without it there is no app. */
    @Test
    fun `location cannot be skipped`() {
        val step = picker.next(snapshot(34), SkippableStep.entries.toSet())

        assertThat(step).isEqualTo(OnboardingStep.Location(viaSettings = false))
    }

    @Test
    fun `stock Android with everything granted is done`() {
        val all =
            located(36).copy(
                notifications = true,
                backgroundLocation = true,
                ignoringBatteryOptimisations = true,
            )

        assertThat(picker.next(all)).isEqualTo(OnboardingStep.Done)
    }

    /** A grant revoked in system settings brings its step back on the next resume. */
    @Test
    fun `a revoked permission is asked again`() {
        val revoked =
            located(34).copy(
                notifications = true,
                backgroundLocation = false,
                ignoringBatteryOptimisations = true,
            )

        assertThat(
            picker.next(revoked),
        ).isEqualTo(OnboardingStep.BackgroundLocation(viaSettings = true))
    }

    private fun snapshot(sdk: Int) =
        PermissionSnapshot(sdkInt = sdk, fineLocation = false, coarseLocation = false)

    private fun located(sdk: Int) = snapshot(sdk).copy(fineLocation = true, coarseLocation = true)
}
