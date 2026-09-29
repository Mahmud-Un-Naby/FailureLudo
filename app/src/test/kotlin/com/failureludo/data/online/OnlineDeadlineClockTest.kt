package com.failureludo.data.online

import com.failureludo.engine.GameMode
import com.failureludo.engine.PlayerColor
import com.failureludo.online.*
import org.junit.Assert.*
import org.junit.Test

class OnlineDeadlineClockTest {
    @Test fun `server time and elapsed time drive countdown without phone wall clock`() {
        var elapsed = 500L
        val clock = OnlineDeadlineClock { elapsed }
        assertNull(clock.remainingMillis(20_000))
        clock.observe(10_000)
        assertEquals(10_000L, clock.remainingMillis(20_000))
        elapsed += 9_999
        assertEquals(1L, clock.remainingMillis(20_000))
        elapsed++
        assertEquals(0L, clock.remainingMillis(20_000))
        elapsed += 500_000
        assertEquals(0L, clock.remainingMillis(20_000))
        clock.observe(800_000)
        assertEquals(10_000L, clock.remainingMillis(810_000))
        assertNull(clock.remainingMillis(null))
    }

    @Test fun `restart needs a new sample and invalid clock metadata is ignored`() {
        val clock = OnlineDeadlineClock { 10 }
        clock.observe(0)
        assertNull(clock.remainingMillis(20_000))
        clock.observe(10_000)
        clock.observe(-1)
        assertEquals(10_000L, clock.remainingMillis(20_000))
        assertNull(OnlineDeadlineClock { 10 }.remainingMillis(20_000))
    }

    @Test fun `timed input waits for synchronization and blocks expired actions but permits return`() {
        val room = OnlineRoom("ABCDEFGH", "guest", 2, GameMode.FREE_FOR_ALL,
            listOf(Member("guest", "Guest", PlayerColor.RED)), status = RoomStatus.PLAYING,
            actionTimeoutMillis = 10_000, actionDeadlineAtMillis = 20_000, afkTimeoutMillis = 120_000)
        val state = OnlineSessionState(room = room, uid = "guest", connected = true, configured = true)
        assertFalse(state.canPlay)
        assertTrue(state.copy(actionRemainingMillis = 1).canPlay)
        assertFalse(state.copy(actionRemainingMillis = 0).canPlay)
        assertFalse(state.copy(actionRemainingMillis = 1, deadlineDue = true).canPlay)
        assertTrue(state.copy(actionRemainingMillis = 0).canAct)
        assertFalse(state.copy(actionRemainingMillis = 1, pending = true).canPlay)
        assertFalse(state.copy(actionRemainingMillis = 1, connected = false).canPlay)
        assertTrue(state.copy(room = room.copy(actionDeadlineAtMillis = null)).canPlay)
    }
}
