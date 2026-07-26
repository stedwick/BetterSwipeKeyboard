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
 * Item index where [categoryIndex]'s section starts, counting only the
 * category sections: the grid emits one full-span header item per
 * category followed by that category's emoji items, so category N starts
 * after the headers and emojis of all preceding categories. EmojiPanel
 * prepends full-span leading items (suggestion block, Categories label,
 * category bar) — use [categoryJumpIndex] for actual scroll targets.
 */
fun categoryStartIndex(categories: List<EmojiCategory>, categoryIndex: Int): Int {
    var index = 0
    for (i in 0 until categoryIndex) {
        index += 1 + categories[i].emojis.size
    }
    return index
}

/** Leading full-span items always preceding the sections: "Categories" label + category bar. */
private const val PANEL_FIXED_LEADING_ITEMS = 2

/** Extra leading items while the suggestion row is visible: "Suggestions" label + row. */
private const val PANEL_SUGGESTION_ITEMS = 2

/**
 * Scroll target for [categoryIndex]'s section header in EmojiPanel's
 * single scroll surface. The panel puts full-span items ahead of the
 * sections — always a "Categories" label and the category bar, plus the
 * "Suggestions" label and row while suggestions exist — so jump targets
 * shift with [hasSuggestions]. EmojiPanel's item order must match this
 * accounting exactly, or category jumps land mid-list.
 */
fun categoryJumpIndex(
    categories: List<EmojiCategory>,
    hasSuggestions: Boolean,
    categoryIndex: Int,
): Int =
    (if (hasSuggestions) PANEL_SUGGESTION_ITEMS else 0) +
        PANEL_FIXED_LEADING_ITEMS +
        categoryStartIndex(categories, categoryIndex)
