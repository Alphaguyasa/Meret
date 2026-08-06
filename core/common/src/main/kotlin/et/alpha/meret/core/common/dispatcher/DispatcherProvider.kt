package et.alpha.meret.core.common.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Injectable dispatchers.
 *
 * Nothing in this codebase references [Dispatchers] directly outside this file. Room,
 * file and geometry work must be provably off the main thread, and "provably" means a
 * test can substitute the dispatcher and assert on it.
 */
public interface DispatcherProvider {
    /** CPU-bound work: geometry, filtering, simplification. */
    public val default: CoroutineDispatcher

    /** Blocking I/O: Room, files, network. */
    public val io: CoroutineDispatcher

    public val main: CoroutineDispatcher

    public val mainImmediate: CoroutineDispatcher
}

/**
 * The production implementation.
 *
 * Every member is a getter rather than a stored property. `Dispatchers.Main` throws when
 * no main dispatcher is installed, and evaluating it in a constructor would make this
 * class unconstructable in a plain JVM unit test that never touches the main thread.
 */
public class StandardDispatcherProvider : DispatcherProvider {
    override val default: CoroutineDispatcher get() = Dispatchers.Default
    override val io: CoroutineDispatcher get() = Dispatchers.IO
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val mainImmediate: CoroutineDispatcher get() = Dispatchers.Main.immediate
}
