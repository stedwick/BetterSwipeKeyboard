package com.example.betterswipekeyboard

import com.example.betterswipekeyboard.layout.EmojiCategories
import com.example.betterswipekeyboard.layout.categoryStartIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiDataTest {

    @Test
    fun `every category has title icon and emojis`() {
        assertTrue(EmojiCategories.isNotEmpty())
        EmojiCategories.forEach { category ->
            assertTrue(category.title.isNotBlank())
            assertTrue(category.icon.isNotBlank())
            assertTrue(category.emojis.isNotEmpty())
        }
    }

    @Test
    fun `no blank emoji strings`() {
        EmojiCategories.forEach { category ->
            category.emojis.forEach { emoji ->
                assertTrue(emoji.isNotBlank())
            }
        }
    }

    @Test
    fun `no duplicate emojis within or across categories`() {
        val all = EmojiCategories.flatMap { it.emojis }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `category start index of first category is zero`() {
        assertEquals(0, categoryStartIndex(EmojiCategories, 0))
    }

    @Test
    fun `category start index counts one header plus emojis per preceding category`() {
        var expected = 0
        EmojiCategories.forEachIndexed { i, category ->
            assertEquals(expected, categoryStartIndex(EmojiCategories, i))
            expected += 1 + category.emojis.size
        }
    }

    @Test
    fun `consecutive start indices differ by exactly one category size plus header`() {
        for (i in 1 until EmojiCategories.size) {
            val diff = categoryStartIndex(EmojiCategories, i) - categoryStartIndex(EmojiCategories, i - 1)
            assertEquals(1 + EmojiCategories[i - 1].emojis.size, diff)
        }
    }
}
