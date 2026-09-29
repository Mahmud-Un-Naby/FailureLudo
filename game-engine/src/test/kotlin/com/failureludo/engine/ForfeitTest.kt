package com.failureludo.engine

import org.junit.Assert.*
import org.junit.Test

class ForfeitTest {
    private fun game(count: Int = 4) = GameEngine.newGame(PlayerColor.entries.take(count))

    @Test fun `current player forfeits a pending move without passing their dice to the next seat`() {
        val original = GameEngine.rollDice(game(), 6)
        val next = GameEngine.forfeit(original, original.currentPlayer.id)
        assertFalse(next.players[0].isActive)
        assertEquals(PlayerColor.GREEN, next.currentPlayer.color)
        assertEquals(TurnPhase.WAITING_FOR_ROLL, next.turnPhase)
        assertNull(next.lastDice)
        assertNull(next.diceByPlayer[PlayerId(1)])
        assertTrue(next.movablePieces.isEmpty())
        assertEquals(1, GameEngine.rollDice(next, 6).lastDice!!.rollCount)
        assertTrue(original.players[0].isActive)
        assertEquals(TurnPhase.WAITING_FOR_PIECE_SELECTION, original.turnPhase)
    }

    @Test fun `out of turn forfeit removes barriers and recomputes choices without rerolling`() {
        var initial = game(3)
        initial = initial.copy(players = initial.players.map { player ->
            when (player.color) {
                PlayerColor.RED -> player.copy(pieces = player.pieces.map {
                    when (it.id) {
                        0 -> it.copy(position = PiecePosition.MainTrack(4))
                        1 -> it.copy(position = PiecePosition.MainTrack(20))
                        else -> it
                    }
                })
                PlayerColor.BLUE -> player.copy(pieces = player.pieces.map {
                    if (it.id < 2) it.copy(position = PiecePosition.MainTrack(5), pairKey = "barrier") else it
                })
                else -> player
            }
        })
        val rolled = GameEngine.rollDice(initial, 2)
        assertFalse(rolled.movablePieces.any { it.id == 0 })
        assertTrue(rolled.movablePieces.any { it.id == 1 })
        val next = GameEngine.forfeit(rolled, PlayerId(2))
        assertEquals(rolled.currentPlayer.id, next.currentPlayer.id)
        assertEquals(rolled.lastDice, next.lastDice)
        assertEquals(rolled.moveCounter, next.moveCounter)
        assertEquals(TurnPhase.WAITING_FOR_PIECE_SELECTION, next.turnPhase)
        assertTrue(next.movablePieces.any { it.id == 0 })
        assertFalse(next.players[1].isActive)
        assertEquals(PiecePosition.MainTrack(6), GameEngine.selectPiece(next,
            next.movablePieces.single { it.id == 0 }).players[0].pieces[0].position)
    }

    @Test fun `last remaining FFA player wins immediately`() {
        val next = GameEngine.forfeit(game(2), PlayerId(2))
        assertTrue(next.isGameOver)
        assertEquals(listOf(PlayerId(1)), next.winners)
        assertNull(next.lastDice)
        assertTrue(next.movablePieces.isEmpty())
        assertEquals(GameEvent.PlayerWon(listOf(PlayerId(1))), next.eventLog.last())
    }

    @Test fun `forfeited seats are skipped on subsequent turns`() {
        var next = GameEngine.forfeit(game(), PlayerId(2))
        assertEquals(PlayerColor.RED, next.currentPlayer.color)
        for (color in listOf(PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.RED)) {
            next = GameEngine.advanceNoMoves(GameEngine.rollDice(next, 2))
            assertEquals(color, next.currentPlayer.color)
        }
    }

    @Test fun `team forfeit awards both opposing colors`() {
        val initial = GameEngine.rollDice(GameEngine.newGame(PlayerColor.entries, mode = GameMode.TEAM), 6)
        val next = GameEngine.forfeit(initial, PlayerId(3))
        assertTrue(next.isGameOver)
        assertEquals(listOf(PlayerId(2), PlayerId(4)), next.winners)
        assertEquals(setOf(PlayerColor.BLUE, PlayerColor.GREEN), next.players.filter { it.isActive }.map { it.color }.toSet())
        assertNull(next.lastDice)
        assertTrue(next.movablePieces.isEmpty())
    }

    @Test fun `inactive unknown and finished players cannot forfeit`() {
        val initial = game(3)
        assertThrows(IllegalArgumentException::class.java) { GameEngine.forfeit(initial, PlayerId(4)) }
        val next = GameEngine.forfeit(initial, PlayerId(2))
        assertThrows(IllegalArgumentException::class.java) { GameEngine.forfeit(next, PlayerId(2)) }
        val finished = GameEngine.forfeit(next, PlayerId(3))
        assertThrows(IllegalArgumentException::class.java) { GameEngine.forfeit(finished, PlayerId(1)) }
    }
}
