package com.example.betterswipekeyboard

import com.example.betterswipekeyboard.swipe.ScoredWord
import com.example.betterswipekeyboard.swipe.SwipeTrailCapture
import com.example.betterswipekeyboard.swipe.TimedPoint
import com.example.betterswipekeyboard.swipe.Vec2
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeTrailCaptureTest {

    @Test
    fun `encode produces one valid JSON line with trail, keys and results`() {
        val line = SwipeTrailCapture.encode(
            trail = listOf(
                TimedPoint(Vec2(10.5f, 20.25f), 1000L),
                TimedPoint(Vec2(30f, 40f), 1016L),
            ),
            keyCenters = mapOf('a' to Vec2(1f, 2f), 'b' to Vec2(3f, 4f)),
            keyWidth = 97.5f,
            results = listOf(ScoredWord("ab", 0.125f), ScoredWord("ad", 1.5f)),
        )

        assertTrue("must be a single line", line.indexOf('\n') == -1)
        val json = JSONObject(line)
        assertEquals(97.5, json.getDouble("keyWidth"), 1e-6)

        val keys = json.getJSONObject("keys")
        assertEquals(2, keys.length())
        val a = keys.getJSONArray("a")
        assertEquals(1.0, a.getDouble(0), 1e-6)
        assertEquals(2.0, a.getDouble(1), 1e-6)

        val trail = json.getJSONArray("trail")
        assertEquals(2, trail.length())
        val p0 = trail.getJSONArray(0)
        assertEquals(10.5, p0.getDouble(0), 1e-6)
        assertEquals(20.25, p0.getDouble(1), 1e-6)
        assertEquals(1000L, p0.getLong(2))

        val results = json.getJSONArray("results")
        assertEquals(2, results.length())
        assertEquals("ab", results.getJSONArray(0).getString(0))
        assertEquals(0.125, results.getJSONArray(0).getDouble(1), 1e-6)
    }

    @Test
    fun `encode escapes quotes and backslashes in words`() {
        val line = SwipeTrailCapture.encode(
            trail = emptyList(),
            keyCenters = emptyMap(),
            keyWidth = 100f,
            results = listOf(ScoredWord("a\"b\\c", 0f)),
        )
        assertEquals("a\"b\\c", JSONObject(line).getJSONArray("results")
            .getJSONArray(0).getString(0))
    }

    @Test
    fun `init migrates a legacy internal log into the external file`() {
        val internal = createTempDir(suffix = "internal")
        val external = createTempDir(suffix = "external")
        try {
            val legacy = java.io.File(internal, SwipeTrailCapture.LOG_FILE_NAME)
            legacy.writeText("{\"ts\":1}\n")

            SwipeTrailCapture.init(internal, external)

            val externalLog = java.io.File(external, SwipeTrailCapture.LOG_FILE_NAME)
            assertTrue("legacy file must be deleted after migration", !legacy.exists())
            assertEquals("{\"ts\":1}\n", externalLog.readText())

            // New records go to the external file, appended after migrated lines.
            SwipeTrailCapture.enabled = true
            SwipeTrailCapture.record(
                trail = listOf(TimedPoint(Vec2(1f, 2f), 0L)),
                keyCenters = mapOf('a' to Vec2(1f, 2f)),
                keyWidth = 100f,
                results = listOf(ScoredWord("a", 0f)),
            )
            val lines = externalLog.readText().lines().filter { it.isNotBlank() }
            assertEquals(2, lines.size)
            assertTrue(lines[0].contains("\"ts\":1"))
            assertTrue(lines[1].contains("\"trail\""))
        } finally {
            SwipeTrailCapture.enabled = false
            SwipeTrailCapture.clear()
            internal.deleteRecursively()
            external.deleteRecursively()
        }
    }

    @Test
    fun `init without external storage falls back to the internal file`() {
        val internal = createTempDir(suffix = "internal")
        try {
            val legacy = java.io.File(internal, SwipeTrailCapture.LOG_FILE_NAME)
            legacy.writeText("{\"ts\":2}\n")
            SwipeTrailCapture.init(internal, null)
            assertTrue("no external dir: internal log must be kept", legacy.exists())
            SwipeTrailCapture.clear()
        } finally {
            internal.deleteRecursively()
        }
    }
}
