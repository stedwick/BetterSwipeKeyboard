package com.example.betterswipekeyboard.ime

import android.text.InputType
import com.example.betterswipekeyboard.layout.LayoutId

/**
 * Number, phone and datetime fields get the dial pad; the rest keep letters.
 * The class mask ignores variation/flag bits, so decimal/signed/numberPassword
 * numbers and date/time datetimes all qualify. Pure over the raw inputType so
 * it is unit-testable without an EditorInfo.
 */
fun isNumericInputType(inputType: Int): Boolean =
    when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_NUMBER,
        InputType.TYPE_CLASS_PHONE,
        InputType.TYPE_CLASS_DATETIME,
        -> true
        else -> false
    }

/**
 * Layout to apply when a field gains focus, or null = keep the current one.
 * Auto-shows the numpad for numeric fields and undoes only that when the next
 * field is not numeric; a manually opened numpad is likewise reset on field
 * change (Gboard resets layouts per field too). The service applies this only
 * when !restarting, so the user's in-session manual toggle is never
 * overridden and non-numeric layouts keep their existing cross-field
 * persistence (e.g. SYMBOLS stays on the next field).
 */
fun fieldStartLayout(inputType: Int, current: LayoutId): LayoutId? = when {
    isNumericInputType(inputType) -> LayoutId.NUMERIC
    current == LayoutId.NUMERIC -> LayoutId.LETTERS
    else -> null
}
