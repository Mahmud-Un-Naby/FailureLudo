package com.failureludo.ui.screens

import com.failureludo.engine.GameMode
import com.failureludo.engine.Piece
import com.failureludo.engine.PiecePosition
import com.failureludo.engine.PlayerColor
import com.failureludo.ui.components.TappedCellPieces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StackTapDecisionTest {

    @Test
    fun arrivingPair_keepsOlderSingleAsSingleChoice() {
        val pair = listOf(0, 1).map { id ->
            Piece(id, PlayerColor.RED, PiecePosition.MainTrack(5), lastMovedAt = 50L, pairKey = "RED:0|RED:1")
        }
        val single = Piece(2, PlayerColor.RED, PiecePosition.MainTrack(5), lastMovedAt = 1L)
        val pieces = pair + single
        val decision = resolveStackTapDecision(TappedCellPieces(pieces, pieces, single), GameMode.FREE_FOR_ALL)
        assertEquals(single, decision.options.first { it.label == "Move single" }.piece)
        assertTrue(decision.options.first { it.label == "Move pair" }.piece in pair)
        assertEquals(listOf(PlayerColor.RED, PlayerColor.RED),
            decision.options.first { it.label == "Move pair" }.previewColors)
    }

    @Test
    fun numberedCellBeforeQueue_keepsPairAndSingleChoices() {
        val pieces = (0..2).map { id ->
            Piece(id, PlayerColor.RED, PiecePosition.MainTrack(50), lastMovedAt = id.toLong())
        }
        val decision = resolveStackTapDecision(TappedCellPieces(pieces, pieces, pieces[2]), GameMode.FREE_FOR_ALL)
        assertEquals(setOf("Move single", "Move pair"), decision.options.map { it.label }.toSet())
    }

    @Test
    fun safeSquare_prefersPreferredMovablePiece() {
        val preferred = Piece(
            id = 0,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(0), // safe square
            lastMovedAt = 10L
        )
        val other = Piece(
            id = 1,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(0),
            lastMovedAt = 20L
        )

        val decision = resolveStackTapDecision(
            TappedCellPieces(
                allPieces = listOf(preferred, other),
                movablePieces = listOf(preferred, other),
                preferredPiece = preferred
            ),
            mode = GameMode.FREE_FOR_ALL
        )

        assertEquals(preferred, decision.autoPiece)
        assertTrue(decision.options.isEmpty())
    }

    @Test
    fun mixedTeamSafeStack_opensChooserForMultipleColorOptions() {
        val redPiece = Piece(
            id = 0,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(0),
            lastMovedAt = 10L
        )
        val yellowPiece = Piece(
            id = 0,
            color = PlayerColor.YELLOW,
            position = PiecePosition.MainTrack(0),
            lastMovedAt = 50L
        )

        val decision = resolveStackTapDecision(
            TappedCellPieces(
                allPieces = listOf(redPiece, yellowPiece),
                movablePieces = listOf(redPiece, yellowPiece),
                preferredPiece = yellowPiece
            ),
            mode = GameMode.TEAM
        )

        assertNull(decision.autoPiece)
        assertEquals(2, decision.options.size)
        assertTrue(decision.options.any { it.previewColors == listOf(PlayerColor.RED) && it.piece == redPiece })
        assertTrue(decision.options.any { it.previewColors == listOf(PlayerColor.YELLOW) && it.piece == yellowPiece })
    }

    @Test
    fun mixedTeamSafeStack_withSingleMovablePiece_keepsAutoMove() {
        val redPiece = Piece(
            id = 0,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(0),
            lastMovedAt = 10L
        )
        val yellowPiece = Piece(
            id = 0,
            color = PlayerColor.YELLOW,
            position = PiecePosition.MainTrack(0),
            lastMovedAt = 50L
        )

        val decision = resolveStackTapDecision(
            TappedCellPieces(
                allPieces = listOf(redPiece, yellowPiece),
                movablePieces = listOf(redPiece),
                preferredPiece = yellowPiece
            ),
            mode = GameMode.TEAM
        )

        assertEquals(redPiece, decision.autoPiece)
        assertTrue(decision.options.isEmpty())
    }

    @Test
    fun mixedTeamLockedPair_onNormalSquare_collapsesToSingleMeaning() {
        val redPiece = Piece(
            id = 0,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(5),
            lastMovedAt = 10L
        )
        val yellowPiece = Piece(
            id = 0,
            color = PlayerColor.YELLOW,
            position = PiecePosition.MainTrack(5),
            lastMovedAt = 20L
        )

        val decision = resolveStackTapDecision(
            TappedCellPieces(
                allPieces = listOf(redPiece, yellowPiece),
                movablePieces = listOf(redPiece, yellowPiece),
                preferredPiece = redPiece
            ),
            mode = GameMode.TEAM
        )

        assertEquals(redPiece, decision.autoPiece)
        assertTrue(decision.options.isEmpty())
    }

    @Test
    fun threePieceLockedStack_returnsSingleAndPairChoices() {
        val pairA = Piece(
            id = 0,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(10),
            lastMovedAt = 10L
        )
        val pairB = Piece(
            id = 1,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(10),
            lastMovedAt = 11L
        )
        val topSingle = Piece(
            id = 2,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(10),
            lastMovedAt = 50L
        )

        val decision = resolveStackTapDecision(
            TappedCellPieces(
                allPieces = listOf(pairA, pairB, topSingle),
                movablePieces = listOf(pairA, pairB, topSingle),
                preferredPiece = pairA
            ),
            mode = GameMode.FREE_FOR_ALL
        )

        assertNull(decision.autoPiece)
        assertEquals(2, decision.options.size)
        assertTrue(decision.options.any { it.label == "Move single" && it.piece == topSingle })
        assertTrue(decision.options.any { it.label == "Move pair" && (it.piece == pairA || it.piece == pairB) })
    }

    @Test
    fun mixedTeamThreeStack_pairAndSingle_optionsAppear() {
        val redPairPiece = Piece(
            id = 0,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(5),
            lastMovedAt = 10L
        )
        val yellowPairPiece = Piece(
            id = 0,
            color = PlayerColor.YELLOW,
            position = PiecePosition.MainTrack(5),
            lastMovedAt = 20L
        )
        val redTopSingle = Piece(
            id = 1,
            color = PlayerColor.RED,
            position = PiecePosition.MainTrack(5),
            lastMovedAt = 50L
        )

        val decision = resolveStackTapDecision(
            TappedCellPieces(
                allPieces = listOf(redPairPiece, yellowPairPiece, redTopSingle),
                movablePieces = listOf(redPairPiece, yellowPairPiece, redTopSingle),
                preferredPiece = redTopSingle
            ),
            mode = GameMode.TEAM
        )

        assertNull(decision.autoPiece)
        assertEquals(2, decision.options.size)
        assertTrue(decision.options.any { it.label == "Move pair" && (it.piece == redPairPiece || it.piece == yellowPairPiece) })
        assertTrue(decision.options.any { it.label == "Move single" && it.piece == redTopSingle })
        assertEquals(listOf(PlayerColor.RED, PlayerColor.YELLOW),
            decision.options.first { it.label == "Move pair" }.previewColors)
        assertEquals(listOf(PlayerColor.RED),
            decision.options.first { it.label == "Move single" }.previewColors)
    }
}
