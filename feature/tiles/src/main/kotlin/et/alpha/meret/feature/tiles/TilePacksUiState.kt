package et.alpha.meret.feature.tiles

/** The packs screen. */
sealed interface TilePacksUiState {
    /** The installed packs have not been read yet. */
    data object Loading : TilePacksUiState

    data class Ready(
        /** Newest first. */
        val packs: List<TilePackItem>,
        /** A picked file is being copied and checked; the import button waits. */
        val importing: Boolean,
        /** The outcome of the last import or delete, until the user dismisses it. */
        val message: PackMessage?,
    ) : TilePacksUiState
}

/** One installed pack, as the list shows it. A blank [name] shows a generic name. */
data class TilePackItem(
    val id: String,
    val name: String,
    val bytes: Long,
)

sealed interface PackMessage {
    /** A blank [name] shows a generic name. */
    data class Imported(
        val name: String,
    ) : PackMessage

    data class ImportFailed(
        val problem: ImportProblem,
    ) : PackMessage

    data object DeleteFailed : PackMessage
}

/** Why an import failed, in terms the user can act on. */
enum class ImportProblem {
    /** Not an MBTiles file, or a raster one: pick another file. */
    NotAPack,

    /** Free some space. */
    OutOfSpace,

    /** The file could not be opened: pick it again. */
    ReadFailed,

    /** The copy failed on this phone: try again. */
    WriteFailed,
}
