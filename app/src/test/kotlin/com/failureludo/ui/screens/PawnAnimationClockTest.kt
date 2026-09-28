package com.failureludo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import com.failureludo.data.FeedbackSettings
import com.failureludo.engine.PlayerColor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PawnAnimationClockTest {
    private class TestClock(private val frameMillis: Long) : MonotonicFrameClock {
        var elapsedMillis = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            elapsedMillis += frameMillis
            return onFrame(elapsedMillis * 1_000_000L)
        }
    }

    @Test fun longReturnsTakeTheirTotalDurationRatherThanTwoFramesPerCell() {
        for (frameMillis in listOf(16L, 33L, 80L)) {
            val clock = TestClock(frameMillis)
            var lastFrame: PawnPathFrame? = null
            val landings = mutableListOf<Int>()
            runBlocking(clock) {
                animatePawnPath(4, 55, 6, false, { lastFrame = it }, { landings += it })
            }
            assertTrue(clock.elapsedMillis in 312L..(312L + 2 * frameMillis))
            assertEquals(PawnPathFrame(55, 1f), lastFrame)
            assertEquals((4..55).toList(), landings)
        }
    }

    @Test fun maximumSpeedIsFasterThanDefaultAndBothBeatOriginalPerCellRunner() {
        val plan = PieceAnimationPlan(movingPieceStepCount = 7,
            capturedKeys = setOf(PlayerColor.BLUE to 0))
        for ((first, last, originalMs) in listOf(Triple(1, 6, 130), Triple(7, 58, 35))) {
            val originalClock = TestClock(16)
            runBlocking(originalClock) {
                val progress = Animatable(1f)
                repeat(last - first + 1) {
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(originalMs, easing = LinearEasing))
                }
            }
            val durations = listOf(2f, 6f).map { speed ->
                val clock = TestClock(16)
                val timing = PawnAnimationTiming(FeedbackSettings(forwardPawnSpeed = speed, backwardPawnSpeed = speed))
                runBlocking(clock) {
                    animatePawnPath(first, last, timing.stepDurationMillis(plan, first, false), false, {})
                }
                clock.elapsedMillis
            }
            assertTrue(durations[1] < durations[0])
            assertTrue(durations[0] < originalClock.elapsedMillis)
        }
    }

    @Test fun reducedMotionCompletesALongPathWithinTwoFrames() {
        val clock = TestClock(16)
        var lastFrame: PawnPathFrame? = null
        runBlocking(clock) {
            animatePawnPath(1, 52, 130, true, { lastFrame = it })
        }
        assertTrue(clock.elapsedMillis <= 32)
        assertEquals(PawnPathFrame(52, 1f), lastFrame)
    }

    @Test fun emptyPhaseDoesNotWaitForAFrameOrEmitLandings() {
        val clock = TestClock(16)
        runBlocking(clock) {
            animatePawnPath(1, 0, 130, false, { fail("Unexpected frame") }, { fail("Unexpected landing") })
        }
        assertEquals(0L, clock.elapsedMillis)
    }
}
