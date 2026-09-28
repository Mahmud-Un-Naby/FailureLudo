package com.failureludo.ui.screens

import org.junit.Assert.*
import org.junit.Test

class PawnPathFrameTest {
    @Test fun startsAtTheSourceAndFinishesAtTheDestination() {
        assertEquals(PawnPathFrame(1, 0f), pawnPathFrame(0f, 1, 6))
        assertEquals(PawnPathFrame(6, 1f), pawnPathFrame(6f, 1, 6))
    }

    @Test fun boundariesLandBeforeTheNextHopBegins() {
        assertEquals(PawnPathFrame(2, 1f), pawnPathFrame(2f, 1, 6))
        assertEquals(PawnPathFrame(3, 0.25f), pawnPathFrame(2.25f, 1, 6))
    }

    @Test fun slowFramesCatchUpInsteadOfQueueingEveryMissedCell() {
        val before = pawnPathFrame(2.5f, 1, 52)
        val after = pawnPathFrame(7.5f, 1, 52)
        assertEquals(PawnPathFrame(3, 0.5f), before)
        assertEquals(PawnPathFrame(8, 0.5f), after)
    }

    @Test fun captureReturnStartsExactlyAtTheCollisionAndEndsAtTheDock() {
        assertEquals(PawnPathFrame(4, 0f), pawnPathFrame(3f, 4, 55))
        assertEquals(PawnPathFrame(4, 0.5f), pawnPathFrame(3.5f, 4, 55))
        assertEquals(PawnPathFrame(55, 1f), pawnPathFrame(55f, 4, 55))
    }

    @Test fun oneStepAndOutOfRangeFramesStayOnThePath() {
        assertEquals(PawnPathFrame(1, 0f), pawnPathFrame(-1f, 1, 1))
        assertEquals(PawnPathFrame(1, 0.5f), pawnPathFrame(0.5f, 1, 1))
        assertEquals(PawnPathFrame(1, 1f), pawnPathFrame(2f, 1, 1))
    }
}
