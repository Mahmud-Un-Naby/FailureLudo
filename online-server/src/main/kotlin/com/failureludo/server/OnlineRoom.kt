package com.failureludo.server

import com.failureludo.online.*

import com.failureludo.online.*

sealed interface Command {
    data class Join(val name: String) : Command
    data object Start : Command
    data object Roll : Command
    data object Leave : Command
    data class Move(val playerId: Int, val pieceId: Int, val deferHomeEntry: Boolean) : Command
}

class ApiException(val status: Int, val code: String, message: String) : RuntimeException(message)
internal fun reject(status: Int, code: String, message: String): Nothing =
    throw ApiException(status, code, message)

data class CommandResult(val room: OnlineRoom, val acceptedRevision: Long, val duplicate: Boolean)

/** The room and request receipt MUST commit atomically. Callbacks may be retried.
 * Duplicate keys with the same fingerprint return the current room and original
 * accepted revision; different fingerprints fail with REQUEST_ID_REUSED.
 */
interface RoomStore {
    fun get(code: String): OnlineRoom?
    fun execute(
        requestKey: String, fingerprint: String, code: String,
        change: (OnlineRoom?) -> OnlineRoom
    ): CommandResult
}
