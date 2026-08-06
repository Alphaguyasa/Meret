package et.alpha.meret.core.common.result

/**
 * Every way an operation can fail, as a closed set.
 *
 * No member carries a user-facing message. Mapping an error to text is a UI-layer job:
 * the data layer does not know the locale, does not know whether the user is an
 * enumerator or a supervisor, and cannot be reached by the translators. A `String` here
 * would be a string that never gets an Amharic version.
 */
public sealed interface AppError {
    /** The originating throwable, where one exists. Never surfaced to the user. */
    public val cause: Throwable?

    /**
     * The network could not be used, or the server refused.
     *
     * Not a failure state for the app: every write path already succeeded locally before
     * a sync was ever attempted.
     */
    public data class Network(
        public val kind: Kind,
        public val statusCode: Int? = null,
        override val cause: Throwable? = null,
    ) : AppError {
        public enum class Kind {
            /** No usable connectivity. The normal condition in the field. */
            Unreachable,
            Timeout,

            /** Session expired or rejected. The app stays fully usable. */
            Unauthorised,
            Forbidden,
            NotFound,
            RateLimited,
            Server,
            Malformed,
        }
    }

    /** Local persistence failed: Room, DataStore, or the filesystem. */
    public data class Storage(
        public val kind: Kind,
        override val cause: Throwable? = null,
    ) : AppError {
        public enum class Kind {
            /** The write could not be made. This one is genuinely serious. */
            WriteFailed,
            ReadFailed,
            NotFound,
            OutOfSpace,
            Corrupt,
        }
    }

    /** A runtime permission is missing. [permission] is the Android manifest constant. */
    public data class Permission(
        public val permission: String,
        public val permanentlyDenied: Boolean = false,
        override val cause: Throwable? = null,
    ) : AppError

    /** Location acquisition failed for a reason the user may be able to act on. */
    public data class Gps(
        public val kind: Kind,
        override val cause: Throwable? = null,
    ) : AppError {
        public enum class Kind {
            /** Location services are switched off device-wide. */
            Disabled,

            /** No fix within the timeout — indoors, or a cold start under cover. */
            NoFix,

            /** Fixes are arriving but none met the accuracy gate. */
            InsufficientAccuracy,

            /** Mock locations are active and the project disallows them. */
            MockLocation,

            /** The OS killed the provider, typically an OEM battery policy. */
            ProviderUnavailable,
        }
    }

    /**
     * The server holds a newer version of an entity than the one we edited.
     *
     * Local data is never discarded on this path: a `sync_conflict` row is written and
     * the user resolves it.
     */
    public data class Conflict(
        public val entityType: String,
        public val entityId: String,
        override val cause: Throwable? = null,
    ) : AppError

    /** Anything not anticipated. Always carries its throwable. */
    public data class Unexpected(
        override val cause: Throwable?,
    ) : AppError
}
