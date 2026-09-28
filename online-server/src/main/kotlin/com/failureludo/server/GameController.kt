package com.failureludo.server

import com.failureludo.online.*

import com.failureludo.engine.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/** Identity comes exclusively from the verified Firebase token, never a request body. */
class GameController(
    private val store: RoomStore,
    private val rollDie: () -> Int = { random.nextInt(6) + 1 },
    private val newCode: () -> String = { (1..8).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("") }
) {
    fun create(uid: String, requestId: String, name: String, maxPlayers: Int, mode: GameMode): CommandResult {
        val key = requestKey(uid, requestId)
        val displayName = checkedName(name)
        if (maxPlayers !in 2..4 || (mode == GameMode.TEAM && maxPlayers != 4)) {
            reject(400, "INVALID_PLAYERS", "Choose 2–4 players, or four for team mode.")
        }
        val fingerprint = digest("CREATE", displayName, maxPlayers.toString(), mode.name)
        repeat(5) {
            val code = newCode()
            try {
                return store.execute(key, fingerprint, code) { existing ->
                    if (existing != null) reject(409, "CODE_COLLISION", "Try creating the room again.")
                    OnlineRoom(code, uid, maxPlayers, mode, listOf(Member(uid, displayName, PlayerColor.RED)))
                }.also { requireMember(it.room, uid) }
            } catch (error: ApiException) {
                if (error.code != "CODE_COLLISION") throw error
            }
        }
        reject(503, "ROOM_UNAVAILABLE", "Could not allocate a room. Retry with the same request ID.")
    }

    fun get(uid: String, rawCode: String): OnlineRoom {
        val room = store.get(checkedCode(rawCode)) ?: reject(404, "ROOM_NOT_FOUND", "Room not found.")
        requireMember(room, uid)
        return room
    }

    fun execute(uid: String, rawCode: String, requestId: String, expectedRevision: Long?, command: Command): CommandResult {
        val code = checkedCode(rawCode)
        val key = requestKey(uid, requestId)
        val normalized = if (command is Command.Join) command.copy(name = checkedName(command.name)) else command
        if (normalized !is Command.Join && (expectedRevision == null || expectedRevision < 0)) {
            reject(400, "INVALID_REVISION", "Supply the last confirmed room revision.")
        }
        val fingerprint = digest(code, expectedRevision.toString(), normalized.toString())
        // Firestore may retry the callback. One candidate roll per HTTP operation,
        // generated only after authorization/phase checks. A committed receipt wins.
        val dice by lazy { rollDie().also { check(it in 1..6) } }
        return store.execute(key, fingerprint, code) change@{ stored ->
            val room = stored ?: reject(404, "ROOM_NOT_FOUND", "Room not found.")
            if (normalized is Command.Join) return@change join(room, uid, normalized.name)
            val member = requireMember(room, uid)
            if (room.revision != expectedRevision) {
                reject(409, "STALE_REVISION", "Reload the room before sending another action.")
            }
            when (normalized) {
                Command.Leave -> {
                    if (room.status != RoomStatus.WAITING) reject(409, "ALREADY_STARTED", "The game has started. Rejoin it to continue.")
                    val remaining = room.members.filterNot { it.uid == uid }
                    room.copy(revision = room.revision + 1, members = remaining,
                        hostUid = if (room.hostUid == uid) remaining.firstOrNull()?.uid ?: uid else room.hostUid,
                        status = if (remaining.isEmpty()) RoomStatus.CLOSED else RoomStatus.WAITING,
                        lastAction = LastAction("LEAVE", uid))
                }
                Command.Start -> {
                    if (room.hostUid != uid) reject(403, "HOST_REQUIRED", "Only the host can start.")
                    if (room.status != RoomStatus.WAITING) reject(409, "ALREADY_STARTED", "Game already started.")
                    if (room.members.size < 2 || (room.mode == GameMode.TEAM && room.members.size != 4)) {
                        reject(409, "NEED_PLAYERS", "More players are needed to start.")
                    }
                    val game = GameEngine.newGame(
                        activeColors = room.members.map { it.color },
                        playerNames = room.members.associate { it.color to it.name },
                        mode = room.mode
                    )
                    room.copy(revision = room.revision + 1, status = RoomStatus.PLAYING, game = game,
                        lastAction = LastAction("START", uid))
                }
                Command.Roll, is Command.Move -> {
                    val game = room.game
                    if (room.status != RoomStatus.PLAYING || game == null) {
                        reject(409, "NOT_PLAYING", "This game is not in progress.")
                    }
                    if (game.currentPlayer.color != member.color) {
                        reject(403, "NOT_YOUR_TURN", "Wait for your turn.")
                    }
                    val next = when (normalized) {
                        Command.Roll -> {
                            if (game.turnPhase != TurnPhase.WAITING_FOR_ROLL) {
                                reject(409, "WRONG_PHASE", "Choose a pawn before rolling again.")
                            }
                            GameEngine.rollDice(game, dice).let {
                                if (it.turnPhase == TurnPhase.NO_MOVES_AVAILABLE) GameEngine.advanceNoMoves(it) else it
                            }
                        }
                        is Command.Move -> {
                            if (game.turnPhase != TurnPhase.WAITING_FOR_PIECE_SELECTION) {
                                reject(409, "WRONG_PHASE", "Roll before selecting a pawn.")
                            }
                            val owner = game.players.find { it.id.value == normalized.playerId }
                                ?: reject(400, "INVALID_PAWN", "Unknown pawn owner.")
                            val piece = game.movablePieces.find {
                                it.color == owner.color && it.id == normalized.pieceId
                            } ?: reject(400, "ILLEGAL_MOVE", "That pawn cannot move.")
                            if (!GameRules.canMove(piece, game.lastDice!!.value, owner, game.players,
                                    game.mode, normalized.deferHomeEntry)) {
                                reject(400, "ILLEGAL_ROUTE", "That pawn route is blocked.")
                            }
                            GameEngine.selectPiece(game, piece, normalized.deferHomeEntry)
                        }
                        else -> error("Unexpected command")
                    }
                    room.copy(revision = room.revision + 1,
                        status = if (next.isGameOver) RoomStatus.FINISHED else RoomStatus.PLAYING,
                        game = next.copy(eventLog = next.eventLog.takeLast(32)),
                        lastAction = LastAction(if (normalized == Command.Roll) "ROLL" else "MOVE", uid,
                            if (normalized == Command.Roll) dice else null,
                            (next.eventLog.size - game.eventLog.size).coerceIn(0, 32)))
                }
                is Command.Join -> error("Join already handled")
            }
        }.let { result ->
            if (normalized == Command.Leave) {
                // Receipts outlive membership. A former member must not use an old
                // receipt to read the room's subsequent game or newly joined users.
                result.copy(room = result.room.copy(revision = result.acceptedRevision, hostUid = uid, members = emptyList(), game = null,
                    status = RoomStatus.CLOSED, lastAction = LastAction("LEAVE", uid)))
            } else result.also { requireMember(it.room, uid) }
        }
    }

    private fun join(room: OnlineRoom, uid: String, name: String): OnlineRoom {
        if (room.members.any { it.uid == uid }) return room
        if (room.status != RoomStatus.WAITING) reject(409, "ALREADY_STARTED", "Game already started.")
        if (room.members.size >= room.maxPlayers) reject(409, "ROOM_FULL", "Room is full.")
        val color = PlayerColor.entries.first { color -> room.members.none { it.color == color } }
        return room.copy(revision = room.revision + 1, members = room.members + Member(uid, name, color),
            lastAction = LastAction("JOIN", uid))
    }

    private fun requireMember(room: OnlineRoom, uid: String): Member =
        room.members.find { it.uid == uid } ?: reject(403, "NOT_A_MEMBER", "Join this room first.")

    private fun checkedName(name: String): String = name.trim().also {
        if (it.isEmpty() || it.length > 32 || it.any(Char::isISOControl)) {
            reject(400, "INVALID_NAME", "Use a display name of 1–32 characters.")
        }
    }

    private fun checkedCode(code: String): String = code.trim().uppercase(Locale.ROOT).also {
        if (!it.matches(Regex("[A-HJ-NP-Z2-9]{8}"))) reject(400, "INVALID_CODE", "Enter an eight-character room code.")
    }

    private fun requestKey(uid: String, requestId: String): String {
        require(uid.isNotBlank())
        if (!requestId.matches(Regex("[A-Za-z0-9_-]{16,80}"))) {
            reject(400, "INVALID_REQUEST_ID", "Supply a unique request ID of 16–80 safe characters.")
        }
        return digest(uid, requestId)
    }

    companion object {
        private val random = SecureRandom()
        private const val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private fun digest(vararg parts: String): String = MessageDigest.getInstance("SHA-256")
            .digest(parts.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
