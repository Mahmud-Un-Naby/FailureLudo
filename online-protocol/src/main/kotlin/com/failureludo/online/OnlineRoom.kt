package com.failureludo.online

import com.failureludo.engine.*

const val PROTOCOL_VERSION = 2
const val RULES_VERSION = "2026-09-29"

enum class RoomStatus { WAITING, PLAYING, FINISHED, CLOSED }
data class Member(val uid: String, val name: String, val color: PlayerColor, val resigned: Boolean = false)
data class LastAction(val type: String, val uid: String, val dice: Int? = null, val eventCount: Int = 0)
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
) {
    /** A resigned teammate keeps their color/pawns; the remaining teammate owns its turns. */
    fun controllerUid(color: PlayerColor): String? {
        val member = members.find { it.color == color } ?: return null
        if (!member.resigned) return member.uid
        return if (mode == GameMode.TEAM) members.find {
            !it.resigned && it.color.teamIndex == color.teamIndex
        }?.uid else null
    }

    fun hasResigned(uid: String?): Boolean = members.any { it.uid == uid && it.resigned }
    fun canResign(uid: String?): Boolean = status == RoomStatus.PLAYING &&
        members.any { it.uid == uid && !it.resigned }
    fun canForget(uid: String?): Boolean = status in listOf(RoomStatus.FINISHED, RoomStatus.CLOSED) || hasResigned(uid)
}

class UnsupportedRoomVersion : IllegalArgumentException("This room requires a compatible app version.")
