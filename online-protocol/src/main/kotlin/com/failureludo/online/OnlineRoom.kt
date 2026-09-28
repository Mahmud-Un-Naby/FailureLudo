package com.failureludo.online

import com.failureludo.engine.*

const val PROTOCOL_VERSION = 1
const val RULES_VERSION = "2026-09-23"

enum class RoomStatus { WAITING, PLAYING, FINISHED, CLOSED }
data class Member(val uid: String, val name: String, val color: PlayerColor)
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
)

class UnsupportedRoomVersion : IllegalArgumentException("This room requires a compatible app version.")
