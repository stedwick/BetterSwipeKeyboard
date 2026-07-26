package com.example.betterswipekeyboard.emoji

import com.example.betterswipekeyboard.layout.EmojiCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Two layers of tests: small synthetic tables pin the matcher rules
 * (tokenization, bigram-first, recency, singular fallback, dedup, cap),
 * and tests against the real generated asset pin the data quality the
 * feature promises (the "plane" example, panel coverage).
 */
class EmojiSuggesterTest {

    private lateinit var real: EmojiSuggester
    private lateinit var rawTable: Map<String, List<String>>

    @Before
    fun setUp() {
        val stream = javaClass.getResourceAsStream("/emoji_keywords_en.txt")
            ?: error("emoji_keywords_en.txt missing from test resources")
        real = EmojiSuggester.load(stream)
        rawTable = parseAsset(
            javaClass.getResourceAsStream("/emoji_keywords_en.txt")!!
                .bufferedReader().readLines(),
        )
    }

    // --- tokenization (synthetic tables) ---

    @Test
    fun `empty and blank text suggest nothing`() {
        val s = EmojiSuggester(mapOf("cat" to listOf("🐱")))
        assertEquals(emptyList<String>(), s.suggest(""))
        assertEquals(emptyList<String>(), s.suggest("   \n"))
        assertEquals(emptyList<String>(), s.suggest("123 !!!"))
    }

    @Test
    fun `punctuation and emoji between words are skipped`() {
        val s = EmojiSuggester(mapOf("cat" to listOf("🐱")))
        // After inserting an emoji the last LETTER token is still "cat",
        // so the row stays relevant right after picking a suggestion.
        assertEquals(listOf("🐱"), s.suggest("my cat!! 🐱 "))
    }

    @Test
    fun `partial last word counts as a token`() {
        val s = EmojiSuggester(mapOf("cat" to listOf("🐱")))
        assertEquals(listOf("🐱"), s.suggest("cat"))
        assertEquals(emptyList<String>(), s.suggest("ca"))
    }

    @Test
    fun `only the last TOKEN_WINDOW words are considered`() {
        val s = EmojiSuggester(
            mapOf("dog" to listOf("🐶"), "cat" to listOf("🐱")),
        )
        val window = EmojiSuggester.TOKEN_WINDOW
        val filler = (1..window).joinToString(" ") { "filler$it" }
        assertEquals(listOf("🐶"), s.suggest("cat $filler dog"))
        // "cat" pushed out of the window by one more word.
        assertEquals(emptyList<String>(), s.suggest("cat $filler extra dog".replace("dog", "zzz")))
    }

    @Test
    fun `matching is case-insensitive`() {
        val s = EmojiSuggester(mapOf("cat" to listOf("🐱")))
        assertEquals(listOf("🐱"), s.suggest("My CAT"))
    }

    // --- matching and ranking (synthetic tables) ---

    @Test
    fun `bigram is matched and ranked before unigrams`() {
        val s = EmojiSuggester(
            mapOf("taking off" to listOf("🛫"), "off" to listOf("📴")),
        )
        assertEquals(listOf("🛫", "📴"), s.suggest("taking off"))
    }

    @Test
    fun `bigrams are matched anywhere in the token window`() {
        val s = EmojiSuggester(
            mapOf("taking off" to listOf("🛫"), "soon" to listOf("🔜")),
        )
        // The bigram is not the last two words, but still inside the window.
        assertEquals(listOf("🛫", "🔜"), s.suggest("taking off soon"))
    }

    @Test
    fun `most recent word ranks first among unigram matches`() {
        val s = EmojiSuggester(
            mapOf("cat" to listOf("🐱"), "dog" to listOf("🐶")),
        )
        assertEquals(listOf("🐶", "🐱"), s.suggest("cat dog"))
        assertEquals(listOf("🐱", "🐶"), s.suggest("dog cat"))
    }

