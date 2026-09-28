package com.failureludo.ui.screens

import com.failureludo.data.FeedbackSettings
import com.failureludo.engine.PlayerColor
import org.junit.Assert.*
import org.junit.Test

class PawnAnimationTimingTest {
    private val capture = PieceAnimationPlan(movingPieceStepCount = 4,
        capturedKeys = setOf(PlayerColor.BLUE to 0, PlayerColor.BLUE to 1))

    @Test fun defaultsPreserveForwardAndCaptureReturnDurations() {
        val timing = PawnAnimationTiming(FeedbackSettings())
        assertEquals(130, timing.stepDurationMillis(capture, 3, false))
        assertEquals(35, timing.stepDurationMillis(capture, 4, false))
    }

    @Test fun landingUsesForwardSpeedAndEntirePairReturnUsesBackwardSpeed() {
        val timing = PawnAnimationTiming(FeedbackSettings(forwardPawnSpeed = 2f, backwardPawnSpeed = 0.5f))
        for (step in 1..3) assertEquals(65, timing.stepDurationMillis(capture, step, false))
        for (step in 4..56) assertEquals(70, timing.stepDurationMillis(capture, step, false))
    }

    @Test fun normalMovementNeverUsesBackwardSpeed() {
        val plan = PieceAnimationPlan(movingPieceStepCount = 7)
        val timing = PawnAnimationTiming(FeedbackSettings(forwardPawnSpeed = 0.5f, backwardPawnSpeed = 4f))
        for (step in 1..6) assertEquals(260, timing.stepDurationMillis(plan, step, false))
    }

    @Test fun fullSliderRangeProducesPositiveNonIncreasingDurations() {
        val durations = (0..14).map { tick ->
            val speed = 0.5f + tick * 0.25f
            val timing = PawnAnimationTiming(FeedbackSettings(forwardPawnSpeed = speed, backwardPawnSpeed = speed))
            timing.stepDurationMillis(capture, 3, false) to timing.stepDurationMillis(capture, 4, false)
        }
        assertEquals(260 to 70, durations.first())
        assertEquals(33 to 9, durations.last())
        durations.zipWithNext().forEach { (slower, faster) ->
            assertTrue(faster.first in 1 until slower.first)
            // Adjacent fast return settings can round to the same whole millisecond.
            assertTrue(faster.second in 1..slower.second)
        }
    }

    @Test fun reducedMotionOverridesBothDirectionsAtEverySpeed() {
        for (speed in listOf(0.5f, 1f, 4f)) {
            val timing = PawnAnimationTiming(FeedbackSettings(forwardPawnSpeed = speed, backwardPawnSpeed = speed))
            assertEquals(1, timing.stepDurationMillis(capture, 3, true))
            assertEquals(1, timing.stepDurationMillis(capture, 4, true))
        }
    }

    @Test fun newPreferencesOnlyAffectTheNextTimingSnapshot() {
        val settings = FeedbackSettings()
        val currentMove = PawnAnimationTiming(settings)
        val nextMove = PawnAnimationTiming(settings.copy(forwardPawnSpeed = 2f, backwardPawnSpeed = 0.5f))
        assertEquals(130, currentMove.stepDurationMillis(capture, 3, false))
        assertEquals(35, currentMove.stepDurationMillis(capture, 4, false))
        assertEquals(65, nextMove.stepDurationMillis(capture, 3, false))
        assertEquals(70, nextMove.stepDurationMillis(capture, 4, false))
    }

    @Test fun invalidSpeedsCannotBreakAnAnimation() {
        val timing = PawnAnimationTiming(FeedbackSettings(forwardPawnSpeed = Float.NaN, backwardPawnSpeed = 0f))
        assertEquals(130, timing.stepDurationMillis(capture, 3, false))
        assertEquals(70, timing.stepDurationMillis(capture, 4, false))
    }
}
