package et.alpha.meret.core.common.result

import kotlin.coroutines.cancellation.CancellationException

/**
 * The return type of every fallible operation that crosses a module boundary.
 *
 * Named `AppResult` rather than `Result` — the docs say `Result<T>` — because
 * `kotlin.Result` is in the default import set. A type of ours by that name would be
 * shadowed by an explicit import in some files and not others, and `runCatching { }`
 * would silently produce the wrong one. The cost of the longer name is a few characters;
 * the cost of the collision is a class of bug that compiles.
 *
 * Reads expose `Flow<T>` and do not use this type. Writes return it.
 */
public sealed interface AppResult<out T> {
    public data class Success<out T>(
        public val value: T,
    ) : AppResult<T>

    public data class Failure(
        public val error: AppError,
    ) : AppResult<Nothing>
}

public val AppResult<*>.isSuccess: Boolean
    get() = this is AppResult.Success

public val AppResult<*>.isFailure: Boolean
    get() = this is AppResult.Failure

public fun <T> AppResult<T>.getOrNull(): T? = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> null
}

public fun AppResult<*>.errorOrNull(): AppError? = when (this) {
    is AppResult.Success -> null
    is AppResult.Failure -> error
}

public inline fun <T> AppResult<T>.getOrElse(fallback: (AppError) -> T): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> fallback(error)
}

public inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

public inline fun <T> AppResult<T>.mapError(transform: (AppError) -> AppError): AppResult<T> =
    when (this) {
        is AppResult.Success -> this
        is AppResult.Failure -> AppResult.Failure(transform(error))
    }

public inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> =
    when (this) {
        is AppResult.Success -> transform(value)
        is AppResult.Failure -> this
    }

public inline fun <T, R> AppResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (AppError) -> R,
): R = when (this) {
    is AppResult.Success -> onSuccess(value)
    is AppResult.Failure -> onFailure(error)
}

public inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(value)
    return this
}

public inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}

/**
 * Runs [block], converting a thrown exception into [AppResult.Failure] via [onThrow].
 *
 * [CancellationException] is rethrown rather than captured. Swallowing it would leave a
 * cancelled coroutine believing it had merely failed, which breaks structured concurrency
 * — and this project cancels tracking coroutines on every pause.
 */
public inline fun <T> appCatching(
    onThrow: (Throwable) -> AppError = { AppError.Unexpected(it) },
    block: () -> T,
): AppResult<T> = try {
    AppResult.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    AppResult.Failure(onThrow(throwable))
}
