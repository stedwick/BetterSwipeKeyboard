package com.example.betterswipekeyboard.layout

private fun letterKeys(letters: String): List<Key> =
    letters.map { Key(label = it.toString(), output = KeyOutput.Text(it.toString())) }

val QwertyLayout = KeyboardLayout(
    id = LayoutId.LETTERS,
    rows = listOf(
        letterKeys("qwertyuiop"),
        letterKeys("asdfghjkl"),
        listOf(
            Key(label = "⇧", output = KeyOutput.Shift, weight = 1.5f),
            *letterKeys("zxcvbnm").toTypedArray(),
            Key(label = "⌫", output = KeyOutput.Backspace, weight = 1.5f),
        ),
        listOf(
            Key(label = "?123", output = KeyOutput.SwitchLayout(LayoutId.SYMBOLS), weight = 1.5f),
            Key(label = "", output = KeyOutput.Microphone),
            Key(label = ",", output = KeyOutput.Text(",")),
            Key(label = "", output = KeyOutput.Text(" "), weight = 4f),
            Key(label = ".", output = KeyOutput.Text(".")),
            Key(label = "⏎", output = KeyOutput.Enter, weight = 1.5f),
        ),
    ),
)
