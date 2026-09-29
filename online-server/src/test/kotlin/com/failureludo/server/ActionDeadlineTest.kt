package com.failureludo.server

import com.failureludo.engine.*
import com.failureludo.online.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ActionDeadlineTest {
    private val store = MemoryRoomStore()
    private var now = 1_000_000L
    private var die = 6
    private val rolls = AtomicInteger()
    private val api = GameController(store, { rolls.incrementAndGet(); die }, { "ABCDEFGH" }, { now })
    private fun id() = UUID.randomUUID().toString()
    private fun start(count: Int = 2, mode: GameMode = GameMode.FREE_FOR_ALL): OnlineRoom {
        var room = api.create("host", id(), "Host", count, mode).room
        assertNull(room.actionDeadlineAtMillis)
        assertEquals(10_000L, room.actionTimeoutMillis)
        assertEquals(120_000L, room.afkTimeoutMillis)
        for (i in 1 until count) room = api.execute("guest$i", room.code, id(), null, Command.Join("Guest $i")).room
        return act(room, Command.Start, "host").room
    }
    private fun act(room: OnlineRoom, command: Command, uid: String = "guest1", request: String = id()) =
        api.execute(uid, room.code, request, room.revision, command)
    private fun error(code: String, action: () -> Unit) =
        assertEquals(code, assertThrows(ApiException::class.java) { action() }.code)
    private fun bot(room: OnlineRoom): OnlineRoom {
        now = requireNotNull(room.nextDeadlineAtMillis())
        return act(room, Command.CheckTimeout).room
    }

    @Test fun `every roll and move receives ten seconds including bonus rolls`() {
        var room = start()
        assertEquals(now + 10_000, room.actionDeadlineAtMillis)
        now += 9_999
        room = act(room, Command.Roll, "host").room
        assertEquals(now + 10_000, room.actionDeadlineAtMillis)
        now += 9_999
        room = act(room, Command.Move(1, 0, false), "host").room
        assertEquals(TurnPhase.WAITING_FOR_ROLL, room.game!!.turnPhase)
        assertEquals("host", room.controllerUid(room.game!!.currentPlayer.color))
        assertEquals(now + 10_000, room.actionDeadlineAtMillis)
        now += 9_999
        room = act(room, Command.Roll, "host").room
        assertEquals(2, room.game!!.lastDice!!.rollCount)
        assertEquals(now + 10_000, room.actionDeadlineAtMillis)
        assertTrue(room.members.all { it.afkSinceMillis == null })
    }

    @Test fun `first missed action rolls once then waits ten seconds before choosing a pawn`() {
        val startedAt = now
        var room = start()
        now += 9_999
        error("TIME_REMAINING") { act(room, Command.CheckTimeout) }
        assertEquals(0, rolls.get())
        now++
        error("TURN_EXPIRED") { act(room, Command.Roll, "host") }
        room = act(room, Command.CheckTimeout).room
        assertEquals("BOT_ROLL", room.lastAction!!.type)
        assertEquals("host", room.lastAction!!.uid)
        assertEquals(1, rolls.get())
        assertEquals(startedAt, room.members.first().afkSinceMillis)
        assertFalse(room.hasResigned("host"))
        assertEquals(TurnPhase.WAITING_FOR_PIECE_SELECTION, room.game!!.turnPhase)
        assertEquals(now + 10_000, room.actionDeadlineAtMillis)
        error("TIME_REMAINING") { act(room, Command.CheckTimeout) }
        room = bot(room)
        assertEquals("BOT_MOVE", room.lastAction!!.type)
        assertEquals(1, rolls.get())
        assertEquals(startedAt, room.members.first().afkSinceMillis)
        assertEquals(TurnPhase.WAITING_FOR_ROLL, room.game!!.turnPhase)
        assertEquals(now + 10_000, room.actionDeadlineAtMillis)
    }

    @Test fun `human action takes over next choice and clears AFK`() {
        var room = bot(start())
        now += 1
        room = act(room, Command.Move(1, 0, false), "host").room
        assertNull(room.members.first().afkSinceMillis)
        assertEquals("MOVE", room.lastAction!!.type)
        assertEquals(now + 10_000, room.actionDeadlineAtMillis)
        room = bot(room)
        assertEquals("BOT_ROLL", room.lastAction!!.type)
        assertEquals(now - 10_000, room.members.first().afkSinceMillis)
    }

    @Test fun `return during another player's turn clears AFK without extending their clock`() {
        die = 2
        var room = bot(start())
        assertEquals(PlayerColor.BLUE, room.game!!.currentPlayer.color)
        val deadline = room.actionDeadlineAtMillis
        now += 3_000
        room = act(room, Command.Return, "host").room
        assertNull(room.members.first().afkSinceMillis)
        assertEquals(deadline, room.actionDeadlineAtMillis)
        assertEquals("RETURN", room.lastAction!!.type)
        error("ALREADY_ACTIVE") { act(room, Command.Return, "host") }
    }

    @Test fun `return on own turn gives a fresh action window without changing pending dice`() {
        var room = bot(start())
        val game = room.game
        now += 8_000
        room = act(room, Command.Return, "host").room
        assertEquals(game, room.game)
        assertEquals(now + 10_000, room.actionDeadlineAtMillis)
        assertNull(room.members.first().afkSinceMillis)
    }

    @Test fun `bot coverage never extends the original two minute AFK limit`() {
        val began = now
        var room = start()
        while (now < began + 120_000) room = bot(room)
        assertTrue(room.hasResigned("host"))
        assertEquals("TIMEOUT", room.lastAction!!.type)
        assertEquals(RoomStatus.FINISHED, room.status)
        assertEquals(listOf(PlayerId(2)), room.game!!.winners)
        assertNull(room.actionDeadlineAtMillis)
        assertNull(room.nextDeadlineAtMillis())
        error("NOT_PLAYING") { act(room, Command.Return, "host") }
    }

    @Test fun `abandoned room forfeits overdue seat on first check without retroactive bot moves`() {
        val room = start()
        now += 120_000
        val next = act(room, Command.CheckTimeout).room
        assertTrue(next.hasResigned("host"))
        assertEquals("TIMEOUT", next.lastAction!!.type)
        assertEquals(0, rolls.get())
    }

    @Test fun `AFK expiry is enforced even while another player has action time left`() {
        var room = start(3)
        val due = now + 120_000
        room = room.copy(members = room.members.map { if (it.uid == "guest1") it.copy(afkSinceMillis = now) else it })
        store.seed(room)
        now = due - 1
        // Model the host beginning a new action just before another member's AFK expiry.
        room = room.copy(actionDeadlineAtMillis = now + 10_000)
        store.seed(room)
        assertEquals(due, room.nextDeadlineAtMillis())
        now = due
        error("TURN_EXPIRED") { act(room, Command.Roll, "host") }
        error("AFK_EXPIRED") { act(room, Command.Return, "guest1") }
        val next = act(room, Command.CheckTimeout, "guest2").room
        assertTrue(next.hasResigned("guest1"))
        assertFalse(next.hasResigned("host"))
        assertEquals(room.actionDeadlineAtMillis, next.actionDeadlineAtMillis)
        assertEquals(room.game!!.currentPlayer, next.game!!.currentPlayer)
    }

    @Test fun `team AFK transfers both colors and pending move at two minutes`() {
        var room = start(4, GameMode.TEAM)
        room = bot(room)
        val game = room.game
        now = room.members.first().afkSinceMillis!! + 120_000
        room = act(room, Command.CheckTimeout).room
        assertEquals(game, room.game)
        assertEquals("guest2", room.controllerUid(PlayerColor.RED))
        assertEquals("guest2", room.controllerUid(PlayerColor.YELLOW))
        assertEquals(now + 10_000, room.actionDeadlineAtMillis)
        room = act(room, Command.Move(1, 0, false), "guest2").room
        assertEquals("MOVE", room.lastAction!!.type)
        now += 120_000
        room = act(room, Command.CheckTimeout).room
        assertEquals(RoomStatus.FINISHED, room.status)
        assertEquals(listOf(PlayerId(2), PlayerId(4)), room.game!!.winners)
    }

    @Test fun `unrelated resignation does not reset action timer`() {
        var room = start(3)
        val deadline = room.actionDeadlineAtMillis
        now += 4_000
        room = act(room, Command.Resign, "guest1").room
        assertEquals(deadline, room.actionDeadlineAtMillis)
    }

    @Test fun `lost bot response and restart replay original receipt without rerolling`() {
        val room = start()
        now += 10_000
        val request = id()
        store.retryCallback = true
        store.loseNextResponse = true
        error("STORE_TIMEOUT") { act(room, Command.CheckTimeout, request = request) }
        assertEquals(1, rolls.get())
        val after = api.get("host", room.code)
        now += 10_000
        val progressed = act(after, Command.CheckTimeout).room
        val restarted = GameController(store, { fail("Retry rerolled"); 1 }, nowMillis = { now })
        val duplicate = restarted.execute("guest1", room.code, request, room.revision, Command.CheckTimeout)
        assertTrue(duplicate.duplicate)
        assertEquals(after.revision, duplicate.acceptedRevision)
        assertEquals(progressed, duplicate.room)
        assertEquals(1, rolls.get())
    }

    @Test fun `concurrent deadline checks advance exactly once`() {
        val room = start()
        now += 10_000
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = pool.invokeAll(listOf("host", "guest1").map { uid -> Callable {
                runCatching { act(room, Command.CheckTimeout, uid) }
            } }).map { it.get() }
            assertEquals(1, results.count { it.isSuccess })
            assertEquals("STALE_REVISION", (results.single { it.isFailure }.exceptionOrNull() as ApiException).code)
            assertEquals(1, rolls.get())
        } finally { pool.shutdownNow() }
    }

    @Test fun `late human and bot race cannot move twice`() {
        val room = start()
        now += 10_000
        val pool = Executors.newFixedThreadPool(2)
        try {
            val actions = listOf(Callable { runCatching { act(room, Command.Roll, "host") } },
                Callable { runCatching { act(room, Command.CheckTimeout) } })
            val results = pool.invokeAll(actions).map { it.get() }
            assertTrue(results[0].isFailure)
            assertTrue(results[1].isSuccess)
            assertEquals(1, rolls.get())
            assertEquals(room.revision + 1, api.get("host", room.code).revision)
        } finally { pool.shutdownNow() }
    }

    @Test fun `outsiders cannot trigger automatic actions and old matches remain untimed`() {
        var room = start()
        now += 10_000
        error("NOT_A_MEMBER") { act(room, Command.CheckTimeout, "stranger") }
        assertEquals(0, rolls.get())
        room = room.copy(actionTimeoutMillis = null, afkTimeoutMillis = null, actionDeadlineAtMillis = null)
        store.seed(room)
        now += 9_000_000
        error("UNTIMED_GAME") { act(room, Command.CheckTimeout) }
        assertEquals("ROLL", act(room, Command.Roll, "host").room.lastAction!!.type)
    }
}
