package et.alpha.meret.core.model

/**
 * An enum constant that has a stable string form.
 *
 * Enum names are a Kotlin implementation detail; these strings are not. They are what
 * lands in a Room `TEXT` column, in an outbox payload, and in a Postgres row, so renaming
 * a constant must never change them.
 */
public interface WireNamed {
    public val wireName: String
}

/**
 * Resolves a wire string back to its constant, or `null` if nothing matches.
 *
 * Returning `null` rather than throwing is deliberate: an unknown value means a row was
 * written by a newer build, and a reader from an older build must degrade rather than
 * crash.
 */
public fun <T : WireNamed> Iterable<T>.fromWireName(value: String): T? =
    firstOrNull { it.wireName == value }
