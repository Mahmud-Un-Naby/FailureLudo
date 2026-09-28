package com.failureludo.data

/** Multipliers relative to the original pawn animation; independent of game rules. */
object PawnMovementSpeed {
    const val DEFAULT = 2f
    const val MIN = 1f
    const val MAX = 6f
    const val INCREMENT = 0.5f

    fun sanitize(value: Float): Float =
        if (value.isFinite()) value.coerceIn(MIN, MAX) else DEFAULT
}
