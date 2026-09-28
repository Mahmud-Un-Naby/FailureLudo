package com.failureludo.ui.screens

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import com.failureludo.data.FeedbackSettings
import com.failureludo.data.PawnMovementSpeed
import kotlin.math.roundToInt
import kotlin.math.ceil

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

/** Convert a continuous path position to the current cell transition without per-cell clocks. */
internal data class PawnPathFrame(val stepIndex: Int, val progress: Float)

internal fun pawnPathFrame(position: Float, firstStep: Int, lastStep: Int): PawnPathFrame {
    require(firstStep >= 1 && lastStep >= firstStep)
    val bounded = position.coerceIn((firstStep - 1).toFloat(), lastStep.toFloat())
    val step = ceil(bounded).toInt().coerceIn(firstStep, lastStep)
    return PawnPathFrame(step, (bounded - (step - 1)).coerceIn(0f, 1f))
}

/** A single animation clock for a whole forward or return path, including missed frames. */
internal suspend fun animatePawnPath(
    firstStep: Int,
    lastStep: Int,
    stepDurationMillis: Int,
    reducedMotion: Boolean,
    onFrame: (PawnPathFrame) -> Unit,
    onLanding: (Int) -> Unit = {}
) {
    if (lastStep < firstStep) return
    var landedStep = firstStep - 1
    animate(
        initialValue = (firstStep - 1).toFloat(), targetValue = lastStep.toFloat(),
        animationSpec = tween(
            durationMillis = if (reducedMotion) 1 else stepDurationMillis * (lastStep - firstStep + 1),
            easing = LinearEasing
        )
    ) { position, _ ->
        onFrame(pawnPathFrame(position, firstStep, lastStep))
        val reachedStep = position.toInt().coerceAtMost(lastStep)
        while (landedStep < reachedStep) onLanding(++landedStep)
    }
}
