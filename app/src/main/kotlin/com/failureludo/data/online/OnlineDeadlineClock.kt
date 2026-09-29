package com.failureludo.data.online

/** Display/scheduling estimate only; the server alone decides when a player times out. */
internal class OnlineDeadlineClock(private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private data class Sample(val serverMillis: Long, val receivedAt: Long)
    @Volatile private var sample: Sample? = null

    fun observe(serverMillis: Long) {
        if (serverMillis > 0) sample = Sample(serverMillis, elapsedMillis())
    }

    fun remainingMillis(deadline: Long?): Long? {
        val observed = sample ?: return null
        if (deadline == null) return null
        val elapsed = (elapsedMillis() - observed.receivedAt).coerceAtLeast(0)
        return (deadline - observed.serverMillis - elapsed).coerceAtLeast(0)
    }
}
