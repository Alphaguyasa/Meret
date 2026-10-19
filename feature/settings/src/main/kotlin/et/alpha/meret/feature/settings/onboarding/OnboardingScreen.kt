package et.alpha.meret.feature.settings.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import et.alpha.meret.core.designsystem.theme.MeretTheme
import et.alpha.meret.core.designsystem.theme.Sizes
import et.alpha.meret.core.designsystem.theme.Spacing
import et.alpha.meret.core.location.compat.OemFamily
import et.alpha.meret.core.location.compat.OemGuidance
import et.alpha.meret.core.location.compat.oemGuidanceFor
import et.alpha.meret.domain.onboarding.OnboardingStep
import et.alpha.meret.domain.onboarding.SkippableStep
import et.alpha.meret.feature.settings.R

/**
 * One permission step per screen: why, one button that does it, and "Not now" where the
 * step can be skipped. Stateless; [OnboardingRoute] owns the ViewModel and the launchers.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onAction: (OnboardingStep) -> Unit,
    onOpenOemScreen: (OemGuidance.DeepLink) -> Unit,
    onSkip: (SkippableStep) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            is OnboardingUiState.Permission -> {
                val copy = onboardingCopyFor(state.step)
                if (copy != null) {
                    StepLayout(
                        title = stringResource(copy.title),
                        action = stringResource(copy.action),
                        onAction = { onAction(state.step) },
                        onSkip = copy.skips?.let { step -> { onSkip(step) } },
                    ) {
                        BodyText(stringResource(copy.body))
                    }
                }
            }

            is OnboardingUiState.OemAutostart -> {
                OemStep(state, onOpenOemScreen, onSkip)
            }

            // Loading lasts one disk read; Done hands over at once. A spinner would only flash.
            OnboardingUiState.Loading, OnboardingUiState.Done -> {
                Box(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun OemStep(
    state: OnboardingUiState.OemAutostart,
    onOpenOemScreen: (OemGuidance.DeepLink) -> Unit,
    onSkip: (SkippableStep) -> Unit,
) {
    val skip = { onSkip(SkippableStep.OEM_AUTOSTART) }
    val manualSteps = state.manualSteps
    val deepLink = state.guidance as? OemGuidance.DeepLink

    if (manualSteps == null && deepLink != null) {
        StepLayout(
            title = stringResource(R.string.onboarding_oem_title),
            action = stringResource(R.string.onboarding_action_open_phone_settings),
            onAction = { onOpenOemScreen(deepLink) },
            onSkip = skip,
        ) {
            BodyText(stringResource(deepLink.instructions))
        }
    } else {
        // No API reports autostart, so the worker's word that it is done is all there is.
        StepLayout(
            title = stringResource(R.string.onboarding_oem_title),
            action = stringResource(R.string.onboarding_action_done),
            onAction = skip,
            onSkip = skip,
        ) {
            BodyText(stringResource(R.string.onboarding_oem_manual_intro))
            manualSteps?.steps.orEmpty().forEachIndexed { index, step ->
                Row(
                    modifier = Modifier.padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    BodyText(stringResource(R.string.onboarding_oem_step_number, index + 1))
                    BodyText(stringResource(step))
                }
            }
        }
    }
}

/**
 * Text scrolls, buttons stay at the bottom within thumb reach. Amharic runs longer than
 * English, and a small phone with large fonts must still show the button.
 */
@Composable
private fun StepLayout(
    title: String,
    action: String,
    onAction: () -> Unit,
    onSkip: (() -> Unit)?,
    body: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.lg),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.md),
            )
            body()
        }
        Button(
            onClick = onAction,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touchTargetField),
        ) {
            Text(action)
        }
        if (onSkip != null) {
            TextButton(
                onClick = onSkip,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs)
                        .heightIn(min = Sizes.touchTargetMin),
            ) {
                Text(stringResource(R.string.onboarding_action_not_now))
            }
        }
    }
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun BackgroundLocationPreview() {
    MeretTheme {
        OnboardingScreen(
            state = OnboardingUiState.Permission(OnboardingStep.BackgroundLocation(true)),
            onAction = {},
            onOpenOemScreen = {},
            onSkip = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun OemManualStepsPreview() {
    val guidance = oemGuidanceFor(OemFamily.TRANSSION) as OemGuidance.DeepLink
    MeretTheme {
        OnboardingScreen(
            state = OnboardingUiState.OemAutostart(guidance, guidance.fallback),
            onAction = {},
            onOpenOemScreen = {},
            onSkip = {},
        )
    }
}
