package et.alpha.meret.core.maps.view

import android.content.Context
import androidx.annotation.MainThread
import org.maplibre.android.MapLibre
import org.maplibre.android.log.Logger
import org.maplibre.android.offline.OfflineManager

/** Process-wide MapLibre setup. `MapView` cannot be built before it. */
internal object MapLibreSetup {
    private var done = false

    @MainThread
    fun ensure(context: Context) {
        if (done) return
        // MapLibre's Logger writes to Logcat (it is @Keep, so native code may call it too),
        // and nothing reaches Logcat from this app (detekt ForbiddenImport, CLAUDE.md).
        Logger.setVerbosity(Logger.NONE)
        MapLibre.getInstance(context.applicationContext)
        // Tiles come from a pack on the phone; caching them again wastes storage. The tile
        // server sends Cache-Control: no-store, but MapLibre's sources never mention it, so
        // the ambient cache is switched off rather than trusted to honour the header. On
        // failure the default 50 MB cache stays, which costs space, not correctness.
        OfflineManager.getInstance(context).setMaximumAmbientCacheSize(0, null)
        done = true
    }
}
