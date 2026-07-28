package com.example.betterswipekeyboard.layout

private fun digitKeys(digits: String): List<Key> =
    digits.map { Key(label = it.toString(), output = KeyOutput.Text(it.toString())) }

/**
 * Numeric dial pad for telephone input, 2FA codes and amounts: a strict 3x4
 * grid of uniform 1/3-width keys in the ITU-T telephone arrangement (what
 * Gboard/Samsung show for number/phone fields). No swipe decoding — like the
 * symbols layout, a non-spacebar drag is swallowed by the gesture loop.
 *
 * Deliberately minimal (Philip's spec): no wide keys, no visible punctuation
 * (it lives behind the 0 long-press popup — see ui/keyboard/LongPressPopup.kt,
 * which also holds the space, so no space bar is needed), and no ABC key —
 * exit is the "123"/"ABC" utility-row toggle, which switches both ways.
 */
val NumericLayout = KeyboardLayout(
    id = LayoutId.NUMERIC,
    rows = listOf(
        digitKeys("123"),
        digitKeys("456"),
        digitKeys("789"),
        listOf(
            Key(label = "⌫", output = KeyOutput.Backspace),
            Key(label = "0", output = KeyOutput.Text("0")),
            Key(label = "⏎", output = KeyOutput.Enter),
        ),
    ),
)
