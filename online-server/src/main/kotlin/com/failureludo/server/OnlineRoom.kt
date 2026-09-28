package com.failureludo.server

import com.failureludo.engine.GameMode
import com.failureludo.engine.GameState
import com.failureludo.engine.PlayerColor

const val PROTOCOL_VERSION = 1
const val RULES_VERSION = "2026-09-23"

enum class RoomStatus { WAITING, PLAYING, FINISHED }
data class Member(val uid: String, val name: String, val color: PlayerColor)
data class LastAction(val type: String, val uid: String, val dice: Int? = null)
data class OnlineRoom(
    val code: String,
    val hostUid: String,
    val maxPlayers: Int,
    val mode: GameMode,
    val members: List<Member>,
    val revision: Long = 0,
    val status: RoomStatus = RoomStatus.WAITING,
    val game: GameState? = null,
    val lastAction: LastAction? = null
)

sealed interface Command {
    data class Join(val name: String) : Command
    data object Start : Command
    data object Roll : Command
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
