package et.alpha.meret.feature.waypoint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import et.alpha.meret.core.designsystem.theme.Sizes
import et.alpha.meret.core.designsystem.theme.Spacing
import et.alpha.meret.core.model.form.BundledFormTemplates
import et.alpha.meret.core.model.form.FormAnswer
import et.alpha.meret.core.model.form.FormField
import et.alpha.meret.core.model.form.LocalizedText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The project's form on the waypoint sheet: one control per visible field, in template order.
 *
 * Labels, hints and options come from the template in the phone's language
 * ([LocalizedText.resolve]); everything else (Required, the error messages, the buttons) is a
 * string resource. Every control is at least [Sizes.touchTargetField] tall, and an error is set
 * as the field's semantics error so TalkBack reads it with the field.
 */
@Composable
internal fun WaypointFormSection(
    form: FormUiState,
    fix: CaptureFix,
    onAnswerChange: (String, FormAnswer?) -> Unit,
    onNumberTextChange: (String, String) -> Unit,
    onTakePhoto: (String) -> Unit,
    onRemovePhoto: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (form !is FormUiState.Ready) return
    val language = LocalConfiguration.current.locales[0].language
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        HorizontalDivider()
        Text(
            text =
                if (form.templateId == BundledFormTemplates.FARM_VISIT_ID) {
                    stringResource(R.string.form_title_farm_visit)
                } else {
                    form.name
                },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        form.fields.forEach { state ->
            FormFieldControl(
                state = state,
                language = language,
                fix = fix,
                onAnswerChange = { onAnswerChange(state.field.key, it) },
                onNumberTextChange = { onNumberTextChange(state.field.key, it) },
                onTakePhoto = { onTakePhoto(state.field.key) },
                onRemovePhoto = { onRemovePhoto(state.field.key, it) },
            )
        }
    }
}

