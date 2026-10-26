package et.alpha.meret.feature.settings.onboarding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.datastore.OnboardingStore
import et.alpha.meret.core.location.compat.OemGuidance
import et.alpha.meret.core.location.compat.OemLaunchResult
import et.alpha.meret.core.location.compat.SettingsTarget
import et.alpha.meret.domain.onboarding.OnboardingStep
import et.alpha.meret.domain.onboarding.PermissionSnapshot
import et.alpha.meret.domain.onboarding.SkippableStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The state for each step, and the transitions the screen drives: a fresh snapshot on
 * resume, skips that persist, a permanent location denial, and the OEM screen. The step
 * order per API level is `:domain`'s `OnboardingStepPickerTest`; this only checks that the
 * ViewModel feeds the picker what the device and the worker said.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private val file: File get() = File(temporaryFolder.root, "meret.preferences_pb")

    /** What the device grants. Tests change it, then call `onResume`, as a worker would. */
    private var device =
        PermissionSnapshot(sdkInt = 34, fineLocation = false, coarseLocation = false)

    private val viewModels = mutableListOf<OnboardingViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `no location asks for location`() = runOnboardingTest {
        viewModel().uiState.test {
            awaitState(OnboardingUiState.Permission(OnboardingStep.Location(viaSettings = false)))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a permanent denial sends the worker to settings`() = runOnboardingTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitState(OnboardingUiState.Permission(OnboardingStep.Location(viaSettings = false)))

            viewModel.onLocationResult(permanentlyDenied = true)

            awaitState(OnboardingUiState.Permission(OnboardingStep.Location(viaSettings = true)))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `approximate only asks for precise location`() = runOnboardingTest {
        device = device.copy(coarseLocation = true)

        viewModel().uiState.test {
            awaitState(
                OnboardingUiState.Permission(OnboardingStep.PreciseLocation(viaSettings = false)),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The worker grants location in system settings, then comes back to the app. */
    @Test
    fun `a grant made in settings shows on resume`() = runOnboardingTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitState(OnboardingUiState.Permission(OnboardingStep.Location(viaSettings = false)))

            device = device.copy(fineLocation = true, coarseLocation = true)
            viewModel.onResume()

            awaitState(OnboardingUiState.Permission(OnboardingStep.Notifications))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a dialog result reads the device again`() = runOnboardingTest {
        device = device.copy(fineLocation = true, coarseLocation = true)
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitState(OnboardingUiState.Permission(OnboardingStep.Notifications))

            device = device.copy(notifications = true)
            viewModel.onPermissionResult()

            awaitState(
                OnboardingUiState.Permission(OnboardingStep.BackgroundLocation(viaSettings = true)),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a skipped step moves on at once and is persisted`() = runOnboardingTest {
        device = device.copy(fineLocation = true, coarseLocation = true)
        val store = store()
        val viewModel = viewModel(store)
        viewModel.uiState.test {
            awaitState(OnboardingUiState.Permission(OnboardingStep.Notifications))

            viewModel.skip(SkippableStep.NOTIFICATIONS)

            awaitState(
                OnboardingUiState.Permission(OnboardingStep.BackgroundLocation(viaSettings = true)),
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(store.skippedSteps.first { it.isNotEmpty() }).containsExactly("NOTIFICATIONS")
    }

    /** A later launch must not ask again for what the worker skipped before. */
    @Test
    fun `steps skipped on an earlier launch stay skipped`() = runOnboardingTest {
        device = device.copy(fineLocation = true, coarseLocation = true)
        val store = store()
        store.skip("NOTIFICATIONS")
        store.skip("BACKGROUND_LOCATION")
        store.skip("A_STEP_FROM_ANOTHER_VERSION")

        viewModel(store).uiState.test {
            awaitState(OnboardingUiState.Permission(OnboardingStep.BatteryOptimisation))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the OEM step carries this phone's guidance`() = runOnboardingTest {
        device = allButOem()

        viewModel().uiState.test {
            awaitState(OnboardingUiState.OemAutostart(DEEP_LINK, manualSteps = null))
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** No API reports autostart, so coming back from the OEM screen is the only "done". */
    @Test
    fun `returning from the OEM screen finishes the flow and is persisted`() = runOnboardingTest {
        device = allButOem()
        val store = store()
        val viewModel = viewModel(store)
        viewModel.uiState.test {
            awaitState(OnboardingUiState.OemAutostart(DEEP_LINK, manualSteps = null))

            viewModel.onOemLaunch(OemLaunchResult.Opened(DEEP_LINK.targets.first()))
            viewModel.onResume()

            awaitState(OnboardingUiState.Done)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(store.skippedSteps.first { it.isNotEmpty() }).containsExactly("OEM_AUTOSTART")
    }

    @Test
    fun `when no OEM screen opens the manual steps are shown`() = runOnboardingTest {
        device = allButOem()
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitState(OnboardingUiState.OemAutostart(DEEP_LINK, manualSteps = null))

            viewModel.onOemLaunch(OemLaunchResult.ShowManualSteps(DEEP_LINK.fallback))
            awaitState(OnboardingUiState.OemAutostart(DEEP_LINK, DEEP_LINK.fallback))

            viewModel.skip(SkippableStep.OEM_AUTOSTART)
            awaitState(OnboardingUiState.Done)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a phone with no known OEM screen shows its manual steps straight away`() =
        runOnboardingTest {
            device = allButOem()
            val manual = OemGuidance.ManualSteps(listOf(STEP_A))

            viewModel(guidance = manual).uiState.test {
                awaitState(OnboardingUiState.OemAutostart(manual, manualSteps = manual))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `everything granted is done`() = runOnboardingTest {
        device = allButOem().copy(oemStepRequired = false)

        viewModel().uiState.test {
            awaitState(OnboardingUiState.Done)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun allButOem() = device.copy(
        fineLocation = true,
        coarseLocation = true,
        notifications = true,
        backgroundLocation = true,
        ignoringBatteryOptimisations = true,
        oemStepRequired = true,
    )

    /**
     * The DataStore runs on the test's scheduler, not on its default `Dispatchers.IO` scope,
     * so a write is ordered with the test's virtual clock. On the real IO threads the
     * persisted skip once never reached `first { }` in CI, and `runTest` timed out after
     * 1 min (PR #41).
     */
    private fun TestScope.store(): OnboardingStore = OnboardingStore(
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file }),
    )

    private fun TestScope.viewModel(
        store: OnboardingStore = store(),
        guidance: OemGuidance = DEEP_LINK,
    ) = OnboardingViewModel(
        store,
        { denied -> device.copy(locationPermanentlyDenied = denied) },
        guidance,
    ).also { viewModels += it }

    /**
     * `runTest` that also ends every ViewModel's scope before returning, standing in for
     * `onCleared`. Left running, the store's flow would resume on `Dispatchers.Main` after
     * `resetMain` and fail the next test.
     */
    private fun runOnboardingTest(body: suspend TestScope.() -> Unit) = runTest {
        try {
            body()
        } finally {
            viewModels.forEach {
                it.viewModelScope.coroutineContext.job
                    .cancelAndJoin()
            }
        }
    }

    /** States in between (Loading, a re-read with the same answer) are not the point. */
    private suspend fun ReceiveTurbine<OnboardingUiState>.awaitState(expected: OnboardingUiState) {
        // Reads until the expected state arrives, or Turbine's timeout fails the test.
        while (true) {
            if (awaitItem() == expected) return
        }
    }

    private companion object {
        const val STEP_A = 1
        const val STEP_B = 2

        val DEEP_LINK =
            OemGuidance.DeepLink(
                targets = listOf(SettingsTarget("com.example.phonemaster", "AutoStartActivity")),
                instructions = STEP_A,
                fallback = OemGuidance.ManualSteps(listOf(STEP_A, STEP_B)),
            )
    }
}
