package et.alpha.meret.core.maps.view

import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MapLifecycleForwarderTest {
    private val owner = TestLifecycleOwner()
    private val calls = mutableListOf<String>()
    private val forwarder = MapLifecycleForwarder(RecordingTarget(calls))

    @Test
    fun `a map added to a resumed screen is created, started and resumed`() {
        owner.moveTo(Lifecycle.State.RESUMED)

        owner.lifecycle.addObserver(forwarder)

        assertThat(calls).containsExactly("create", "start", "resume").inOrder()
    }

    @Test
    fun `leaving the screen winds the map down in order`() {
        owner.lifecycle.addObserver(forwarder)
        owner.moveTo(Lifecycle.State.RESUMED)
        calls.clear()

        owner.moveTo(Lifecycle.State.DESTROYED)

        assertThat(calls).containsExactly("pause", "stop", "destroy").inOrder()
    }

    @Test
    fun `stop and restart reuse the same view`() {
        owner.lifecycle.addObserver(forwarder)
        owner.moveTo(Lifecycle.State.RESUMED)
        calls.clear()

        owner.moveTo(Lifecycle.State.CREATED)
        owner.moveTo(Lifecycle.State.RESUMED)

        assertThat(calls).containsExactly("pause", "stop", "start", "resume").inOrder()
    }

    @Test
    fun `disposing while resumed destroys the map exactly once`() {
        owner.lifecycle.addObserver(forwarder)
        owner.moveTo(Lifecycle.State.RESUMED)
        calls.clear()

        forwarder.dispose()
        owner.moveTo(Lifecycle.State.DESTROYED)
        forwarder.dispose()

        assertThat(calls).containsExactly("pause", "stop", "destroy").inOrder()
    }

    @Test
    fun `a map never created is not destroyed`() {
        forwarder.dispose()

        assertThat(calls).isEmpty()
    }

    private class RecordingTarget(
        private val calls: MutableList<String>,
    ) : MapLifecycleTarget {
        override fun onCreate() {
            calls += "create"
        }

        override fun onStart() {
            calls += "start"
        }

        override fun onResume() {
            calls += "resume"
        }

        override fun onPause() {
            calls += "pause"
        }

        override fun onStop() {
            calls += "stop"
        }

        override fun onDestroy() {
            calls += "destroy"
        }
    }
}
