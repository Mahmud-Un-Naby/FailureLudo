package com.failureludo.ui.screens

import com.failureludo.data.FeedbackSettings
import com.failureludo.data.PawnMovementSpeed
import kotlin.math.roundToInt

/** Snapshot once per move so changing a setting cannot change speed halfway through a path. */
internal class PawnAnimationTiming(settings: FeedbackSettings) {
    private val forwardStepMs = stepDuration(130, settings.forwardPawnSpeed)
    private val backwardStepMs = stepDuration(35, settings.backwardPawnSpeed)

    fun stepDurationMillis(plan: PieceAnimationPlan, stepIndex: Int, reducedMotion: Boolean): Int =
        when {
            reducedMotion -> 1
            // The collision landing still belongs to the forward path. Captured pawns
            // start returning only on the following step, including captured pairs.
            plan.hasCapture && stepIndex >= plan.movingPieceStepCount -> backwardStepMs
            else -> forwardStepMs
        }

    private fun stepDuration(baseMs: Int, speed: Float): Int =
        (baseMs / PawnMovementSpeed.sanitize(speed)).roundToInt().coerceAtLeast(1)
}
