package et.alpha.meret.core.common.geo

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.fixture.TraceFixtures
import et.alpha.meret.core.common.fixture.maxDeviationMetres
import et.alpha.meret.core.model.geo.LatLng
import org.junit.Test

class DouglasPeuckerTest {
    @Test
    fun `a five thousand point trace loses over eighty percent of its points at three metres`() {
        // BUILD_PLAN phase 2 acceptance: >80% reduction at eps=3 m, staying within 3 m.
        val trace =
            TraceFixtures.withGaussianNoise(
                trace = TraceFixtures.windingWalk(pointCount = 5_000, spacingMetres = 1.0),
                sigmaMetres = 0.5,
                seed = 20260806L,
            )

        val simplified = DouglasPeucker.simplify(trace, epsilonMetres = 3.0)

        val reduction = 1.0 - simplified.size.toDouble() / trace.size
        val deviation = maxDeviationMetres(trace, simplified)

        assertThat(trace).hasSize(5_000)
        assertThat(reduction).isGreaterThan(0.80)
        assertThat(deviation).isLessThan(3.0)

        println(
            "Douglas-Peucker: 5000 -> ${simplified.size} points " +
                "(${"%.1f".format(reduction * 100)}% reduction), " +
                "max deviation ${"%.3f".format(deviation)} m",
        )
    }

    @Test
    fun `simplification always keeps the first and last point`() {
        val trace = TraceFixtures.windingWalk(pointCount = 500)

        val simplified = DouglasPeucker.simplify(trace, epsilonMetres = 50.0)

        assertThat(simplified.first()).isEqualTo(trace.first())
        assertThat(simplified.last()).isEqualTo(trace.last())
        assertThat(simplified.size).isAtLeast(2)
    }

    @Test
    fun `simplification preserves the original ordering`() {
        val trace = TraceFixtures.windingWalk(pointCount = 800)

        val simplified = DouglasPeucker.simplify(trace, epsilonMetres = 5.0)

        var searchFrom = 0
        for (point in simplified) {
            val index = trace.subList(searchFrom, trace.size).indexOf(point)
            assertThat(index).isAtLeast(0)
            searchFrom += index + 1
        }
    }

    @Test
    fun `a straight line collapses to its endpoints`() {
        val straight = TraceFixtures.straightWalk(pointCount = 200, spacingMetres = 10.0)

        val simplified = DouglasPeucker.simplify(straight, epsilonMetres = 1.0)

        assertThat(simplified).hasSize(2)
    }

    @Test
    fun `traces of two points or fewer are returned unchanged`() {
        val point = LatLng(9.0, 38.0)

        assertThat(DouglasPeucker.simplify(emptyList(), 3.0)).isEmpty()
        assertThat(DouglasPeucker.simplify(listOf(point), 3.0)).containsExactly(point)
        assertThat(DouglasPeucker.simplify(listOf(point, point), 3.0)).hasSize(2)
    }

    @Test
    fun `a non-positive epsilon is a no-op rather than a crash`() {
        val trace = TraceFixtures.windingWalk(pointCount = 100)

        assertThat(DouglasPeucker.simplify(trace, epsilonMetres = 0.0)).isEqualTo(trace)
        assertThat(DouglasPeucker.simplify(trace, epsilonMetres = -1.0)).isEqualTo(trace)
    }

    @Test
    fun `a run of duplicate points collapses without dividing by zero`() {
        val point = LatLng(9.0, 38.0)
        val duplicates = List(500) { point }

        val simplified = DouglasPeucker.simplify(duplicates, epsilonMetres = 3.0)

        assertThat(simplified).hasSize(2)
        assertThat(simplified.all { it == point }).isTrue()
    }

    @Test
    fun `a spike wider than epsilon survives simplification`() {
        val trace =
            TraceFixtures
                .straightWalk(
                    pointCount = 101,
                    spacingMetres = 10.0,
                ).toMutableList()
        val spike = GeoMath.destinationPoint(trace[50], 40.0, 135.0)
        trace[50] = spike

        val simplified = DouglasPeucker.simplify(trace, epsilonMetres = 3.0)

        assertThat(simplified).contains(spike)
    }

    @Test
    fun `a tighter epsilon never keeps fewer points than a looser one`() {
        val trace = TraceFixtures.windingWalk(pointCount = 2_000)

        val tight = DouglasPeucker.simplify(trace, epsilonMetres = 1.0)
        val loose = DouglasPeucker.simplify(trace, epsilonMetres = 20.0)

        assertThat(tight.size).isAtLeast(loose.size)
        assertThat(maxDeviationMetres(trace, tight)).isAtMost(1.0)
        assertThat(maxDeviationMetres(trace, loose)).isAtMost(20.0)
    }

    @Test
    fun `a fifty thousand point trace simplifies without overflowing the stack`() {
        // The recursion is an explicit stack precisely so this cannot regress: a nearly
        // straight track recurses to depth O(n), and real tracks are this long.
        val trace = TraceFixtures.straightWalk(pointCount = 50_000, spacingMetres = 1.0)

        val simplified = DouglasPeucker.simplify(trace, epsilonMetres = 3.0)

        // A handful of points, not two: over 50 km a constant-bearing course is a rhumb
        // line, which is genuinely curved relative to the tangent-plane projection.
        assertThat(simplified.size).isIn(2..50)
        assertThat(maxDeviationMetres(trace, simplified)).isAtMost(3.0)
    }
}