@Composable
private fun FormFieldControl(
    state: FormFieldUiState,
    language: String,
    fix: CaptureFix,
    onAnswerChange: (FormAnswer?) -> Unit,
    onNumberTextChange: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: (String) -> Unit,
) {
    val field = state.field
    val label = fieldLabel(field, language)
    val hint = field.hint?.resolve(language)
    val error = state.error?.let { errorText(it, field) }
    when (field) {
        is FormField.Text -> {
            TextControl(field, label, hint, error, state.answer, onAnswerChange)
        }

        is FormField.Number -> {
            NumberControl(field, label, hint, error, state.numberText, onNumberTextChange)
        }

        else -> {
            LabelledGroup(label = label, hint = hint, error = error) {
                when (field) {
                    is FormField.Select -> {
                        SelectControl(field, language, state.answer, onAnswerChange)
                    }

                    is FormField.MultiSelect -> {
                        MultiSelectControl(field, language, state.answer, onAnswerChange)
                    }

                    is FormField.Date -> {
                        DateControl(state.answer, onAnswerChange)
                    }

                    is FormField.GpsPoint -> {
                        PointControl(fix, state.answer, onAnswerChange)
                    }

                    is FormField.Photo -> {
                        PhotoControl(field, state.photos, onTakePhoto, onRemovePhoto)
                    }

                    is FormField.Unsupported -> {
                        Note(stringResource(R.string.form_field_unsupported))
                    }

                    is FormField.Text, is FormField.Number -> {
                        Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun fieldLabel(
    field: FormField,
    language: String,
): String {
    val text = field.label.resolve(language)
    return if (field.required) stringResource(R.string.form_label_required, text) else text
}

@Composable
private fun TextControl(
    field: FormField.Text,
    label: String,
    hint: String?,
    error: String?,
    answer: FormAnswer?,
    onAnswerChange: (FormAnswer?) -> Unit,
) {
    OutlinedTextField(
        value = (answer as? FormAnswer.Text)?.value.orEmpty(),
        onValueChange = { onAnswerChange(if (it.isEmpty()) null else FormAnswer.Text(it)) },
        label = { Text(label) },
        supportingText = supporting(hint, error),
        isError = error != null,
        singleLine = !field.multiline,
        minLines = if (field.multiline) MULTILINE_MIN_LINES else 1,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (field.multiline) ImeAction.Default else ImeAction.Next,
            ),
        modifier = fieldModifier(error),
    )
}

@Composable
private fun NumberControl(
    field: FormField.Number,
    label: String,
    hint: String?,
    error: String?,
    text: String,
    onTextChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text(label) },
        supportingText = supporting(hint, error),
        isError = error != null,
        suffix = unitSuffix(field.unit),
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (field.integer) KeyboardType.Number else KeyboardType.Decimal,
                imeAction = ImeAction.Next,
            ),
        modifier = fieldModifier(error),
    )
}

private fun supporting(
    hint: String?,
    error: String?,
): (@Composable () -> Unit)? {
    val text = error ?: hint ?: return null
    return { Text(text) }
}

/** A unit after the number, such as "ha". Not translated: units are symbols. */
private fun unitSuffix(unit: String?): (@Composable () -> Unit)? {
    if (unit == null) return null
    return { Text(unit) }
}

private fun fieldModifier(error: String?): Modifier {
    val base = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTargetField)
    return if (error == null) base else base.semantics { this.error(error) }
}

/** A label, the control, then the hint or the error: for fields that are not one text box. */
@Composable
private fun LabelledGroup(
    label: String,
    hint: String?,
    error: String?,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { if (error != null) this.error(error) },
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color =
                if (error !=
                    null
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
        if (hint != null) Note(hint)
        content()
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** One radio row per option. Tapping the chosen one again clears the answer. */
@Composable
private fun SelectControl(
    field: FormField.Select,
    language: String,
    answer: FormAnswer?,
    onAnswerChange: (FormAnswer?) -> Unit,
) {
    val chosen = (answer as? FormAnswer.Choice)?.value
    Column(Modifier.selectableGroup()) {
        field.options.forEach { option ->
            val selected = option.value == chosen
            OptionRow(
                text = option.label.resolve(language),
                modifier =
                    Modifier.selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = {
                            onAnswerChange(if (selected) null else FormAnswer.Choice(option.value))
                        },
                    ),
            ) { RadioButton(selected = selected, onClick = null) }
        }
    }
}

/** One checkbox row per option. At the field's limit, the unticked ones are disabled. */
@Composable
private fun MultiSelectControl(
    field: FormField.MultiSelect,
    language: String,
    answer: FormAnswer?,
    onAnswerChange: (FormAnswer?) -> Unit,
) {
    val chosen = (answer as? FormAnswer.Choices)?.values.orEmpty()
    val atLimit = field.maxSelected?.let { chosen.size >= it } == true
    Column {
        field.options.forEach { option ->
            val checked = option.value in chosen
            val enabled = checked || !atLimit
            OptionRow(
                text = option.label.resolve(language),
                enabled = enabled,
                modifier =
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = { tick ->
                            val next = if (tick) chosen + option.value else chosen - option.value
                            onAnswerChange(
                                next.takeIf { it.isNotEmpty() }?.let(FormAnswer::Choices),
                            )
                        },
                    ),
            ) { Checkbox(checked = checked, onCheckedChange = null, enabled = enabled) }
        }
    }
}

@Composable
private fun OptionRow(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Sizes.touchTargetField),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        control()
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

/**
 * A button that opens the date picker, showing the chosen date in the phone's language. The
 * picker works in UTC midnights, which is what makes a picked day the stored `yyyy-MM-dd`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateControl(
    answer: FormAnswer?,
    onAnswerChange: (FormAnswer?) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val chosen = (answer as? FormAnswer.Date)?.isoDate?.let(::parseIsoDate)
    val locale = LocalConfiguration.current.locales[0]
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = { picking = true },
            modifier = Modifier.heightIn(min = Sizes.touchTargetField),
        ) {
            Text(chosen?.let { formatDate(it, locale) } ?: stringResource(R.string.form_date_pick))
        }
        if (answer != null) {
            TextButton(
                onClick = { onAnswerChange(null) },
                modifier = Modifier.heightIn(min = Sizes.touchTargetField),
            ) { Text(stringResource(R.string.form_clear)) }
        }
    }
    if (picking) {
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    chosen?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onAnswerChange(FormAnswer.Date(day.toString()))
                    }
                    picking = false
                }) { Text(stringResource(R.string.form_date_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) {
                    Text(stringResource(R.string.waypoint_cancel))
                }
            },
        ) { DatePicker(state = pickerState) }
    }
}

private fun parseIsoDate(text: String): LocalDate? = try {
    LocalDate.parse(text)
} catch (_: DateTimeParseException) {
    null
}

private fun formatDate(
    date: LocalDate,
    locale: Locale,
): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

/** A second position, taken from the live fix only, as the waypoint's own is. */
@Composable
private fun PointControl(
    fix: CaptureFix,
    answer: FormAnswer?,
    onAnswerChange: (FormAnswer?) -> Unit,
) {
    val point = answer as? FormAnswer.Point
    if (point != null) {
        Text(
            text =
                stringResource(
                    R.string.form_point_value,
                    String.format(Locale.ROOT, COORDINATE_FORMAT, point.latitude),
                    String.format(Locale.ROOT, COORDINATE_FORMAT, point.longitude),
                ),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        OutlinedButton(
            onClick = {
                if (fix is CaptureFix.Located) {
                    onAnswerChange(
                        FormAnswer.Point(fix.latitude, fix.longitude, fix.accuracyMetres),
                    )
                }
            },
            enabled = fix is CaptureFix.Located,
            modifier = Modifier.heightIn(min = Sizes.touchTargetField),
        ) { Text(stringResource(R.string.form_point_use_current)) }
        if (point != null) {
            TextButton(
                onClick = { onAnswerChange(null) },
                modifier = Modifier.heightIn(min = Sizes.touchTargetField),
            ) { Text(stringResource(R.string.form_clear)) }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val MULTILINE_MIN_LINES = 3
private const val COORDINATE_FORMAT = "%.5f"
