package et.alpha.meret.feature.settings.diagnostics

import et.alpha.meret.domain.diagnostics.DiagnosticsSnapshot

/** The diagnostics screen. One state per screen, per CLAUDE.md. */
sealed interface DiagnosticsUiState {
    /** Before the first snapshot has been read. */
    data object Loading : DiagnosticsUiState

    /**
     * [snapshot] drives the localised screen; [report] is the same facts as the fixed-format
     * English text that "copy diagnostics" puts on the clipboard. [canRecheckPermissions]
     * when the onboarding flow, skips forgotten, would have a step to show.
     */
    data class Ready(
        val snapshot: DiagnosticsSnapshot,
        val report: String,
        val canRecheckPermissions: Boolean = false,
    ) : DiagnosticsUiState
}
