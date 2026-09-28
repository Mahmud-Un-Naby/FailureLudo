package com.failureludo.server

import com.failureludo.online.*

import org.json.JSONObject

/** Test-only transactional fake; production always persists through Firestore. */
class MemoryRoomStore : RoomStore {
    private data class Receipt(val fingerprint: String, val code: String, val revision: Long)
    private val rooms = mutableMapOf<String, OnlineRoom>()
    private val receipts = mutableMapOf<String, Receipt>()
    var retryCallback = false
    var loseNextResponse = false

    @Synchronized override fun get(code: String): OnlineRoom? = rooms[code]?.let(::copy)
    @Synchronized fun seed(room: OnlineRoom) { rooms[room.code] = copy(room) }

    @Synchronized override fun execute(requestKey: String, fingerprint: String, code: String,
        change: (OnlineRoom?) -> OnlineRoom): CommandResult {
        receipts[requestKey]?.let {
            if (it.fingerprint != fingerprint) reject(409, "REQUEST_ID_REUSED", "Request ID reused")
            return CommandResult(requireNotNull(get(it.code)), it.revision, true)
        }
        if (retryCallback) change(get(code))
        val next = change(get(code))
        rooms[code] = copy(next)
        receipts[requestKey] = Receipt(fingerprint, code, next.revision)
        if (loseNextResponse) {
            loseNextResponse = false
            reject(503, "STORE_TIMEOUT", "Simulated lost response after commit")
        }
        return CommandResult(copy(next), next.revision, false)
    }

    private fun copy(room: OnlineRoom) = RoomCodec.decode(JSONObject(RoomCodec.encode(room).toString()))
}
