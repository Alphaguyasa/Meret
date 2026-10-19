package et.alpha.meret.feature.settings.onboarding

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import et.alpha.meret.core.location.compat.DeviceCompat
import et.alpha.meret.domain.onboarding.OnboardingStep

/**
 * Owns the [OnboardingViewModel] and everything that needs an Activity: the permission
 * dialogs, the settings pages, and the OEM screen. Calls [onFinished] once nothing is left
 * to ask.
 *
 * Every launch here comes from a button press, never on its own: system settings and OEM
 * screens opened unprompted look like the app misbehaving, and Android 10+ blocks some of
 * them from the background anyway.
 */
@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val deviceCompat = remember(context) { DeviceCompat.create(context) }

    // Fresh snapshot on every resume: the worker may have changed anything in Settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    val locationLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { grants ->
            val fineDenied = grants[Manifest.permission.ACCESS_FINE_LOCATION] != true
            viewModel.onLocationResult(
                permanentlyDenied =
                    fineDenied && !activity.canAsk(Manifest.permission.ACCESS_FINE_LOCATION),
            )
        }
    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onPermissionResult()
        }
    val backgroundLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            // Denied twice, the system stops offering it; only the app's settings page is left.
            if (!granted &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !activity.canAsk(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            ) {
                context.openAppSettings()
            }
            viewModel.onPermissionResult()
        }

    val actions =
        remember(context) {
            StepActions(context, locationLauncher, notificationLauncher, backgroundLauncher)
        }

    LaunchedEffect(state) {
        if (state is OnboardingUiState.Done) onFinished()
    }

    OnboardingScreen(
        state = state,
        onAction = actions::perform,
        onOpenOemScreen = { link -> viewModel.onOemLaunch(deviceCompat.open(link)) },
        onSkip = viewModel::skip,
        modifier = modifier,
    )
}

/** What each permission step's button does. */
private class StepActions(
    private val context: Context,
    private val location: ActivityResultLauncher<Array<String>>,
    private val notifications: ActivityResultLauncher<String>,
    private val background: ActivityResultLauncher<String>,
) {
    fun perform(step: OnboardingStep) {
        when (step) {
            is OnboardingStep.Location -> {
                requestLocation(step.viaSettings)
            }

            is OnboardingStep.PreciseLocation -> {
                requestLocation(step.viaSettings)
            }

            OnboardingStep.Notifications -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // On Android 11+ this request opens the system settings page itself.
            is OnboardingStep.BackgroundLocation -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }

            OnboardingStep.BatteryOptimisation -> {
                context.requestIgnoreBatteryOptimisations()
            }

            OnboardingStep.OemAutostart, OnboardingStep.Done -> {
                Unit
            }
        }
    }

    private fun requestLocation(viaSettings: Boolean) {
        if (viaSettings) context.openAppSettings() else location.launch(LOCATION_PERMISSIONS)
    }
}

private val LOCATION_PERMISSIONS =
    arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

/**
 * False once the system will no longer show the dialog for [permission]. Read right after a
 * denial, when "no rationale" can only mean "don't ask again".
 */
private fun Activity?.canAsk(permission: String): Boolean =
    this?.shouldShowRequestPermissionRationale(permission) ?: true

private fun Context.openAppSettings() {
    startFirst(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri()))
}

/**
 * The direct "stop optimising this app?" dialog, then the full exemption list, then the
 * app's page. Some OEM builds strip the first two.
 */
private fun Context.requestIgnoreBatteryOptimisations() {
    startFirst(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri()),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri()),
    )
}

private fun Context.packageUri(): Uri = Uri.fromParts("package", packageName, null)

private fun Context.startFirst(vararg intents: Intent) {
    for (intent in intents) {
        val started =
            try {
                startActivity(intent)
                true
            } catch (ignored: ActivityNotFoundException) {
                false
            }
        if (started) return
    }
}
