package com.failureludo.server

import com.failureludo.online.*

import com.failureludo.engine.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class GameControllerTest {
    private val store = MemoryRoomStore()
    private var die = 6
    private val rolls = AtomicInteger()
    private val controller = GameController(store, { rolls.incrementAndGet(); die }, { "ABCDEFGH" })
    private fun id() = UUID.randomUUID().toString()
    private fun room(mode: GameMode = GameMode.FREE_FOR_ALL, count: Int = 2): OnlineRoom {
        var room = controller.create("host", id(), "Host", count, mode).room
        for (i in 1 until count) room = controller.execute("guest$i", room.code, id(), null, Command.Join("Guest $i")).room
        return controller.execute("host", room.code, id(), room.revision, Command.Start).room
    }
    private fun act(room: OnlineRoom, command: Command, uid: String = "host", request: String = id()) =
        controller.execute(uid, room.code, request, room.revision, command)
    private fun error(code: String, action: () -> Unit) {
        val failure = assertThrows(ApiException::class.java) { action() }
        assertEquals(code, failure.code)
    }

    @Test fun `only host starts and only members read or act`() {
        val waiting = controller.create("host", id(), "Host", 2, GameMode.FREE_FOR_ALL).room
        error("NEED_PLAYERS") { act(waiting, Command.Start) }
        val joined = controller.execute("guest1", waiting.code, id(), null, Command.Join("Guest")).room
        error("HOST_REQUIRED") { act(joined, Command.Start, "guest1") }
        error("NOT_A_MEMBER") { controller.get("stranger", joined.code) }
        error("NOT_A_MEMBER") { act(joined, Command.Roll, "stranger") }
        val started = act(joined, Command.Start).room
        error("NOT_YOUR_TURN") { act(started, Command.Roll, "guest1") }
        assertEquals(0, rolls.get())
    }

    @Test fun `retries across server instances return receipt without rerolling`() {
        val initial = room()
        val request = id()
        store.retryCallback = true
        val result = act(initial, Command.Roll, request = request)
        assertEquals(1, rolls.get())
        val otherServer = GameController(store, { fail("Duplicate rolled again"); 1 })
        val duplicate = otherServer.execute("host", initial.code, request, initial.revision, Command.Roll)
        assertTrue(duplicate.duplicate)
        assertEquals(result.room, duplicate.room)
        assertEquals(result.acceptedRevision, duplicate.acceptedRevision)
        error("REQUEST_ID_REUSED") { act(initial, Command.Start, request = request) }
    }

    @Test fun `lost committed response recovers with same request ID`() {
        val initial = room()
        val request = id()
        store.loseNextResponse = true
        error("STORE_TIMEOUT") { act(initial, Command.Roll, request = request) }
        val recovered = act(initial, Command.Roll, request = request)
        assertTrue(recovered.duplicate)
        assertEquals(initial.revision + 1, recovered.room.revision)
        assertEquals(1, rolls.get())
    }

    @Test fun `duplicate returns latest snapshot and original receipt revision`() {
        val initial = room()
        val request = id()
        val rolled = act(initial, Command.Roll, request = request)
        val moved = act(rolled.room, Command.Move(1, 0, false))
        val duplicate = act(initial, Command.Roll, request = request)
        assertEquals(moved.room, duplicate.room)
        assertEquals(rolled.acceptedRevision, duplicate.acceptedRevision)
        error("STALE_REVISION") { act(initial, Command.Roll) }
    }

    @Test fun `concurrent rolls accept exactly one revision`() {
        val initial = room()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = pool.invokeAll(List(2) { Callable {
                runCatching { act(initial, Command.Roll) }
            } }).map { it.get() }
            assertEquals(1, results.count { it.isSuccess })
            assertEquals("STALE_REVISION", (results.single { it.isFailure }.exceptionOrNull() as ApiException).code)
            assertEquals(1, rolls.get())
        } finally { pool.shutdownNow() }
    }

    @Test fun `concurrent joins cannot overfill and rejoin does not duplicate member`() {
        val created = controller.create("host", id(), "Host", 2, GameMode.FREE_FOR_ALL).room
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = pool.invokeAll((1..2).map { i -> Callable {
                runCatching { controller.execute("guest$i", created.code, id(), null, Command.Join("Guest")) }
            } }).map { it.get() }
            assertEquals(1, results.count { it.isSuccess })
            val joined = results.single { it.isSuccess }.getOrThrow().room
            val member = joined.members.last()
            val repeated = controller.execute(member.uid, created.code, id(), null, Command.Join(member.name))
            assertEquals(joined, repeated.room)
            assertEquals("ROOM_FULL", (results.single { it.isFailure }.exceptionOrNull() as ApiException).code)
        } finally { pool.shutdownNow() }
    }

    @Test fun `no legal move advances immediately and retains rolled result`() {
        val initial = room()
        die = 2
        val after = act(initial, Command.Roll).room
        assertEquals(TurnPhase.WAITING_FOR_ROLL, after.game!!.turnPhase)
        assertEquals(PlayerColor.BLUE, after.game!!.currentPlayer.color)
        assertEquals(2, after.lastAction!!.dice)
    }

    @Test fun `third six forfeits without accepting another pawn move`() {
        var current = room()
        repeat(2) {
            current = act(current, Command.Roll).room
            current = act(current, Command.Move(1, it, false)).room
        }
        current = act(current, Command.Roll).room
        assertEquals(PlayerColor.BLUE, current.game!!.currentPlayer.color)
        assertTrue(current.game!!.eventLog.any { it is GameEvent.ConsecutiveSixesForfeit })
        error("NOT_YOUR_TURN") { act(current, Command.Move(1, 2, false)) }
    }

    @Test fun `invalid pawn and wrong phase leave persisted state unchanged`() {
        val initial = room()
        error("WRONG_PHASE") { act(initial, Command.Move(1, 0, false)) }
        val rolled = act(initial, Command.Roll).room
        error("WRONG_PHASE") { act(rolled, Command.Roll) }
        error("ILLEGAL_MOVE") { act(rolled, Command.Move(2, 0, false)) }
        error("ILLEGAL_MOVE") { act(rolled, Command.Move(1, 9, false)) }
        assertEquals(rolled, controller.get("host", initial.code))
    }

    @Test fun `home entry choice is applied by shared engine`() {
        val initial = room()
        val game = initial.game!!
        val red = game.players[0]
        val prepared = game.copy(players = game.players.map {
            if (it.color == PlayerColor.RED) red.copy(pieces = red.pieces.map { piece ->
                if (piece.id == 0) piece.copy(position = PiecePosition.MainTrack(50)) else piece
            }) else it
        })
        store.seed(initial.copy(game = prepared))
        die = 1
        val rolled = act(initial, Command.Roll).room
        val moved = act(rolled, Command.Move(1, 0, true)).room
        assertEquals(PiecePosition.MainTrack(51), moved.game!!.players[0].pieces[0].position)
    }

    @Test fun `team mode requires four players and retains engine sharing rules`() {
        error("INVALID_PLAYERS") { controller.create("host", id(), "Host", 2, GameMode.TEAM) }
        val created = controller.create("host", id(), "Host", 4, GameMode.TEAM).room
        val joined = controller.execute("guest1", created.code, id(), null, Command.Join("Guest")).room
        error("NEED_PLAYERS") { act(joined, Command.Start) }
    }

    @Test fun `create retry is idempotent even with a new candidate room code`() {
        val request = id()
        val first = controller.create("host", request, "Host", 2, GameMode.FREE_FOR_ALL)
        val other = GameController(store, newCode = { "ZZZZZZZZ" })
        val retry = other.create("host", request, "Host", 2, GameMode.FREE_FOR_ALL)
        assertEquals(first.room, retry.room)
        assertNull(store.get("ZZZZZZZZ"))
    }

    @Test fun `host leave transfers authority and last leave closes room without reusing code`() {
        val created = controller.create("host", id(), "Host", 2, GameMode.FREE_FOR_ALL).room
        val joined = controller.execute("guest1", created.code, id(), null, Command.Join("Guest")).room
        val request = id()
        val exit = act(joined, Command.Leave, request = request).room
        assertEquals(RoomStatus.CLOSED, exit.status)
        assertTrue(exit.members.isEmpty())
        val left = controller.get("guest1", created.code)
        assertEquals("guest1", left.hostUid)
        assertEquals(1, left.members.size)
        assertEquals(exit, act(joined, Command.Leave, request = request).room)
        error("NOT_A_MEMBER") { controller.get("host", created.code) }
        val closed = act(left, Command.Leave, "guest1").room
        assertEquals(RoomStatus.CLOSED, closed.status)
        error("ALREADY_STARTED") { controller.execute("late", created.code, id(), null, Command.Join("Late")) }
    }

    @Test fun `old receipts cannot expose snapshots after membership ends`() {
        val createId = id()
        val created = controller.create("host", createId, "Host", 2, GameMode.FREE_FOR_ALL).room
        val joined = controller.execute("guest1", created.code, id(), null, Command.Join("Guest")).room
        val leaveId = id()
        act(joined, Command.Leave, request = leaveId)
        val next = controller.execute("new-player", created.code, id(), null, Command.Join("New")).room
        val playing = act(next, Command.Start, "guest1").room
        error("NOT_A_MEMBER") { controller.create("host", createId, "Host", 2, GameMode.FREE_FOR_ALL) }
        val repeatedExit = act(joined, Command.Leave, request = leaveId).room
        assertEquals(joined.revision + 1, repeatedExit.revision)
        assertNull(repeatedExit.game)
        assertTrue(repeatedExit.members.isEmpty())
        assertEquals(playing, controller.get("guest1", playing.code))
    }

    @Test fun `leave racing with start does not remove a playing seat`() {
        val started = room()
        error("ALREADY_STARTED") { act(started, Command.Leave) }
        assertEquals(started, controller.get("host", started.code))
    }

    @Test fun `complete free for all and team games survive snapshot round trips`() {
        for (mode in GameMode.entries) {
            val memory = MemoryRoomStore()
            val random = Random(20260928)
            val api = GameController(memory, { random.nextInt(1, 7) }, { "ABCDEFGH" })
            var current = api.create("p0", id(), "Player 0", 4, mode).room
            for (i in 1..3) current = api.execute("p$i", current.code, id(), null, Command.Join("Player $i")).room
            current = api.execute("p0", current.code, id(), current.revision, Command.Start).room
            var actions = 0
            while (current.status == RoomStatus.PLAYING && actions++ < 10000) {
                val state = current.game!!
                val actor = current.members.single { it.color == state.currentPlayer.color }.uid
                val command = if (state.turnPhase == TurnPhase.WAITING_FOR_ROLL) Command.Roll else {
                    val piece = state.movablePieces.random(random)
                    Command.Move(state.players.single { it.color == piece.color }.id.value, piece.id, false)
                }
                current = api.execute(actor, current.code, id(), current.revision, command).room
                assertTrue(current.game!!.eventLog.size <= 32)
                val count = current.lastAction!!.eventCount
                assertTrue(count in 0..32)
                if (command is Command.Move) assertTrue(count > 0)
            }
            assertEquals("Game did not finish: $mode", RoomStatus.FINISHED, current.status)
            assertTrue(current.game!!.winners!!.isNotEmpty())
            error("NOT_PLAYING") { api.execute("p0", current.code, id(), current.revision, Command.Roll) }
        }
    }
}
