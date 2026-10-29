package et.alpha.meret.feature.tiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.data.tilepack.TilePackRepository
import et.alpha.meret.core.model.TilePack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The installed packs, and importing and deleting them.
 *
 * Opening the screen sweeps pack files that have no row (a crash mid-import leaves one),
 * so their space comes back. One import runs at a time: a second pick while one copies is
 * ignored, and the button is disabled anyway.
 */
@HiltViewModel
class TilePacksViewModel
    @Inject
    constructor(
        private val repository: TilePackRepository,
        private val files: PickedFileSource,
        private val dispatchers: DispatcherProvider,
    ) : ViewModel() {
        private val importing = MutableStateFlow(false)
        private val message = MutableStateFlow<PackMessage?>(null)

        val uiState: StateFlow<TilePacksUiState> =
            combine(repository.observeTilePacks(), importing, message) { packs, busy, shown ->
                TilePacksUiState.Ready(
                    packs = packs.map { it.toItem() },
                    importing = busy,
                    message = shown,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = TilePacksUiState.Loading,
            )

        init {
            viewModelScope.launch { repository.deleteUnregisteredPacks() }
        }

        /** Copies the picked file in; [uri] is the picker's content URI. */
        fun importPack(uri: String) {
            if (!importing.compareAndSet(expect = false, update = true)) return
            message.value = null
            viewModelScope.launch {
                try {
                    val picked = withContext(dispatchers.io) { files.describe(uri) }
                    val result =
                        repository.importPack(picked.displayName, picked.sizeBytes) {
                            files.open(uri)
                        }
                    message.value = result.toMessage()
                } finally {
                    importing.value = false
                }
            }
        }

        fun deletePack(packId: String) {
            viewModelScope.launch {
                if (repository.deletePack(packId) is AppResult.Failure) {
                    message.value = PackMessage.DeleteFailed
                }
            }
        }

        fun dismissMessage() {
            message.value = null
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

private fun AppResult<TilePack>.toMessage(): PackMessage = when (this) {
    is AppResult.Success -> PackMessage.Imported(value.name)
    is AppResult.Failure -> PackMessage.ImportFailed(error.toProblem())
}

private fun TilePack.toItem(): TilePackItem = TilePackItem(id = id, name = name, bytes = bytes)

private fun AppError.toProblem(): ImportProblem = when ((this as? AppError.Storage)?.kind) {
    AppError.Storage.Kind.Corrupt -> ImportProblem.NotAPack
    AppError.Storage.Kind.OutOfSpace -> ImportProblem.OutOfSpace
    AppError.Storage.Kind.ReadFailed, AppError.Storage.Kind.NotFound -> ImportProblem.ReadFailed
    AppError.Storage.Kind.WriteFailed, null -> ImportProblem.WriteFailed
}
