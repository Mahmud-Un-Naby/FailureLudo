package com.failureludo.server

import com.failureludo.engine.*
import com.failureludo.online.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class ResignationTest {
    private val store = MemoryRoomStore()
    private var die = 6
    private val controller = GameController(store, { die }, { "ABCDEFGH" })
    private fun id() = UUID.randomUUID().toString()
    private fun started(count: Int = 4, mode: GameMode = GameMode.FREE_FOR_ALL): OnlineRoom {
        var room = controller.create("host", id(), "Host", count, mode).room
        for (i in 1 until count) room = controller.execute("guest$i", room.code, id(), null, Command.Join("Guest $i")).room
        return act(room, "host", Command.Start).room
    }
    private fun act(room: OnlineRoom, uid: String, command: Command, request: String = id()) =
        controller.execute(uid, room.code, request, room.revision, command)
    private fun error(code: String, action: () -> Unit) {
        assertEquals(code, assertThrows(ApiException::class.java) { action() }.code)
    }

    @Test fun `FFA resignation is permanent and surviving players continue`() {
        val initial = started(3)
        val resigned = act(initial, "host", Command.Resign).room
        assertEquals(initial.revision + 1, resigned.revision)
        assertEquals(RoomStatus.PLAYING, resigned.status)
        assertTrue(resigned.hasResigned("host"))
        assertTrue(resigned.canForget("host"))
        assertFalse(resigned.canResign("host"))
        assertNull(resigned.controllerUid(PlayerColor.RED))
        assertEquals("guest2", resigned.controllerUid(resigned.game!!.currentPlayer.color))
        assertEquals(resigned, controller.get("host", resigned.code)) // spectating remains allowed
        assertEquals(resigned, controller.execute("host", resigned.code, id(), null, Command.Join("Host")).room)
        error("PLAYER_RESIGNED") { act(resigned, "host", Command.Roll) }
        error("ALREADY_RESIGNED") { act(resigned, "host", Command.Resign) }
        val finished = act(resigned, "guest1", Command.Resign).room // out of turn
        assertEquals(RoomStatus.FINISHED, finished.status)
        assertEquals(listOf(PlayerId(3)), finished.game!!.winners)
        assertEquals(1, finished.lastAction!!.eventCount)
    }

    @Test fun `teammate takes over pending move and both colors turns without early pawn sharing`() {
        var room = started(mode = GameMode.TEAM)
        room = act(room, "host", Command.Roll).room
        val before = room.game
        room = act(room, "host", Command.Resign).room
        assertEquals(before, room.game)
        assertEquals("guest2", room.controllerUid(PlayerColor.RED))
        assertEquals("guest2", room.controllerUid(PlayerColor.YELLOW))
        assertEquals(0, room.lastAction!!.eventCount)
        error("PLAYER_RESIGNED") { act(room, "host", Command.Move(1, 0, false)) }
        error("NOT_YOUR_TURN") { act(room, "guest1", Command.Move(1, 0, false)) }
        error("ILLEGAL_MOVE") { act(room, "guest2", Command.Move(3, 0, false)) }
        room = act(room, "guest2", Command.Move(1, 0, false)).room
        die = 2
        room = act(room, "guest2", Command.Roll).room
        room = act(room, "guest2", Command.Move(1, 0, false)).room
        assertEquals(PlayerColor.GREEN, room.game!!.currentPlayer.color)
        room = act(room, "guest3", Command.Roll).room
        assertEquals(PlayerColor.YELLOW, room.game!!.currentPlayer.color)
        assertEquals(RoomStatus.PLAYING, act(room, "guest2", Command.Roll).room.status)
    }

    @Test fun `both teams can hand off then the second resignation on a team loses`() {
        var room = started(mode = GameMode.TEAM)
        room = act(room, "host", Command.Resign).room
        room = act(room, "guest1", Command.Resign).room
        assertEquals("guest3", room.controllerUid(PlayerColor.BLUE))
        assertEquals("guest3", room.controllerUid(PlayerColor.GREEN))
        val finished = act(room, "guest2", Command.Resign).room
        assertEquals(RoomStatus.FINISHED, finished.status)
        assertEquals(listOf(PlayerId(2), PlayerId(4)), finished.game!!.winners)
        error("NOT_PLAYING") { act(finished, "guest3", Command.Resign) }
    }

    @Test fun `resignation retry after lost response and further play never repeats the transition`() {
        val initial = started()
        val request = id()
        store.loseNextResponse = true
        error("STORE_TIMEOUT") { act(initial, "host", Command.Resign, request) }
        val resigned = controller.get("guest3", initial.code)
        val progressed = act(resigned, "guest3", Command.Roll).room
        val restarted = GameController(store, { fail("Retry must not roll"); 1 })
        val retry = restarted.execute("host", initial.code, request, initial.revision, Command.Resign)
        assertTrue(retry.duplicate)
        assertEquals(initial.revision + 1, retry.acceptedRevision)
        assertEquals(progressed, retry.room)
        error("REQUEST_ID_REUSED") { act(initial, "host", Command.Roll, request) }
    }

    @Test fun `resignation requires a playing room membership and the observed revision`() {
        val waiting = controller.create("host", id(), "Host", 2, GameMode.FREE_FOR_ALL).room
        error("NOT_PLAYING") { act(waiting, "host", Command.Resign) }
        val joined = controller.execute("guest1", waiting.code, id(), null, Command.Join("Guest")).room
        val playing = act(joined, "host", Command.Start).room
        error("NOT_A_MEMBER") { act(playing, "stranger", Command.Resign) }
        error("STALE_REVISION") { act(joined, "host", Command.Resign) }
        assertEquals(playing, controller.get("host", playing.code))
    }

    @Test fun `concurrent resignations commit only one observed revision`() {
        val initial = started(2)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = pool.invokeAll(listOf("host", "guest1").map { uid -> Callable {
                runCatching { act(initial, uid, Command.Resign) }
            } }).map { it.get() }
            assertEquals(1, results.count { it.isSuccess })
            assertEquals("STALE_REVISION", (results.single { it.isFailure }.exceptionOrNull() as ApiException).code)
            assertEquals(1, controller.get("host", initial.code).members.count { it.resigned })
        } finally { pool.shutdownNow() }
    }
}
