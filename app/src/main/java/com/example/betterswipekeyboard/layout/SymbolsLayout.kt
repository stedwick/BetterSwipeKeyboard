package com.example.betterswipekeyboard.layout

private fun symbolKeys(symbols: String): List<Key> =
    symbols.map { Key(label = it.toString(), output = KeyOutput.Text(it.toString())) }

val SymbolsLayout = KeyboardLayout(
    id = LayoutId.SYMBOLS,
    rows = listOf(
        KeyRow(symbolKeys("1234567890")),
        KeyRow(symbolKeys("@#\$%&*+-()")),
        KeyRow(
            listOf(
                *symbolKeys("=_\"':;!?").toTypedArray(),
                // 2u (not 1.5u) so the 8 character keys total 10 weight units
                // and match the width of rows 1-2.
                Key(label = "⌫", output = KeyOutput.Backspace, weight = 2f),
            ),
        ),
        KeyRow(
            listOf(
                Key(label = "ABC", output = KeyOutput.SwitchLayout(LayoutId.LETTERS), weight = 1.5f),
                Key(label = ",", output = KeyOutput.Text(",")),
                Key(label = "", output = KeyOutput.Text(" "), weight = 5f),
                Key(label = ".", output = KeyOutput.Text(".")),
                Key(label = "⏎", output = KeyOutput.Enter, weight = 1.5f),
            ),
        ),
    ),
)
