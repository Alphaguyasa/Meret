package et.alpha.meret.feature.waypoint

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import et.alpha.meret.core.model.form.FieldError
import et.alpha.meret.core.model.form.FormField

/** The message for [error] on [field]. One per [FieldError]. */
@Composable
internal fun errorText(
    error: FieldError,
    field: FormField,
): String = when (error) {
    FieldError.Required -> {
        stringResource(R.string.form_error_required)
    }

    FieldError.WrongType -> {
        if (field is FormField.Number) {
            stringResource(R.string.form_error_not_a_number)
        } else {
            stringResource(R.string.form_error_wrong_type)
        }
    }

    is FieldError.TooLong -> {
        stringResource(R.string.form_error_too_long, error.maxLength)
    }

    is FieldError.BelowMin -> {
        stringResource(R.string.form_error_below_min, formatLimit(error.min))
    }

    is FieldError.AboveMax -> {
        stringResource(R.string.form_error_above_max, formatLimit(error.max))
    }

    FieldError.NotWholeNumber -> {
        stringResource(R.string.form_error_not_whole)
    }

    FieldError.UnknownOption -> {
        stringResource(R.string.form_error_unknown_option)
    }

    is FieldError.TooManyChoices -> {
        stringResource(R.string.form_error_too_many_choices, error.maxSelected)
    }

    FieldError.InvalidDate -> {
        stringResource(R.string.form_error_invalid_date)
    }

    is FieldError.TooManyPhotos -> {
        stringResource(R.string.form_error_too_many_photos, error.maxCount)
    }

    FieldError.InvalidPoint -> {
        stringResource(R.string.form_error_invalid_point)
    }
}

/** A range limit as the worker would write it: 30, not 30.0. Digits in any locale. */
internal fun formatLimit(value: Double): String = if (value == Math.rint(value)) {
    value.toLong().toString()
} else {
    value.toBigDecimal().stripTrailingZeros().toPlainString()
}
