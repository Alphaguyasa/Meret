package et.alpha.meret.core.common.result

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException

/**
 * `AppError` is a closed set on purpose: a `when` over it is exhaustive, so adding a
 * variant is a compile error at every call site that has to handle it. These tests pin
 * the shape of that set and, more usefully, pin the rule that no variant carries a
 * user-facing message.
 */
class AppErrorTest {
    @Test
    fun `a network error records its kind, status code and cause`() {
        val cause = IOException("connection reset")

        val error =
            AppError.Network(
                kind = AppError.Network.Kind.Timeout,
                statusCode = 504,
                cause = cause,
            )

        assertThat(error.kind).isEqualTo(AppError.Network.Kind.Timeout)
        assertThat(error.statusCode).isEqualTo(504)
        assertThat(error.cause).isSameInstanceAs(cause)
    }

    @Test
    fun `a network error is usable with neither a status code nor a cause`() {
        // The normal field condition: no connectivity at all, nothing to report.
        val error = AppError.Network(AppError.Network.Kind.Unreachable)

        assertThat(error.statusCode).isNull()
        assertThat(error.cause).isNull()
    }

    @Test
    fun `every network kind is distinct and reachable`() {
        val kinds = AppError.Network.Kind.entries

        assertThat(kinds).containsExactly(
            AppError.Network.Kind.Unreachable,
            AppError.Network.Kind.Timeout,
            AppError.Network.Kind.Unauthorised,
            AppError.Network.Kind.Forbidden,
            AppError.Network.Kind.NotFound,
            AppError.Network.Kind.RateLimited,
            AppError.Network.Kind.Server,
            AppError.Network.Kind.Malformed,
        )
        assertThat(kinds.map { AppError.Network(it) }.toSet()).hasSize(kinds.size)
    }

    @Test
    fun `every storage kind is distinct and reachable`() {
        val kinds = AppError.Storage.Kind.entries

        assertThat(kinds).containsExactly(
            AppError.Storage.Kind.WriteFailed,
            AppError.Storage.Kind.ReadFailed,
            AppError.Storage.Kind.NotFound,
            AppError.Storage.Kind.OutOfSpace,
            AppError.Storage.Kind.Corrupt,
        )
        assertThat(kinds.map { AppError.Storage(it) }.toSet()).hasSize(kinds.size)
    }

    @Test
    fun `every gps kind is distinct and reachable`() {
        val kinds = AppError.Gps.Kind.entries

        assertThat(kinds).containsExactly(
            AppError.Gps.Kind.Disabled,
            AppError.Gps.Kind.NoFix,
            AppError.Gps.Kind.InsufficientAccuracy,
            AppError.Gps.Kind.MockLocation,
            AppError.Gps.Kind.ProviderUnavailable,
        )
        assertThat(kinds.map { AppError.Gps(it) }.toSet()).hasSize(kinds.size)
    }

    @Test
    fun `a permission error names the permission and whether asking again is pointless`() {
        val transient = AppError.Permission("android.permission.ACCESS_FINE_LOCATION")
        val permanent = transient.copy(permanentlyDenied = true)

        assertThat(transient.permission).isEqualTo("android.permission.ACCESS_FINE_LOCATION")
        assertThat(transient.permanentlyDenied).isFalse()
        assertThat(permanent.permanentlyDenied).isTrue()
        assertThat(transient.cause).isNull()
    }

    @Test
    fun `a conflict error identifies the row the server refused`() {
        val error = AppError.Conflict(entityType = "waypoint", entityId = "0197-abcd")

        assertThat(error.entityType).isEqualTo("waypoint")
        assertThat(error.entityId).isEqualTo("0197-abcd")
        assertThat(error.cause).isNull()
    }

    @Test
    fun `an unexpected error always carries its throwable`() {
        val boom = IllegalStateException("impossible")

        val error = AppError.Unexpected(boom)

        assertThat(error.cause).isSameInstanceAs(boom)
    }

    @Test
    fun `errors are value types so a UI state holding one is comparable`() {
        val cause = IOException("x")

        assertThat(AppError.Network(AppError.Network.Kind.Server, 500))
            .isEqualTo(AppError.Network(AppError.Network.Kind.Server, 500))
        assertThat(AppError.Gps(AppError.Gps.Kind.NoFix))
            .isNotEqualTo(AppError.Gps(AppError.Gps.Kind.Disabled))
        assertThat(AppError.Unexpected(cause)).isEqualTo(AppError.Unexpected(cause))
        assertThat(AppError.Permission("p").hashCode())
            .isEqualTo(AppError.Permission("p").hashCode())
    }
}
