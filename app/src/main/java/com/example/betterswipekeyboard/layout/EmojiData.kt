package com.example.betterswipekeyboard.layout

/**
 * Emoji panel content as pure data, next to the letter/symbol layouts.
 * Hand-picked common emojis — deliberately not a full Unicode dataset:
 * a few categories with a few dozen emojis each covers daily use and keeps
 * this file reviewable. Emoji are plain strings; the UI commits them
 * verbatim via KeyboardAction.InsertText.
 */
data class EmojiCategory(
    /** Header shown above the section in the grid. */
    val title: String,
    /** Representative emoji shown in the category bar. */
    val icon: String,
    val emojis: List<String>,
)

val EmojiCategories: List<EmojiCategory> = listOf(
    EmojiCategory(
        title = "Smileys",
        icon = "😀",
        emojis = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "😉", "😊", "😇", "🥰", "😍", "🤩", "😘",
            "😋", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫",
            "🤔", "😐", "😑", "😶", "😏", "😒", "🙄", "😬",
            "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕",
            "🤢", "🤧", "🥵", "🥶", "😵", "🤯", "🤠", "🥳",
            "😎", "🤓", "🧐", "😕", "😟", "🙁", "😮", "😯",
            "😲", "😳", "🥺", "😦", "😧", "😨", "😰", "😥",
            "😢", "😭", "😱", "😖", "😞", "😓", "😩", "😤",
            "😡", "😠", "🤬", "😈", "💀", "💩", "🤡", "👻",
        ),
    ),
    EmojiCategory(
        title = "People & Gestures",
        icon = "👋",
        emojis = listOf(
            "👋", "🤚", "✋", "🖖", "👌", "🤌", "🤏", "✌️",
            "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "👇",
            "☝️", "👍", "👎", "✊", "👊", "👏", "🙌", "👐",
            "🤲", "🤝", "🙏", "💪", "🦾", "🖐️", "👁️", "👀",
            "👶", "👧", "🧒", "👦", "👩", "🧑", "👨", "👵",
            "🧓", "👴", "👮", "🕵️", "💂", "👷", "🤴", "👸",
            "👳", "👲", "🧕", "🤵", "👰", "🤰", "🤱", "👼",
            "🎅", "🤶", "🦸", "🦹", "🧙", "🧚", "🧛", "🧜",
            "🏃", "🚶", "🧍", "👯", "🕺", "💃", "🗣️", "👤",
        ),
    ),
    EmojiCategory(
        title = "Animals & Nature",
        icon = "🐻",
        emojis = listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔",
            "🐧", "🐦", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗",
            "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐜",
            "🐢", "🐍", "🦎", "🐙", "🦑", "🦐", "🦀", "🐡",
            "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🐊", "🐘",
            "🦒", "🦘", "🐎", "🐖", "🐑", "🐐", "🦌", "🐕",
            "🐈", "🐇", "🐿️", "🦔", "🌵", "🌲", "🌳", "🌴",
            "🌱", "🌿", "☘️", "🍀", "🍁", "🍂", "🍃", "🌺",
            "🌻", "🌹", "🌷", "🌸", "💐", "🌙", "⭐", "🔥",
        ),
    ),
    EmojiCategory(
        title = "Food & Drink",
        icon = "🍔",
        emojis = listOf(
            "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇",
            "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥",
            "🥝", "🍅", "🥑", "🥦", "🥬", "🥒", "🌶️", "🌽",
            "🥕", "🧄", "🧅", "🥔", "🍠", "🥐", "🍞", "🥖",
            "🥨", "🧀", "🥚", "🍳", "🥞", "🧇", "🥓", "🍔",
            "🍟", "🍕", "🌭", "🥪", "🌮", "🌯", "🥗", "🍝",
            "🍜", "🍲", "🍣", "🍱", "🥟", "🍤", "🍙", "🍚",
            "🍦", "🍧", "🍨", "🍩", "🍪", "🎂", "🍰", "🧁",
            "🍫", "🍬", "🍭", "☕", "🍵", "🧃", "🥤", "🍺",
        ),
    ),
    EmojiCategory(
        title = "Activities & Sport",
        icon = "⚽",
        emojis = listOf(
            "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉",
            "🥏", "🎱", "🏓", "🏸", "🏒", "🥍", "🏏", "🥅",
            "⛳", "🏹", "🎣", "🥊", "🥋", "🎽", "🛹", "🛷",
            "⛸️", "🥌", "🎿", "⛷️", "🏂", "🏋️", "🤼", "🤸",
            "⛹️", "🤺", "🤾", "🏌️", "🏇", "🧘", "🏄", "🏊",
            "🤽", "🚣", "🧗", "🚵", "🚴", "🏆", "🥇", "🥈",
            "🥉", "🏅", "🎖️", "🎗️", "🎫", "🎟️", "🎪", "🤹",
            "🎭", "🩰", "🎨", "🎬", "🎤", "🎧", "🎼", "🎹",
            "🥁", "🎷", "🎺", "🎸", "🪕", "🎻", "🎲", "♟️",
            "🎯", "🎳", "🎮", "🎰", "🧩", "🪁", "🎈", "🎉",
        ),
    ),
    EmojiCategory(
        title = "Travel & Objects",
        icon = "✈️",
        emojis = listOf(
            "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑",
            "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🛵", "🏍️",
            "🚲", "🛴", "🚏", "🛣️", "⛽", "🚨", "🚥", "🚦",
            "🚂", "🚆", "🚇", "🚈", "🚉", "✈️", "🛫", "🛬",
            "🚀", "🛸", "🚁", "⛵", "🚤", "🛥️", "🚢", "⚓",
            "🗺️", "🗿", "🗽", "🗼", "🏰", "🏯", "🏟️", "🎡",
            "🎢", "⛲", "⛺", "🏕️", "🏖️", "🏝️", "🏔️", "🌋",
            "⌚", "📱", "💻", "⌨️", "🖥️", "🖨️", "🖱️", "📷",
            "📹", "🔍", "💡", "🔦", "🕯️", "📖", "📚", "📝",
            "✏️", "🖊️", "📌", "📍", "🔒", "🔑", "🔨", "🧲",
            "🔫", "💣", "🧰", "⚙️", "💰", "💎", "⏰", "🎁",
        ),
    ),
)

/**
 * Item index in the flattened emoji grid where [categoryIndex] starts.
 * The grid emits one full-span header item per category followed by that
 * category's emoji items, so category N starts after the headers and
 * emojis of all preceding categories. EmojiPanel's item order must match
 * this accounting exactly, or category jumps land mid-section.
 */
fun categoryStartIndex(categories: List<EmojiCategory>, categoryIndex: Int): Int {
    var index = 0
    for (i in 0 until categoryIndex) {
        index += 1 + categories[i].emojis.size
    }
    return index
}
