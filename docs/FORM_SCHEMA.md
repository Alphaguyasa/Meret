# Form schema (`form_template.schema_json`)

A project's waypoint form is one JSON object. The app reads it at runtime
(`FormSchemaParser` in `:core:model`); no project's fields are compiled into the app. The
same text is stored in `form_template.schema_json` in Room and in Postgres.

The bundled example, used until sync delivers a project's own, is
`core/model/src/main/resources/forms/example_farm_visit.json`.

## Template

```json
{
  "format": 1,
  "fields": [ { "key": "main_crop", "type": "select", "label": "…", "options": [ … ] } ]
}
```

| Property | Required | Meaning |
|---|---|---|
| `format` | no (default 1) | Breaking-change number. A template with a higher `format` than the app reads is refused. Adding a property or a field type is **not** a breaking change. |
| `fields` | yes | The questions, in the order they show. May be empty. |

## Fields

Every field has:

| Property | Required | Meaning |
|---|---|---|
| `key` | yes | The answer's key in `waypoint.form_data`. Unique in the template. Never rename it once answers exist. |
| `type` | yes | One of the types below. |
| `label` | yes | Text (see "Text") shown above the field. |
| `hint` | no | Text shown under the field. |
| `required` | no (false) | A **visible** field must be answered. A hidden field never is. |
| `visible_if` | no | When the field shows (see below). Absent means always. |

| `type` | Extra properties | Answer stored as |
|---|---|---|
| `text` | `multiline` (bool), `max_length` (whole number > 0, counted in characters) | `"text"` |
| `number` | `min`, `max` (numbers, `min` ≤ `max`), `integer` (bool), `unit` (text shown after the box, not translated) | `12.5` |
| `select` | `options` (required, non-empty) | `"teff"` |
| `multiselect` | `options` (required, non-empty), `max_selected` (whole number > 0) | `["seed", "compost"]` |
| `date` | none | `"2026-07-14"` (Gregorian `yyyy-MM-dd`, no time or zone) |
| `photo` | `max_count` (whole number > 0, default 3) | `["<waypoint_photo id>", …]` |
| `gps_point` | none | `{"lat": 9.03, "lon": 38.74, "accuracy_m": 4.5}` (`accuracy_m` optional) |

An option is `{"value": "teff", "label": …}`. `value` is what is stored: non-empty, unique in
the field, never renamed once used. Without a `label` the value is shown.

**Unknown types.** A `type` the app does not know (from a newer template) is kept as
*unsupported*: it is shown read-only, it is never required, and an answer already stored for
it is kept exactly as it is. It is never dropped.

**Unknown properties** are ignored.

## Text

A label, hint or option label is either one string (the same in every language), or an
object of language code to string:

```json
{ "en": "Main crop", "am": "ዋናው ሰብል" }
```

The app shows the phone's language, else `en`, else the first one given. Ship `am` with
every label.

## `visible_if`

A condition on the answers to fields **before** this one. A condition that names a later
field, the field itself, or a key that does not exist makes the template invalid.

```json
{ "field": "main_crop", "equals": "other" }
{ "field": "farmer_present", "not_equals": "no" }
{ "field": "household_size", "in": [1, 2, 3] }
{ "and": [ <condition>, <condition>, … ] }
{ "or":  [ <condition>, <condition>, … ] }
```

The comparison value may be text, a number or `true`/`false`; it is compared as text, except
against a number answer, which compares numerically (`2` equals `2.0`).

Rules:
- A field with no answer has no value: `equals` and `in` are false, `not_equals` is true.
- A field that is **hidden** also has no value, so hiding cascades: if B depends on A and C
  on B, hiding A hides B, and C sees no value for B.
- A `multiselect` answer `equals` a value when the value is among its choices, and is `in` a
  list when any of its choices is.
- `and` / `or` take a non-empty list and may nest.

## Saving

`FormRules.validate` checks visible fields only. `FormRules.answersToKeep` is what is
stored: the answers of hidden fields are dropped, as the template intends. Everything else is
kept, including answers to unsupported fields and to keys the current template no longer has
(a waypoint begun on an older template version). An answer the worker collected is dropped
only because the template hid its field.

## Answers (`waypoint.form_data`)

One JSON object, answer by field key, in the shapes in the table above. An unanswered field
has no entry. An answer whose shape does not match its field (or whose field is unknown) is
kept as raw JSON and written back unchanged. `form_data` that is not a JSON object is
reported as unreadable, so the caller keeps the stored text instead of overwriting it.

## Invalid templates

The parser refuses a template, with a reason naming the field and the rule, when:
- it is not JSON or not an object, `fields` is missing, or `format` is too new;
- a field has no `key`, `type` or `label`, or a `key` is used twice;
- a `select`/`multiselect` has no options, an option has no value, or a value is repeated;
- `min` > `max`, or a number property is text, or a count is not a whole number above 0;
- `required`, `multiline` or `integer` is not `true`/`false`;
- a `visible_if` is malformed or names a field that is not before it.

The reason contains template text only, never answers, so it is safe for the diagnostics
report.
