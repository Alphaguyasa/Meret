package et.alpha.meret.feature.waypoint

import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.data.tracking.TrackingController
import et.alpha.meret.core.data.tracking.TrackingState
import et.alpha.meret.core.model.Track
import et.alpha.meret.core.model.geo.TrackLine
import et.alpha.meret.domain.diagnostics.TrackingHealth
import kotlinx.coroutines.flow.MutableStateFlow

/** The recording, as the sheet sees it. The sheet never starts or stops one. */
internal class FakeTrackingController : TrackingController {
    override val state = MutableStateFlow<TrackingState>(TrackingState.Idle)
    override val activeTrack = MutableStateFlow<Track?>(null)
    override val activeLine = MutableStateFlow<TrackLine?>(null)
    override val activeSegmentCount = MutableStateFlow<Int?>(null)
    override val health = MutableStateFlow(TrackingHealth())

    override suspend fun start(trackId: String): AppResult<Unit> = error("the service's call")

    override suspend fun reopen(trackId: String): AppResult<Unit> = error("the service's call")

    override suspend fun restore(): AppResult<TrackingState> = error("the service's call")

    override suspend fun interruptedTrackId(): String? = error("the service's call")

    override suspend fun pause(): Unit = error("the service's call")

    override suspend fun resume(): AppResult<Unit> = error("the service's call")

    override suspend fun stop(): Unit = error("the service's call")
}
