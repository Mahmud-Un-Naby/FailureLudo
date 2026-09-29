package com.failureludo.online

import com.failureludo.engine.*

const val PROTOCOL_VERSION = 3
const val RULES_VERSION = "2026-09-29-afk"
const val DEFAULT_ACTION_TIMEOUT_MILLIS = 10_000L
const val DEFAULT_AFK_TIMEOUT_MILLIS = 120_000L

enum class RoomStatus { WAITING, PLAYING, FINISHED, CLOSED }
data class Member(val uid: String, val name: String, val color: PlayerColor, val resigned: Boolean = false,
                  val afkSinceMillis: Long? = null)
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
    val lastAction: LastAction? = null,
    /** Null for games created before action deadlines were introduced. */
    val actionTimeoutMillis: Long? = null,
    val actionDeadlineAtMillis: Long? = null,
    val afkTimeoutMillis: Long? = null
) {
    /** A resigned teammate keeps their color/pawns; the remaining teammate owns its turns. */
    fun controllerUid(color: PlayerColor): String? {
        val member = members.find { it.color == color } ?: return null
        if (!member.resigned) return member.uid
        return if (mode == GameMode.TEAM) members.find {
            !it.resigned && it.color.teamIndex == color.teamIndex
        }?.uid else null
    }

    fun nextDeadlineAtMillis(): Long? = if (status != RoomStatus.PLAYING) null else
        (members.filter { !it.resigned }.mapNotNull { member ->
            member.afkSinceMillis?.let { start -> afkTimeoutMillis?.let { start + it } }
        } + listOfNotNull(actionDeadlineAtMillis)).minOrNull()

    fun hasResigned(uid: String?): Boolean = members.any { it.uid == uid && it.resigned }
    fun canResign(uid: String?): Boolean = status == RoomStatus.PLAYING &&
        members.any { it.uid == uid && !it.resigned }
    fun canForget(uid: String?): Boolean = status in listOf(RoomStatus.FINISHED, RoomStatus.CLOSED) || hasResigned(uid)
}

class UnsupportedRoomVersion : IllegalArgumentException("This room requires a compatible app version.")