    @Test
    fun `singular fallback strips es then s`() {
        val s = EmojiSuggester(
            mapOf("plane" to listOf("✈️"), "bus" to listOf("🚌")),
        )
        assertEquals(listOf("✈️"), s.suggest("planes"))
        assertEquals(listOf("🚌"), s.suggest("buses"))
        // Exact match wins over the stripped form.
        val exact = EmojiSuggester(
            mapOf("class" to listOf("🏫"), "clas" to listOf("❌")),
        )
        assertEquals(listOf("🏫"), exact.suggest("class"))
    }

    @Test
    fun `duplicates collapse keeping first rank`() {
        val s = EmojiSuggester(
            mapOf(
                "fire" to listOf("🔥", "🚒"),
                "hot" to listOf("🔥", "🥵"),
            ),
        )
        assertEquals(listOf("🔥", "🥵", "🚒"), s.suggest("fire hot"))
    }

    @Test
    fun `results are capped at maxResults defaulting to one row of 8`() {
        val many = (1..10).map { "emoji$it" }
        val s = EmojiSuggester(mapOf("many" to many))
        assertEquals(many.take(8), s.suggest("many"))
        assertEquals(many.take(3), s.suggest("many", maxResults = 3))
    }

    @Test
    fun `loader skips comments and malformed lines`() {
        val text = "# header\n" +
            "cat\t🐱,🐈\n" +
            "no-tab-here\n" +
            "\t🚫\n" +
            "dog\t🐶\n"
        val s = EmojiSuggester.load(text.byteInputStream())
        assertEquals(listOf("🐱", "🐈"), s.suggest("cat"))
        assertEquals(listOf("🐶"), s.suggest("dog"))
        assertEquals(emptyList<String>(), s.suggest("no-tab-here"))
    }

    // --- real asset: the feature's promises ---

    @Test
    fun `the headline example works on the real asset`() {
        val suggestions = real.suggest("my plane is taking off soon")
        assertTrue("expected ✈️ in $suggestions", "✈️" in suggestions)
        assertTrue("expected 🛫 in $suggestions", "🛫" in suggestions)
    }

    @Test
    fun `plane suggests the panel airplane before exotic ones`() {
        val suggestions = real.suggest("plane")
        assertEquals("✈️", suggestions.first())
        assertTrue("🛫" in suggestions)
        assertTrue(suggestions.indexOf("✈️") < suggestions.indexOf("🛩"))
    }

    @Test
    fun `planes falls back to the singular on the real asset`() {
        assertTrue("✈️" in real.suggest("two planes"))
    }

    @Test
    fun `hand-tuned aliases are present on the real asset`() {
        val lol = real.suggest("that is hilarious lol")
        assertTrue("😂" in lol)
        assertEquals("😂", lol.first())
        assertEquals("🛫", real.suggest("taking off").first())
    }

    @Test
    fun `unknown words suggest nothing on the real asset`() {
        assertEquals(emptyList<String>(), real.suggest("qwxyzt blorptastic"))
    }

    @Test
    fun `every panel emoji is reachable through at least one keyword`() {
        val covered = rawTable.values.flatten().toSet()
        EmojiCategories.forEach { category ->
            category.emojis.forEach { emoji ->
                assertTrue("panel emoji $emoji (${category.title}) has no keyword", emoji in covered)
            }
        }
    }

    @Test
    fun `asset lines are well formed`() {
        assertTrue(rawTable.size > 1000)
        rawTable.forEach { (keyword, emojis) ->
            assertTrue(keyword.isNotBlank())
            assertTrue(keyword == keyword.lowercase())
            emojis.forEach { emoji -> assertTrue(emoji.isNotBlank()) }
        }
    }

    private fun parseAsset(lines: List<String>): Map<String, List<String>> =
        lines.filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line ->
                val tab = line.indexOf('\t')
                line.substring(0, tab) to line.substring(tab + 1).split(',')
            }
}
