package com.failureludo.server

import com.failureludo.online.*

import com.failureludo.engine.GameMode
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Explicit integrationTest task only. Refuses to run against a real project. */
class FirestoreIntegrationTest {
    private lateinit var db: Firestore
    private lateinit var host: String
    private fun id() = UUID.randomUUID().toString()
    @Before fun connect() {
        host = System.getenv("FIRESTORE_EMULATOR_HOST").orEmpty()
        require(host.matches(Regex("(127\\.0\\.0\\.1|localhost):[0-9]+"))) {
            "Run integrationTest through firebase emulators:exec with a loopback Firestore emulator"
        }
        // NoCredentials or a host containing "localhost" selects the SDK's legacy
        // unauthenticated channel, bypassing emulator owner credentials. Use the
        // numeric loopback address and emulator-only credentials; never load ADC.
        host = "127.0.0.1:${host.substringAfter(':')}"
        val options = FirestoreOptions.newBuilder().setProjectId(PROJECT)
            .setHost(host).setEmulatorHost(host)
            .setCredentials(FirestoreOptions.EmulatorCredentials()).build()
        check(options.host == host && options.emulatorHost == host)
        db = options.service
    }
    @After fun close() { if (::db.isInitialized) db.close() }

    @Test fun `independent controllers serialize concurrent joins and rolls with durable receipts`() {
        val first = GameController(FirestoreRoomStore(db), { 6 }, nowMillis = { 1_000_000L })
        val second = GameController(FirestoreRoomStore(db), { 2 }, nowMillis = { 1_000_000L })
        var room = first.create("host", id(), "Host", 2, GameMode.FREE_FOR_ALL).room
        val code = room.code
        val pool = Executors.newFixedThreadPool(2)
        try {
            val joins = pool.invokeAll((1..2).map { i -> Callable {
                runCatching { second.execute("guest$i", code, id(), null, Command.Join("Guest")) }
            } }).map { it.get() }
            assertEquals(1, joins.count { it.isSuccess })
            assertEquals("ROOM_FULL", (joins.single { it.isFailure }.exceptionOrNull() as ApiException).code)
            room = first.get("host", code)
            room = first.execute("host", code, id(), room.revision, Command.Start).room
            val revision = room.revision
            val request = id()
            val rolls = pool.invokeAll(listOf(first, second).map { api -> Callable {
                api.execute("host", code, request, revision, Command.Roll)
            } }).map { it.get() }
            assertEquals(1, rolls.count { it.duplicate })
            assertEquals(rolls[0].room, rolls[1].room)
            assertEquals(revision + 1, rolls[0].room.revision)
            val restarted = GameController(FirestoreRoomStore(db), { fail("Retry rerolled"); 1 })
            assertEquals(rolls[0].room, restarted.execute("host", code, request, revision, Command.Roll).room)
            assertEquals("STALE_REVISION", assertThrows(ApiException::class.java) {
                first.execute("host", code, id(), revision, Command.Roll)
            }.code)
            assertEquals("REQUEST_ID_REUSED", assertThrows(ApiException::class.java) {
                first.execute("host", code, request, revision, Command.Start)
            }.code)
        } finally { pool.shutdownNow() }
    }

    @Test fun `rules allow member snapshots but deny outsiders client writes and receipts`() {
        val api = GameController(FirestoreRoomStore(db))
        val room = api.create("host", id(), "Host", 2, GameMode.FREE_FOR_ALL).room
        val path = "authoritativeRooms/${room.code}"
        assertEquals(200, rest("GET", path, "host"))
        assertEquals(403, rest("GET", path, "stranger"))
        assertEquals(403, rest("GET", path, null))
        assertEquals(403, rest("PATCH", path, "host"))
        assertEquals(403, rest("DELETE", path, "host"))
        assertEquals(403, rest("PATCH", "authoritativeRooms/ZZZZZZZZ", "host"))
        assertEquals(403, rest("GET", "authoritativeRequests/anything", "host"))
        assertEquals(403, rest("PATCH", "authoritativeRequests/anything", "host"))
        assertEquals(403, rest("GET", "rooms/legacy", "host"))
        assertEquals(403, rest("PATCH", "rooms/legacy", "host"))
        assertEquals(403, rest("PATCH", "rooms/legacy/moves/0", "host"))
    }

    @Test fun `leaving revokes snapshot access and receipts stay private after restart`() {
        val api = GameController(FirestoreRoomStore(db))
        val createRequest = id()
        var room = api.create("host", createRequest, "Host", 2, GameMode.FREE_FOR_ALL).room
        val code = room.code
        val path = "authoritativeRooms/$code"
        room = api.execute("guest", code, id(), null, Command.Join("Guest")).room
        assertEquals(200, rest("GET", path, "host"))
        assertEquals(200, rest("GET", path, "guest"))
        val revision = room.revision
        val leaveRequest = id()
        val left = api.execute("host", code, leaveRequest, revision, Command.Leave)
        assertTrue(left.room.members.isEmpty())
        assertEquals(403, rest("GET", path, "host"))
        assertEquals(200, rest("GET", path, "guest"))
        assertEquals("guest", api.get("guest", code).hostUid)
        room = api.execute("newcomer", code, id(), null, Command.Join("New guest")).room
        val restarted = GameController(FirestoreRoomStore(db))
        val retry = restarted.execute("host", code, leaveRequest, revision, Command.Leave)
        assertTrue(retry.duplicate)
        assertTrue(retry.room.members.isEmpty())
        assertNull(retry.room.game)
        assertEquals(left.acceptedRevision, retry.acceptedRevision)
        assertEquals("NOT_A_MEMBER", assertThrows(ApiException::class.java) {
            restarted.create("host", createRequest, "Host", 2, GameMode.FREE_FOR_ALL)
        }.code)
        assertEquals(room, restarted.get("guest", code))
    }

