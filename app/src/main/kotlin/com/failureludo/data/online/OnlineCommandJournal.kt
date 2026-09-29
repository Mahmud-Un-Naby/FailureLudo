package com.failureludo.data.online

import com.failureludo.online.OnlineRoom
import com.failureludo.online.RoomCodec
import org.json.JSONObject
import java.util.UUID
import java.io.IOException

internal interface JournalStorage {
    fun read(): String?
    fun write(value: String)
}
internal data class PendingCommand(val uid: String, val origin: String, val path: String, val body: String)
internal data class JournalState(val uid: String, val origin: String, val room: OnlineRoom?, val pending: PendingCommand?)

/** Call under the session mutex. Every change is persisted before replacing memory. */
internal class OnlineCommandJournal(private val storage: JournalStorage) {
    var state: JournalState? = storage.read()?.let { raw ->
        val json = JSONObject(raw)
        require(json.getInt("version") == 1) { "Unsupported online recovery file" }
        val pending = json.optJSONObject("pending")?.let {
            PendingCommand(it.getString("uid"), it.getString("origin"), it.getString("path"), it.getString("body"))
        }
        JournalState(json.getString("uid"), json.getString("origin"),
            json.optJSONObject("room")?.let(RoomCodec::decode), pending)
    }
        private set

    fun bind(uid: String, origin: String) {
        val current = state
        check(current == null || (current.uid == uid && current.origin == origin) ||
            (current.room == null && current.pending == null)) {
            "This saved online session belongs to another identity or service. Restore that session to continue."
        }
        if (current == null || current.uid != uid || current.origin != origin) save(JournalState(uid, origin, null, null))
    }

    fun begin(path: String, body: JSONObject): PendingCommand {
        val current = requireNotNull(state)
        check(current.pending == null) { "Retry the pending action before sending another." }
        val pending = PendingCommand(current.uid, current.origin, path,
            JSONObject(body.toString()).put("requestId", UUID.randomUUID().toString()).toString())
        save(current.copy(pending = pending))
        return pending
    }

    fun accept(room: OnlineRoom, completesPending: Boolean = false) {
        val current = requireNotNull(state)
        val previous = current.room
        val next = if (previous?.code == room.code && previous.revision > room.revision) previous else room
        save(current.copy(room = next, pending = if (completesPending) null else current.pending))
    }

    fun completeExit() { save(requireNotNull(state).copy(room = null, pending = null)) }
    fun rejectPending() { save(requireNotNull(state).copy(pending = null)) }
    fun forgetRoom() {
        check(state?.pending == null)
        check(state?.room?.canForget(state?.uid) == true) { "Finish or resign from this game first." }
        save(requireNotNull(state).copy(room = null))
    }

    private fun save(next: JournalState) {
        val json = JSONObject().put("version", 1).put("uid", next.uid).put("origin", next.origin)
            .put("room", next.room?.let(RoomCodec::encode) ?: JSONObject.NULL)
            .put("pending", next.pending?.let { JSONObject().put("uid", it.uid).put("origin", it.origin)
                .put("path", it.path).put("body", it.body) } ?: JSONObject.NULL)
        storage.write(json.toString())
        state = next
    }
}

internal class OnlineApiException(val status: Int, val code: String, message: String) : IOException(message) {
    val isDefinitiveRejection: Boolean get() = status in listOf(400, 403, 404, 409, 410, 415)
}

/** An ambiguous transport result must never discard a request or create a new ID. */
internal suspend fun deliverPending(journal: OnlineCommandJournal,
    transport: suspend (PendingCommand) -> JSONObject): OnlineRoom {
    val pending = requireNotNull(journal.state?.pending)
    val response = try { transport(pending) }
    catch (error: OnlineApiException) {
        if (error.isDefinitiveRejection) journal.rejectPending()
        throw error
    }
    val room = RoomCodec.decode(response.getJSONObject("room"))
    if (JSONObject(pending.body).optString("type") == "LEAVE") journal.completeExit()
    else journal.accept(room, completesPending = true)
    return room
}
