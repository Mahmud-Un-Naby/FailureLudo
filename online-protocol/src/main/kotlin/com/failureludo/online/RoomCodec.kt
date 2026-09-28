package com.failureludo.online

import com.failureludo.engine.*
import org.json.JSONArray
import org.json.JSONObject

/** Explicit wire format, independent of Android saves and reflection/Kotlin value classes. */
object RoomCodec {
    fun encode(room: OnlineRoom): JSONObject = JSONObject()
        .put("protocolVersion", PROTOCOL_VERSION).put("rulesVersion", RULES_VERSION)
        .put("code", room.code).put("hostUid", room.hostUid).put("maxPlayers", room.maxPlayers)
        .put("mode", room.mode.name).put("revision", room.revision).put("status", room.status.name)
        .put("members", JSONArray(room.members.map { JSONObject()
            .put("uid", it.uid).put("name", it.name).put("color", it.color.name) }))
        .put("game", room.game?.let(::gameStateToJson) ?: JSONObject.NULL)
        .put("lastAction", room.lastAction?.let { JSONObject().put("type", it.type)
            .put("uid", it.uid).put("dice", it.dice ?: JSONObject.NULL).put("eventCount", it.eventCount) } ?: JSONObject.NULL)

    fun decode(json: JSONObject): OnlineRoom {
        if (json.getInt("protocolVersion") != PROTOCOL_VERSION || json.getString("rulesVersion") != RULES_VERSION) {
            throw UnsupportedRoomVersion()
        }
        return OnlineRoom(
            code = json.getString("code"), hostUid = json.getString("hostUid"),
            maxPlayers = json.getInt("maxPlayers"), mode = GameMode.valueOf(json.getString("mode")),
            members = json.getJSONArray("members").toObjectList {
                Member(it.getString("uid"), it.getString("name"), PlayerColor.valueOf(it.getString("color")))
            }, revision = json.getLong("revision"), status = RoomStatus.valueOf(json.getString("status")),
            game = if (json.isNull("game")) null else gameStateFromJson(json.getJSONObject("game")),
            lastAction = if (json.isNull("lastAction")) null else json.getJSONObject("lastAction").let {
                LastAction(it.getString("type"), it.getString("uid"), if (it.isNull("dice")) null else it.getInt("dice"),
                    it.optInt("eventCount", 0).also { count -> require(count in 0..32) })
            }
        )
    }

    private fun gameStateFromJson(json: JSONObject): GameState = GameState(
        players = json.getJSONArray("players").toObjectList(::playerFromJson),
        mode = GameMode.valueOf(json.getString("mode")), moveCounter = json.getLong("moveCounter"),
        currentPlayerIndex = json.getInt("currentPlayerIndex"),
        turnPhase = TurnPhase.valueOf(json.getString("turnPhase")),
        lastDice = if (json.isNull("lastDice")) null else diceFromJson(json.getJSONObject("lastDice")),
        diceByPlayer = diceByPlayerFromJson(json.getJSONObject("diceByPlayer")),
        hasEnteredBoardAtLeastOnce = enteredBoardFlagsFromJson(json.getJSONObject("enteredBoardAtLeastOnce")),
        sharedTeamDiceEnabled = json.getJSONArray("sharedTeamDiceEnabled").toIntList().toSet(),
        movablePieces = json.getJSONArray("movablePieces").toObjectList(::pieceFromJson),
        winners = json.getJSONArray("winners").toIntList().map(::PlayerId).takeIf { it.isNotEmpty() },
        eventLog = json.getJSONArray("eventLog").toObjectList(::eventFromJson)
    )

    private fun playerToJson(player: Player): JSONObject = JSONObject()
        .put("id", player.id.value).put("color", player.color.name).put("name", player.name)
        .put("type", player.type.name).put("isActive", player.isActive)
        .put("pieces", JSONArray(player.pieces.map(::pieceToJson)))

    private fun playerFromJson(json: JSONObject): Player = Player(
        id = PlayerId(json.getInt("id")), color = PlayerColor.valueOf(json.getString("color")),
        name = json.getString("name"), type = PlayerType.valueOf(json.getString("type")),
        isActive = json.getBoolean("isActive"), pieces = json.getJSONArray("pieces").toObjectList(::pieceFromJson)
    )

