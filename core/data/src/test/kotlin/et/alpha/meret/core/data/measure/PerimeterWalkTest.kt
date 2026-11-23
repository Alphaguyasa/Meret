package et.alpha.meret.core.data.measure

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.location.filter.MotionState
import et.alpha.meret.core.location.filter.ProcessedFix
import et.alpha.meret.core.location.filter.RejectionReason
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.TrackPoint
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.domain.measure.MeasureRingDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.IOException

class PerimeterWalkTest {
    private val draft = FakeDraft()

    @Test
    fun `a fix that becomes a vertex is stored in the draft`() = runBlocking<Unit> {
        val walk = PerimeterWalkFactory(draft).resume()

        val result = walk.offer(processed(START))

        assertThat(result).isEqualTo(AppResult.Success(Unit))
        assertThat(draft.ring.value).containsExactly(START)
    }

    @Test
    fun `a fix that is not a vertex writes nothing`() = runBlocking<Unit> {
        val walk = PerimeterWalkFactory(draft).resume()
        walk.offer(processed(START))
        val writes = draft.writes

        walk.offer(processed(north(1.0)))
        walk.offer(processed(north(METRES_APART), rejection = RejectionReason.IMPLIED_SPEED))

        assertThat(draft.writes).isEqualTo(writes)
        assertThat(draft.ring.value).containsExactly(START)
    }

    /** The process was killed mid-walk: the new walk carries on from the stored corners. */
    @Test
    fun `a resumed walk starts from the draft and keeps the spacing`() = runBlocking<Unit> {
        draft.ring.value = listOf(START, north(METRES_APART))
        val walk = PerimeterWalkFactory(draft).resume()

        walk.offer(processed(north(METRES_APART + 1.0)))
        walk.offer(processed(north(METRES_APART * 2)))

        assertThat(walk.vertices)
            .containsExactly(START, north(METRES_APART), north(METRES_APART * 2))
            .inOrder()
        assertThat(draft.ring.value).isEqualTo(walk.vertices)
    }

    /** A failed write must not lose the vertex: the next write stores it as well. */
    @Test
    fun `a vertex whose write failed is stored by the next write`() = runBlocking<Unit> {
        val walk = PerimeterWalkFactory(draft).resume()
        walk.offer(processed(START))

        draft.failNext = true
        val failed = walk.offer(processed(north(METRES_APART)))
        walk.offer(processed(north(METRES_APART * 2)))

        assertThat(failed).isInstanceOf(AppResult.Failure::class.java)
        assertThat(draft.ring.value)
            .containsExactly(START, north(METRES_APART), north(METRES_APART * 2))
            .inOrder()
    }

    @Test
    fun `undo takes the last vertex back in memory and in the draft`() = runBlocking<Unit> {
        val walk = PerimeterWalkFactory(draft).resume()
        walk.offer(processed(START))
        walk.offer(processed(north(METRES_APART)))

        walk.undo()

        assertThat(walk.vertices).containsExactly(START)
        assertThat(draft.ring.value).containsExactly(START)
    }

    @Test
    fun `undo with nothing walked writes nothing`() = runBlocking<Unit> {
        val walk = PerimeterWalkFactory(draft).resume()

        val result = walk.undo()

        assertThat(result).isEqualTo(AppResult.Success(Unit))
        assertThat(draft.writes).isEqualTo(0)
    }

    @Test
    fun `clear forgets the ring`() = runBlocking<Unit> {
        val walk = PerimeterWalkFactory(draft).resume()
        walk.offer(processed(START))

        walk.clear()

        assertThat(walk.vertices).isEmpty()
        assertThat(draft.ring.value).isEmpty()
    }

    @Test
    fun `the ring drops the vertices that walked back onto the start`() = runBlocking<Unit> {
        val walk = PerimeterWalkFactory(draft).resume()
        listOf(START, north(METRES_APART), north(METRES_APART * 2), north(1.0))
            .forEach { walk.offer(processed(it)) }

        assertThat(walk.ring())
            .containsExactly(START, north(METRES_APART), north(METRES_APART * 2))
            .inOrder()
    }

    private class FakeDraft : MeasureRingDraft {
        val ring = MutableStateFlow<List<LatLng>>(emptyList())
        var writes = 0
        var failNext = false

        override val vertices: Flow<List<LatLng>> = ring

        override suspend fun update(transform: (List<LatLng>) -> List<LatLng>): AppResult<Unit> {
            if (failNext) {
                failNext = false
                return AppResult.Failure(
                    AppError.Storage(AppError.Storage.Kind.WriteFailed, IOException("full")),
                )
            }
            writes++
            ring.value = transform(ring.value)
            return AppResult.Success(Unit)
        }
    }

    private fun processed(
        position: LatLng,
        rejection: RejectionReason? = null,
    ): ProcessedFix = ProcessedFix(
        point =
            TrackPoint(
                id = "p",
                trackId = "t",
                seq = 0,
                position = position,
                accuracyMeters = 4.0,
                provider = LocationProvider.REPLAY,
                recordedAt = 0L,
                elapsedRealtimeNanos = 0L,
                filteredPosition = if (rejection == null) position else null,
                isOutlier = rejection != null,
            ),
        rejectionReason = rejection,
        stepMetres = 0.0,
        totalDistanceMetres = 0.0,
        movingDurationMillis = 0L,
        motionState = MotionState.MOVING,
        startedNewSegment = false,
    )

    private fun north(metres: Double): LatLng =
        LatLng(START.latitude + metres * METRES_TO_DEGREES, START.longitude)

    private companion object {
        val START = LatLng(8.9806, 38.7578)
        const val METRES_APART = 5.0
        const val METRES_TO_DEGREES = 1.0 / 111_320.0
    }
}
