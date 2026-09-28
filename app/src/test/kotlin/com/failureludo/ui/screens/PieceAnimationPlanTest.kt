package com.failureludo.ui.screens

import com.failureludo.data.FeedbackSettings
import com.failureludo.engine.*
import com.failureludo.ui.components.BoardCoordinates
import org.junit.Assert.*
import org.junit.Test

class PieceAnimationPlanTest {
    private fun positions(state: GameState) = state.players.flatMap { player ->
        player.pieces.map { (it.color to it.id) to it.position }
    }.toMap()

    private fun capture(color: PlayerColor, landing: Int): Pair<GameState, GameState> {
        val attacker = PlayerColor.entries[(color.ordinal + 1) % 4]
        val before = GameEngine.newGame(PlayerColor.entries).let { initial ->
            initial.copy(moveCounter = 7, currentPlayerIndex = attacker.ordinal,
                players = initial.players.map { player ->
                    player.copy(pieces = player.pieces.map { pawn ->
                        when {
                            player.color == attacker && pawn.id == 0 -> pawn.copy(
                                position = PiecePosition.MainTrack((landing + 50) % 52), lastMovedAt = 7)
                            player.color == color && pawn.id == 2 -> pawn.copy(
                                position = PiecePosition.MainTrack(landing), lastMovedAt = 6)
                            else -> pawn
                        }
                    })
                })
        }
        val rolled = GameEngine.rollDice(before, 2)
        val after = GameEngine.selectPiece(rolled, rolled.currentPlayer.pieces[0])
        assertTrue(after.players.first { it.color == color }.pieces[2].isAtHome)
        return before to after
    }

    @Test
    fun capturesWaitForContactThenRetraceEveryCellToTheirOwnDock() {
        PlayerColor.entries.forEach { color ->
            val landing = (color.entryPosition + 7) % 52
            val (before, after) = capture(color, landing)
            val plan = buildAnimationPlan(after, positions(before), before.moveCounter)
            assertEquals(setOf(color to 2), plan.capturedKeys)
            assertEquals(3, plan.movingPieceStepCount)
            val path = plan.paths.toMap().getValue(color to 2)
            val collisionCell = BoardCoordinates.MAIN_TRACK[landing]
            assertEquals(List(3) { collisionCell }, path.take(3))
            val expectedReturn = (7 downTo 0).map {
                BoardCoordinates.MAIN_TRACK[(color.entryPosition + it) % 52]
            } + listOf(BoardCoordinates.HOME_YARD_SPOTS.getValue(color)[2])
            assertEquals(expectedReturn, path.drop(2))
            val timing = PawnAnimationTiming(FeedbackSettings(forwardPawnSpeed = 2f, backwardPawnSpeed = 1f))
            path.indices.drop(1).forEach { step ->
                assertEquals(if (step < 3) 65 else 35, timing.stepDurationMillis(plan, step, false))
            }
        }
    }

    @Test
    fun reverseReturnCrossesTrackZeroWithoutTeleportingToDock() {
        val (before, after) = capture(PlayerColor.BLUE, 2)
        val plan = buildAnimationPlan(after, positions(before), before.moveCounter)
        val path = plan.paths.toMap().getValue(PlayerColor.BLUE to 2).drop(2)
        assertEquals(listOf(2, 1, 0, 51, 50).map { BoardCoordinates.MAIN_TRACK[it] }, path.take(5))
        assertEquals(BoardCoordinates.MAIN_TRACK[13], path[path.lastIndex - 1])
        assertEquals(BoardCoordinates.HOME_YARD_SPOTS.getValue(PlayerColor.BLUE)[2], path.last())
        assertTrue(path.size <= 53)
    }

    @Test
    fun restoreUndoAndUnchangedStateNeverInventCaptureAnimations() {
        val (before, after) = capture(PlayerColor.BLUE, 20)
        assertTrue(buildAnimationPlan(after, null, -1).paths.isEmpty())
        assertTrue(buildAnimationPlan(after, positions(after), after.moveCounter).paths.isEmpty())
        assertTrue(buildAnimationPlan(before, positions(after), after.moveCounter).paths.isEmpty())
    }

    @Test
    fun ordinaryMovesDoNotAnimateUnenteredPawns() {
        val initial = GameEngine.newGame(PlayerColor.entries)
        val before = initial.copy(moveCounter = 7, players = initial.players.map { player ->
            if (player.color != PlayerColor.RED) player else player.copy(pieces = player.pieces.map {
                if (it.id == 0) it.copy(position = PiecePosition.MainTrack(1), lastMovedAt = 7) else it
            })
        })
        val rolled = GameEngine.rollDice(before, 2)
        val after = GameEngine.selectPiece(rolled, rolled.currentPlayer.pieces[0])
        val plan = buildAnimationPlan(after, positions(before), before.moveCounter)
        assertFalse(plan.hasCapture)
        assertEquals(setOf(PlayerColor.RED to 0), plan.paths.map { it.first }.toSet())
        assertEquals(listOf(1, 2, 3).map { BoardCoordinates.MAIN_TRACK[it] }, plan.paths.single().second)
    }
}