    private fun gameStateToJson(state: GameState): JSONObject {
        return JSONObject()
            .put("players", JSONArray(state.players.map { playerToJson(it) }))
            .put("mode", state.mode.name)
            .put("moveCounter", state.moveCounter)
            .put("currentPlayerIndex", state.currentPlayerIndex)
            .put("turnPhase", state.turnPhase.name)
            .put("lastDice", state.lastDice?.let { diceToJson(it) } ?: JSONObject.NULL)
            .put("diceByPlayer", diceByPlayerToJson(state.diceByPlayer))
            .put("enteredBoardAtLeastOnce", enteredBoardFlagsToJson(state.hasEnteredBoardAtLeastOnce))
            .put("sharedTeamDiceEnabled", JSONArray(state.sharedTeamDiceEnabled.sorted()))
            .put("movablePieces", JSONArray(state.movablePieces.map { pieceToJson(it) }))
                .put("winners", JSONArray((state.winners ?: emptyList()).map { it.value }))
            .put("eventLog", JSONArray(state.eventLog.map { eventToJson(it) }))
    }

    private fun pieceToJson(piece: Piece): JSONObject {
        return JSONObject()
            .put("id", piece.id)
            .put("color", piece.color.name)
            .put("position", piecePositionToJson(piece.position))
            .put("lastMovedAt", piece.lastMovedAt)
            .put("pairKey", piece.pairKey ?: JSONObject.NULL)
    }

    private fun pieceFromJson(json: JSONObject): Piece {
        return Piece(
            id = json.getInt("id"),
            color = PlayerColor.valueOf(json.getString("color")),
            position = piecePositionFromJson(json.getJSONObject("position")),
            lastMovedAt = if (json.has("lastMovedAt")) json.getLong("lastMovedAt") else 0L,
            pairKey = if (json.has("pairKey") && !json.isNull("pairKey")) json.getString("pairKey") else null
        )
    }

    private fun diceToJson(dice: DiceResult): JSONObject {
        return JSONObject()
            .put("value", dice.value)
            .put("rollCount", dice.rollCount)
    }

    private fun diceFromJson(json: JSONObject): DiceResult {
        return DiceResult(
            value = json.getInt("value"),
            rollCount = if (json.getInt("value") == 6) json.getInt("rollCount") else 0
        )
    }

    private fun piecePositionToJson(position: PiecePosition): JSONObject {
        return when (position) {
            is PiecePosition.HomeBase -> JSONObject().put("type", "HOME_BASE")
            is PiecePosition.MainTrack -> JSONObject().put("type", "MAIN_TRACK").put("index", position.index)
            is PiecePosition.HomeColumn -> JSONObject().put("type", "HOME_COLUMN").put("step", position.step)
            is PiecePosition.Finished -> JSONObject().put("type", "FINISHED")
        }
    }

    private fun piecePositionFromJson(json: JSONObject): PiecePosition {
        return when (json.getString("type")) {
            "HOME_BASE" -> PiecePosition.HomeBase
            "MAIN_TRACK" -> PiecePosition.MainTrack(json.getInt("index"))
            "HOME_COLUMN" -> PiecePosition.HomeColumn(json.getInt("step"))
            "FINISHED" -> PiecePosition.Finished
            else -> error("Unsupported pawn position")
        }
    }

    private fun diceByPlayerToJson(diceByPlayer: Map<PlayerId, Int?>): JSONObject {
        val obj = JSONObject()
        (1..4).forEach { idValue ->
            val id = PlayerId(idValue)
            val value = diceByPlayer[id]
            obj.put(idValue.toString(), value ?: JSONObject.NULL)
        }
        return obj
    }

    private fun diceByPlayerFromJson(json: JSONObject): Map<PlayerId, Int?> {
        return (1..4).associate { idValue ->
            val key = idValue.toString()
            val value = if (!json.has(key) || json.isNull(key)) null else json.getInt(key)
            PlayerId(idValue) to value
        }
    }

    private fun enteredBoardFlagsToJson(flags: Map<PlayerId, Boolean>): JSONObject {
        val obj = JSONObject()
        (1..4).forEach { idValue ->
            val id = PlayerId(idValue)
            obj.put(idValue.toString(), flags[id] == true)
        }
        return obj
    }

    private fun enteredBoardFlagsFromJson(json: JSONObject): Map<PlayerId, Boolean> {
        return (1..4).associate { idValue ->
            val key = idValue.toString()
            val value = if (!json.has(key)) false else json.optBoolean(key, false)
            PlayerId(idValue) to value
        }
    }

