package com.example.betterswipekeyboard

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Store-listing helper, NOT a real test: injects a scripted curved touch path
 * (a swipe-typing trail) into the global input pipeline and HOLDS the final
 * point down, so the host can take a screenshot of the keyboard mid-swipe.
 *
 * Run from the host:
 *   adb shell am instrument -w \
 *     -e class com.example.betterswipekeyboard.SwipePoseInjector \
 *     -e path "x1,y1;x2,y2;..." -e moveMs 1400 -e holdMs 9000 \
 *     com.philpdx.keyboard.test/androidx.test.runner.AndroidJUnitRunner
 *
 * The host sleeps (moveMs - epsilon) after starting, then screencaps while the
 * finger is parked at the last point. Coordinates are display pixels.
 */
@RunWith(AndroidJUnit4::class)
class SwipePoseInjector {

    @Test
    fun injectAndHold() {
        val args = InstrumentationRegistry.getArguments()
        val path = args.getString("path")!!.split(";").map {
            val (x, y) = it.split(",")
            x.toFloat() to y.toFloat()
        }
        require(path.size >= 2) { "need at least 2 points" }
        val moveMs = args.getString("moveMs")?.toLong() ?: 1400L
        val holdMs = args.getString("holdMs")?.toLong() ?: 9000L
        // am instrument KILLS the app's foreground activity when it starts,
        // so give the host time to re-open the target screen before injecting.
        val delayMs = args.getString("delayMs")?.toLong() ?: 0L
        SystemClock.sleep(delayMs)

        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()

        fun event(action: Int, x: Float, y: Float, eventTime: Long): MotionEvent =
            MotionEvent.obtain(
                downTime, eventTime, action, x, y, 0
            ).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }

        // Finger down on the first key.
        ui.injectInputEvent(event(MotionEvent.ACTION_DOWN, path[0].first, path[0].second, downTime), true)

        // Interpolate moves along the polyline, ~90 events evenly over moveMs.
        val segments = path.zipWithNext()
        val totalLen = segments.sumOf { (a, b) ->
            Math.hypot((b.first - a.first).toDouble(), (b.second - a.second).toDouble())
        }
        val steps = 90
        var segIdx = 0
        var segStartLen = 0.0
        for (i in 1..steps) {
            val targetLen = totalLen * i / steps
            while (segIdx < segments.size - 1 &&
                segStartLen + Math.hypot(
                    (segments[segIdx].second.first - segments[segIdx].first.first).toDouble(),
                    (segments[segIdx].second.second - segments[segIdx].first.second).toDouble()
                ) < targetLen
            ) {
                segStartLen += Math.hypot(
                    (segments[segIdx].second.first - segments[segIdx].first.first).toDouble(),
                    (segments[segIdx].second.second - segments[segIdx].first.second).toDouble()
                )
                segIdx++
            }
            val (a, b) = segments[segIdx]
            val segLen = Math.hypot((b.first - a.first).toDouble(), (b.second - a.second).toDouble())
            val t = if (segLen == 0.0) 0.0 else (targetLen - segStartLen) / segLen
            val x = (a.first + (b.first - a.first) * t.toFloat())
            val y = (a.second + (b.second - a.second) * t.toFloat())
            val now = SystemClock.uptimeMillis()
            ui.injectInputEvent(event(MotionEvent.ACTION_MOVE, x, y, now), true)
            SystemClock.sleep(moveMs / steps)
        }

        // Park the finger on the last point: host takes the screenshot now.
        SystemClock.sleep(holdMs)

        val now = SystemClock.uptimeMillis()
        val last = path.last()
        ui.injectInputEvent(event(MotionEvent.ACTION_UP, last.first, last.second, now), true)

        // Keep the instrumentation session alive after finger-up so the host
        // can screenshot the committed state before am instrument tears the
        // app process down.
        val postMs = args.getString("postMs")?.toLong() ?: 0L
        SystemClock.sleep(postMs)
    }
}