    @Test fun `team resignation persists handoff and retries after another controller moves`() {
        val api = GameController(FirestoreRoomStore(db), { 6 }, nowMillis = { 1_000_000L })
        var room = api.create("host", id(), "Host", 4, GameMode.TEAM).room
        val code = room.code
        for (i in 1..3) room = api.execute("guest$i", code, id(), null, Command.Join("Guest $i")).room
        room = api.execute("host", code, id(), room.revision, Command.Start).room
        room = api.execute("host", code, id(), room.revision, Command.Roll).room
        val revision = room.revision
        val request = id()
        val resigned = api.execute("host", code, request, revision, Command.Resign)
        val restarted = GameController(FirestoreRoomStore(db), { fail("Must reuse the pending dice"); 1 }, nowMillis = { 1_000_000L })
        room = restarted.get("guest2", code)
        assertTrue(room.hasResigned("host"))
        assertEquals("guest2", room.controllerUid(com.failureludo.engine.PlayerColor.RED))
        assertEquals(200, rest("GET", "authoritativeRooms/$code", "host")) // resigned member can watch
        assertEquals(403, rest("PATCH", "authoritativeRooms/$code", "host"))
        assertEquals("PLAYER_RESIGNED", assertThrows(ApiException::class.java) {
            restarted.execute("host", code, id(), room.revision, Command.Move(1, 0, false))
        }.code)
        val moved = restarted.execute("guest2", code, id(), room.revision, Command.Move(1, 0, false)).room
        val retry = api.execute("host", code, request, revision, Command.Resign)
        assertTrue(retry.duplicate)
        assertEquals(resigned.acceptedRevision, retry.acceptedRevision)
        assertEquals(moved, retry.room)
    }

    @Test fun `concurrent bot checks persist one roll and AFK survives a new controller`() {
        val clock = java.util.concurrent.atomic.AtomicLong(1_000_000L)
        val first = GameController(FirestoreRoomStore(db), { 6 }, nowMillis = clock::get)
        val second = GameController(FirestoreRoomStore(db), { 6 }, nowMillis = clock::get)
        var room = first.create("host", id(), "Host", 2, GameMode.FREE_FOR_ALL).room
        val code = room.code
        room = first.execute("guest", code, id(), null, Command.Join("Guest")).room
        room = first.execute("host", code, id(), room.revision, Command.Start).room
        val initial = room
        val request = id()
        clock.addAndGet(10_000)
        val pool = Executors.newFixedThreadPool(2)
        val results = try {
            pool.invokeAll(listOf(first, second).map { api -> Callable {
                api.execute("guest", code, request, initial.revision, Command.CheckTimeout)
            } }).map { it.get() }
        } finally { pool.shutdownNow() }
        assertEquals(1, results.count { it.duplicate })
        assertEquals(results[0].room, results[1].room)
        room = second.get("host", code)
        assertEquals("BOT_ROLL", room.lastAction!!.type)
        assertEquals(1_000_000L, room.members.first().afkSinceMillis)
        assertEquals(1_020_000L, room.actionDeadlineAtMillis)
        assertEquals(403, rest("PATCH", "authoritativeRooms/$code", "guest"))
        val restarted = GameController(FirestoreRoomStore(db), { fail("Bot move must use persisted roll"); 1 }, nowMillis = clock::get)
        clock.addAndGet(10_000)
        room = restarted.execute("guest", code, id(), room.revision, Command.CheckTimeout).room
        assertEquals("BOT_MOVE", room.lastAction!!.type)
        assertEquals(1_000_000L, room.members.first().afkSinceMillis)
        val retry = first.execute("guest", code, request, initial.revision, Command.CheckTimeout)
        assertTrue(retry.duplicate)
        assertEquals(initial.revision + 1, retry.acceptedRevision)
        assertEquals(room, retry.room)
        clock.set(1_120_000L)
        room = restarted.execute("guest", code, id(), room.revision, Command.CheckTimeout).room
        assertTrue(room.hasResigned("host"))
        assertEquals(RoomStatus.FINISHED, room.status)
    }

    private fun rest(method: String, document: String, uid: String?): Int {
        val request = HttpRequest.newBuilder(URI("http://$host/v1/projects/$PROJECT/databases/(default)/documents/$document"))
        if (uid != null) request.header("Authorization", "Bearer ${emulatorToken(uid)}")
        if (method == "PATCH") request.header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString("{\"fields\":{}}"))
        else request.method(method, HttpRequest.BodyPublishers.noBody())
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode()
    }

    private fun emulatorToken(uid: String): String {
        fun base64(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
        val now = System.currentTimeMillis() / 1000
        val claims = JSONObject().put("sub", uid).put("user_id", uid).put("aud", PROJECT)
            .put("iss", "https://securetoken.google.com/$PROJECT").put("iat", now).put("exp", now + 3600)
            .put("auth_time", now).put("firebase", JSONObject().put("sign_in_provider", "anonymous"))
        // Unsigned tokens are accepted only by the local emulator, never production.
        return "${base64("{\"alg\":\"none\",\"typ\":\"JWT\"}")}.${base64(claims.toString())}."
    }

    companion object { private const val PROJECT = "demo-failure-ludo-server" }
}