    private fun eventToJson(event: GameEvent): JSONObject {
        return when (event) {
            is GameEvent.PieceMoved -> JSONObject()
                .put("type", "PIECE_MOVED")
                .put("playerId", event.playerId.value)
                .put("color", event.color.name)
                .put("pieceId", event.pieceId)

            is GameEvent.PieceEnteredBoard -> JSONObject()
                .put("type", "PIECE_ENTERED")
                .put("playerId", event.playerId.value)
                .put("color", event.color.name)
                .put("pieceId", event.pieceId)

            is GameEvent.PieceCaptured -> JSONObject()
                .put("type", "PIECE_CAPTURED")
                .put("capturedPlayerId", event.capturedPlayerId.value)
                .put("capturedColor", event.capturedColor.name)
                .put("byPlayerId", event.byPlayerId.value)
                .put("byColor", event.byColor.name)

            is GameEvent.PieceFinished -> JSONObject()
                .put("type", "PIECE_FINISHED")
                .put("playerId", event.playerId.value)
                .put("color", event.color.name)
                .put("pieceId", event.pieceId)

            is GameEvent.PlayerWon -> JSONObject()
                .put("type", "PLAYER_WON")
                .put("playerIds", JSONArray(event.playerIds.map { it.value }))

            is GameEvent.ExtraRollGranted -> JSONObject()
                .put("type", "EXTRA_ROLL")
                .put("playerId", event.playerId.value)
                .put("color", event.color.name)
                .put("reason", event.reason)

            is GameEvent.TurnSkipped -> JSONObject()
                .put("type", "TURN_SKIPPED")
                .put("playerId", event.playerId.value)
                .put("color", event.color.name)

            is GameEvent.ConsecutiveSixesForfeit -> JSONObject()
                .put("type", "THREE_SIX_FORFEIT")
                .put("playerId", event.playerId.value)
                .put("color", event.color.name)
        }
    }

    private fun eventFromJson(json: JSONObject): GameEvent {
        return when (json.getString("type")) {
            "PIECE_MOVED" -> GameEvent.PieceMoved(
                playerId = PlayerId(json.getInt("playerId")),
                color = PlayerColor.valueOf(json.getString("color")),
                pieceId = json.getInt("pieceId")
            )

            "PIECE_ENTERED" -> GameEvent.PieceEnteredBoard(
                playerId = PlayerId(json.getInt("playerId")),
                color = PlayerColor.valueOf(json.getString("color")),
                pieceId = json.getInt("pieceId")
            )

            "PIECE_CAPTURED" -> GameEvent.PieceCaptured(
                capturedPlayerId = PlayerId(json.getInt("capturedPlayerId")),
                capturedColor = PlayerColor.valueOf(json.getString("capturedColor")),
                byPlayerId = PlayerId(json.getInt("byPlayerId")),
                byColor = PlayerColor.valueOf(json.getString("byColor"))
            )

            "PIECE_FINISHED" -> GameEvent.PieceFinished(
                playerId = PlayerId(json.getInt("playerId")),
                color = PlayerColor.valueOf(json.getString("color")),
                pieceId = json.getInt("pieceId")
            )

            "PLAYER_WON" -> {
                val playerIds = json.getJSONArray("playerIds").toIntList().map { PlayerId(it) }
                GameEvent.PlayerWon(playerIds)
            }

            "EXTRA_ROLL" -> GameEvent.ExtraRollGranted(
                playerId = PlayerId(json.getInt("playerId")),
                color = PlayerColor.valueOf(json.getString("color")),
                reason = json.getString("reason")
            )

            "TURN_SKIPPED" -> GameEvent.TurnSkipped(
                playerId = PlayerId(json.getInt("playerId")),
                color = PlayerColor.valueOf(json.getString("color"))
            )

            "THREE_SIX_FORFEIT" -> GameEvent.ConsecutiveSixesForfeit(
                playerId = PlayerId(json.getInt("playerId")),
                color = PlayerColor.valueOf(json.getString("color"))
            )

            else -> error("Unsupported game event")
        }
    }

    private fun <T> JSONArray.toObjectList(block: (JSONObject) -> T): List<T> =
        (0 until length()).map { block(getJSONObject(it)) }

    private fun JSONArray.toIntList(): List<Int> = (0 until length()).map(::getInt)
}
