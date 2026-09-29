package com.failureludo.data.online

import com.failureludo.engine.GameMode
import com.failureludo.engine.PlayerColor
import com.failureludo.online.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class OnlineCommandJournalTest {
    private class Storage : JournalStorage {
        var raw: String? = null
        var failWrites = false
        override fun read() = raw
        override fun write(value: String) { if (failWrites) throw IOException("Disk full"); raw = value }
    }
    private val storage = Storage()
    private val origin = "https://example.run.app"
    private fun journal() = OnlineCommandJournal(storage).apply { bind("guest", origin) }
    private fun room(revision: Long = 2) = OnlineRoom("ABCDEFGH", "guest", 2, GameMode.FREE_FOR_ALL,
        listOf(Member("guest", "Guest", PlayerColor.RED)), revision)
    private fun roll(journal: OnlineCommandJournal) = journal.begin("/v1/rooms/ABCDEFGH/commands",
        JSONObject().put("type", "ROLL").put("expectedRevision", 2))
    private fun response(revision: Long = 3) = JSONObject().put("room", RoomCodec.encode(room(revision)))

    @Test fun `lost response then process death retries the exact same request`() = runBlocking {
        val first = journal()
        first.accept(room())
        val pending = roll(first)
        try { deliverPending(first) { throw IOException("Lost after commit") }; fail("Expected network failure") }
        catch (_: IOException) { }
        val restored = journal()
        assertEquals(pending, restored.state!!.pending)
        deliverPending(restored) { request -> assertEquals(pending, request); response() }
        assertNull(journal().state!!.pending)
        assertEquals(3, journal().state!!.room!!.revision)
    }

    @Test fun `ambiguous errors keep receipt while definitive errors allow refreshed action`() = runBlocking {
        for (status in listOf(401, 408, 429, 500, 503)) {
            val journal = journal()
            if (journal.state!!.pending == null) roll(journal)
            val pending = journal.state!!.pending
            try { deliverPending(journal) { throw OnlineApiException(status, "ERROR", "Retry") } } catch (_: OnlineApiException) { }
            assertEquals(pending, journal().state!!.pending)
        }
        for (status in listOf(400, 403, 404, 409, 410, 415)) {
            val journal = journal()
            if (journal.state!!.pending == null) roll(journal)
            try { deliverPending(journal) { throw OnlineApiException(status, "ERROR", "Rejected") } } catch (_: OnlineApiException) { }
            assertNull(journal().state!!.pending)
        }
    }

    @Test fun `invalid or incompatible response never loses pending request`() = runBlocking {
        val journal = journal()
        val pending = roll(journal)
        try { deliverPending(journal) { JSONObject("{}") } } catch (_: Exception) { }
        assertEquals(pending, journal().state!!.pending)
        try { deliverPending(journal) { response().apply { getJSONObject("room").put("protocolVersion", 999) } } }
        catch (_: UnsupportedRoomVersion) { }
        assertEquals(pending, journal().state!!.pending)
    }

    @Test fun `newer listener revision wins over delayed HTTP confirmation`() = runBlocking {
        val journal = journal()
        journal.accept(room())
        roll(journal)
        journal.accept(room(5))
        deliverPending(journal) { response(3) }
        assertEquals(5, journal().state!!.room!!.revision)
        assertNull(journal.state!!.pending)
    }

    @Test fun `pending request locks other actions and stays bound to identity and service`() {
        val journal = journal()
        roll(journal)
        assertThrows(IllegalStateException::class.java) { roll(journal) }
        assertThrows(IllegalStateException::class.java) { journal.bind("other", origin) }
        assertThrows(IllegalStateException::class.java) { journal.bind("guest", "https://other.run.app") }
    }

    @Test fun `failed durable write never changes memory or permits unsafe retry`() = runBlocking {
        val journal = journal()
        journal.accept(room())
        storage.failWrites = true
        assertThrows(IOException::class.java) { roll(journal) }
        assertNull(journal.state!!.pending)
        storage.failWrites = false
        val pending = roll(journal)
        storage.failWrites = true
        try { deliverPending(journal) { response() } } catch (_: IOException) { }
        assertEquals(pending, journal.state!!.pending)
        storage.failWrites = false
        assertEquals(pending, journal().state!!.pending)
    }

    @Test fun `confirmed leave clears room and pending together after restart`() = runBlocking {
        val journal = journal()
        journal.accept(room())
        journal.begin("/v1/rooms/ABCDEFGH/commands", JSONObject().put("type", "LEAVE").put("expectedRevision", 2))
        deliverPending(journal) { response() }
        assertNull(journal().state!!.room)
        assertNull(journal().state!!.pending)
    }

    @Test fun `resignation survives response loss and blocks new room until confirmation`() = runBlocking {
        val first = journal()
        first.accept(room())
        assertThrows(IllegalStateException::class.java) { first.forgetRoom() }
        val pending = first.begin("/v1/rooms/ABCDEFGH/commands", JSONObject().put("type", "RESIGN").put("expectedRevision", 2))
        try { deliverPending(first) { throw IOException("Lost after commit") } } catch (_: IOException) { }
        val restored = journal()
        assertEquals(pending, restored.state!!.pending)
        val resigned = room(3).copy(status = RoomStatus.PLAYING,
            members = room().members.map { it.copy(resigned = true) })
        restored.accept(resigned) // listener can observe acceptance before HTTP retry
        assertThrows(IllegalStateException::class.java) { restored.forgetRoom() }
        deliverPending(restored) { request ->
            assertEquals(pending, request)
            JSONObject().put("room", RoomCodec.encode(resigned))
        }
        assertNull(journal().state!!.pending)
        assertTrue(journal().state!!.room!!.hasResigned("guest"))
        restored.forgetRoom()
        assertNull(journal().state!!.room)
    }

    @Test fun `legacy saved room upgrades while retaining the exact pending request`() {
        val first = journal()
        first.accept(room())
        val pending = roll(first)
        val raw = JSONObject(storage.raw!!)
        val legacy = raw.getJSONObject("room").put("protocolVersion", 1).put("rulesVersion", "2026-09-23")
        legacy.getJSONArray("members").getJSONObject(0).remove("resigned")
        storage.raw = raw.toString()
        val restored = journal()
        assertEquals(room(), restored.state!!.room)
        assertEquals(pending, restored.state!!.pending)
        restored.accept(room(3))
        assertEquals(PROTOCOL_VERSION, JSONObject(storage.raw!!).getJSONObject("room").getInt("protocolVersion"))
        assertEquals(pending, journal().state!!.pending)
    }

    @Test fun `corrupt recovery data is surfaced instead of silently discarding a pending move`() {
        storage.raw = "{broken"
        assertThrows(Exception::class.java) { journal() }
    }
}
