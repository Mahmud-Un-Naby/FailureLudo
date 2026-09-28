package com.failureludo.data

/** Multipliers relative to the original pawn animation; independent of game rules. */
object PawnMovementSpeed {
    const val DEFAULT = 2f
    const val MIN = 0.25f
    const val MAX = 6f
    const val INCREMENT = 0.25f

    fun sanitize(value: Float): Float =
        if (value.isFinite()) value.coerceIn(MIN, MAX) else DEFAULT
}
