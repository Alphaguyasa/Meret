package et.alpha.meret.core.model.form

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

// Typed reads of a template's properties for FormSchemaParser. A property that is absent is
// null; one that is present but of the wrong kind fails the whole template.

internal fun JsonObject.string(name: String): String? {
    val value = this[name] as? JsonPrimitive ?: return null
    return if (value.isString) value.content else null
}

internal fun JsonObject.boolean(
    name: String,
    key: String,
): Boolean? {
    val value = this[name] ?: return null
    return (value as? JsonPrimitive)?.booleanOrNull
        ?: fail("field '$key': $name is not true or false")
}

internal fun JsonObject.number(
    name: String,
    key: String,
): Double? {
    val value = this[name] ?: return null
    val primitive = value as? JsonPrimitive
    if (primitive == null || primitive.isString) fail("field '$key': $name is not a number")
    return primitive.doubleOrNull ?: fail("field '$key': $name is not a number")
}

internal fun JsonObject.positiveInt(
    name: String,
    key: String,
): Int? {
    val value = this[name] ?: return null
    val primitive = value as? JsonPrimitive
    val int = if (primitive == null || primitive.isString) null else primitive.intOrNull
    if (int == null || int < 1) fail("field '$key': $name is not a whole number above 0")
    return int
}

/** Stops parsing: the template is invalid for [message]. */
internal fun fail(message: String): Nothing = throw FormSchemaException(message)

internal class FormSchemaException(
    message: String,
) : Exception(message)
