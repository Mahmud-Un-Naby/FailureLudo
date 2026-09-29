package com.failureludo.server

import com.failureludo.online.*

import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

class ApiHandlerTest {
    private lateinit var server: HttpServer
    private val client = HttpClient.newHttpClient()
    private fun id() = UUID.randomUUID().toString()
    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/", ApiHandler(GameController(MemoryRoomStore(), { 6 }, { "ABCDEFGH" }), TokenVerifier {
            if (it !in listOf("host", "guest")) reject(401, "UNAUTHENTICATED", "Invalid test token")
            it
        }))
        server.start()
    }
    @After fun stop() { server.stop(0) }

    private fun request(path: String, body: String? = null, token: String? = "host", contentType: String = "application/json"): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${server.address.port}$path"))
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (body != null) builder.header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(body)) else builder.GET()
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }
    private fun createBody() = JSONObject().put("requestId", id()).put("name", "Guest Host")
        .put("maxPlayers", 2).put("mode", "FREE_FOR_ALL")
    private fun command(type: String, revision: Long = 0) = JSONObject().put("requestId", id())
        .put("type", type).put("expectedRevision", revision)

    @Test fun `health is public but all room endpoints require verified identity`() {
        assertEquals(200, request("/healthz", token = null).statusCode())
        assertEquals(401, request("/v1/rooms", createBody().toString(), null).statusCode())
        assertEquals(401, request("/v1/rooms", createBody().toString(), "forged").statusCode())
        assertEquals(401, request("/v1/rooms/ABCDEFGH", token = "forged").statusCode())
    }
    @Test fun `HTTP guest flow returns confirmed roll and accepts pawn intention`() {
        assertEquals(200, request("/v1/rooms", createBody().toString()).statusCode())
        val path = "/v1/rooms/ABCDEFGH/commands"
        val join = JSONObject().put("requestId", id()).put("type", "JOIN").put("name", "Guest")
        assertEquals(200, request(path, join.toString(), "guest").statusCode())
        assertEquals(200, request(path, command("START", 1).toString()).statusCode())
        val roll = command("ROLL", 2).toString()
        val rolled = request(path, roll)
        assertEquals(200, rolled.statusCode())
        assertEquals(6, JSONObject(rolled.body()).getJSONObject("room").getJSONObject("lastAction").getInt("dice"))
        assertTrue(JSONObject(request(path, roll).body()).getBoolean("duplicate"))
        val move = command("MOVE", 3).put("playerId", 1).put("pieceId", 0).put("deferHomeEntry", false)
        assertEquals(200, request(path, move.toString()).statusCode())
        assertEquals(4L, JSONObject(request("/v1/rooms/ABCDEFGH", token = "guest").body()).getJSONObject("room").getLong("revision"))
    }
    @Test fun `HTTP leave returns a private acknowledgement and revokes room reads`() {
        assertEquals(200, request("/v1/rooms", createBody().toString()).statusCode())
        val path = "/v1/rooms/ABCDEFGH/commands"
        val body = command("LEAVE", 0).toString()
        val response = request(path, body)
        assertEquals(200, response.statusCode())
        val room = JSONObject(response.body()).getJSONObject("room")
        assertEquals("CLOSED", room.getString("status"))
        assertEquals(0, room.getJSONArray("members").length())
        assertEquals(403, request("/v1/rooms/ABCDEFGH").statusCode())
        assertTrue(JSONObject(request(path, body).body()).getBoolean("duplicate"))
    }

    @Test fun `storage error causes stay internal to the server`() {
        val cause = IllegalStateException("private storage diagnostic")
        val failure = ApiException(503, "STORE_UNAVAILABLE", "Storage unavailable. Retry with the same request ID.", cause)
        val unavailable = object : RoomStore {
            override fun get(code: String): OnlineRoom? = throw failure
            override fun execute(requestKey: String, fingerprint: String, code: String,
                                 change: (OnlineRoom?) -> OnlineRoom): CommandResult = throw failure
        }
        server.removeContext("/")
        server.createContext("/", ApiHandler(GameController(unavailable), TokenVerifier { "host" }))
        val response = request("/v1/rooms/ABCDEFGH")
        assertEquals(503, response.statusCode())
        val body = JSONObject(response.body())
        assertEquals(setOf("error", "message"), body.keySet())
        assertEquals("STORE_UNAVAILABLE", body.getString("error"))
        assertEquals(failure.message, body.getString("message"))
        assertFalse(response.body().contains("private storage diagnostic"))
        assertSame(cause, failure.cause)
    }

    @Test fun `caller cannot inject UID dice or replacement state`() {
        for (field in listOf("uid", "diceValue", "gameState")) {
            val response = request("/v1/rooms", createBody().put(field, "forged").toString())
            assertEquals(400, response.statusCode())
            assertEquals("UNKNOWN_FIELD", JSONObject(response.body()).getString("error"))
        }
        assertEquals(400, request("/v1/rooms/ABCDEFGH/commands", command("ROLL").put("diceValue", 6).toString()).statusCode())
    }
    @Test fun `invalid JSON oversized bodies and fractional numbers are rejected`() {
        assertEquals(400, request("/v1/rooms", "{").statusCode())
        assertEquals(400, request("/v1/rooms", "{} {}").statusCode())
        assertEquals(413, request("/v1/rooms", " ".repeat(4097)).statusCode())
        assertEquals(415, request("/v1/rooms", "{}", contentType = "text/plain").statusCode())
        assertEquals(400, request("/v1/rooms", createBody().put("maxPlayers", 2.5).toString()).statusCode())
        assertEquals(400, request("/v1/rooms/ABCDEFGH/commands", command("ROLL").put("expectedRevision", -1).toString()).statusCode())
    }
}
