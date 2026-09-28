package com.failureludo.server

import com.failureludo.engine.*
import org.junit.Assert.*
import org.junit.Test

class RoomCodecTest {
    @Test fun `snapshot preserves pair identity team flags positions and events`() {
        val initial = GameEngine.newGame(PlayerColor.entries, mode = GameMode.TEAM)
        val pieces = listOf(
            Piece(0, PlayerColor.RED, PiecePosition.HomeBase),
            Piece(1, PlayerColor.RED, PiecePosition.MainTrack(50), 27, "pair-1"),
            Piece(2, PlayerColor.RED, PiecePosition.HomeColumn(5), 28),
            Piece(3, PlayerColor.RED, PiecePosition.Finished, 29)
        )
        val id = PlayerId(1)
        val state = initial.copy(players = initial.players.map { if (it.id == id) it.copy(pieces = pieces) else it },
            moveCounter = 30, lastDice = DiceResult(6, 2), movablePieces = listOf(pieces[1]),
            turnPhase = TurnPhase.WAITING_FOR_PIECE_SELECTION,
            hasEnteredBoardAtLeastOnce = initial.hasEnteredBoardAtLeastOnce + (id to true),
            sharedTeamDiceEnabled = setOf(0), winners = listOf(id),
            eventLog = listOf(GameEvent.PieceMoved(id, PlayerColor.RED, 1),
                GameEvent.PieceEnteredBoard(id, PlayerColor.RED, 1),
                GameEvent.PieceCaptured(PlayerId(2), PlayerColor.BLUE, id, PlayerColor.RED),
                GameEvent.PieceFinished(id, PlayerColor.RED, 3), GameEvent.PlayerWon(listOf(id)),
                GameEvent.ExtraRollGranted(id, PlayerColor.RED, "Capture"),
                GameEvent.TurnSkipped(id, PlayerColor.RED), GameEvent.ConsecutiveSixesForfeit(id, PlayerColor.RED)))
        val room = OnlineRoom("ABCDEFGH", "host", 4, GameMode.TEAM,
            listOf(Member("host", "Host", PlayerColor.RED)), 42, RoomStatus.PLAYING, state, LastAction("ROLL", "host", 6))
        assertEquals(room, RoomCodec.decode(RoomCodec.encode(room)))
    }
    @Test fun `unsupported protocol and rules fail closed`() {
        val room = OnlineRoom("ABCDEFGH", "host", 2, GameMode.FREE_FOR_ALL, listOf(Member("host", "Host", PlayerColor.RED)))
        for ((key, value) in listOf("protocolVersion" to 999, "rulesVersion" to "unknown")) {
            val error = assertThrows(ApiException::class.java) { RoomCodec.decode(RoomCodec.encode(room).put(key, value)) }
            assertEquals("UNSUPPORTED_VERSION", error.code)
        }
    }
}
