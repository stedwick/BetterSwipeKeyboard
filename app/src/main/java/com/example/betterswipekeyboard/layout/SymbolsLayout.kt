package com.example.betterswipekeyboard.layout

private fun symbolKeys(symbols: String): List<Key> =
    symbols.map { Key(label = it.toString(), output = KeyOutput.Text(it.toString())) }

val SymbolsLayout = KeyboardLayout(
    id = LayoutId.SYMBOLS,
    rows = listOf(
        symbolKeys("1234567890"),
        symbolKeys("@#\$%&*+-()"),
        listOf(
            *symbolKeys("=_\"':;!?").toTypedArray(),
            Key(label = "⌫", output = KeyOutput.Backspace, weight = 1.5f),
        ),
        listOf(
            Key(label = "ABC", output = KeyOutput.SwitchLayout(LayoutId.LETTERS), weight = 1.5f),
            Key(label = ",", output = KeyOutput.Text(",")),
            Key(label = "", output = KeyOutput.Text(" "), weight = 5f),
            Key(label = ".", output = KeyOutput.Text(".")),
            Key(label = "⏎", output = KeyOutput.Enter, weight = 1.5f),
        ),
    ),
)
