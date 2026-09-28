package com.failureludo.feedback

import com.failureludo.R
import org.junit.Assert.*
import org.junit.Test

class SoundCatalogTest {
    @Test fun everyEventHasAListedDefaultWithWoodenDiceAndUnchangedOtherDefaults() {
        val resources = mapOf(
            FeedbackEvent.DICE_ROLL to R.raw.wooden_dice_roll,
            FeedbackEvent.PIECE_MOVE to R.raw.tabletop_piece_move,
            FeedbackEvent.CAPTURE to R.raw.sfx_capture,
            FeedbackEvent.PIECE_FINISH to R.raw.tabletop_piece_finish,
            FeedbackEvent.EXTRA_ROLL to R.raw.tabletop_extra_roll,
            FeedbackEvent.TURN_SKIP to R.raw.tabletop_turn_skip,
            FeedbackEvent.INVALID_ACTION to R.raw.tabletop_invalid_action,
            FeedbackEvent.WIN to R.raw.tabletop_win
        )
        assertEquals(FeedbackEvent.entries.toSet(), SoundCatalog.categories.map { it.event }.toSet())
        assertEquals(FeedbackEvent.entries.size, SoundCatalog.categories.size)
        SoundCatalog.categories.forEach { category ->
            val default = category.resolve(null)
            assertTrue(default in category.options)
            assertEquals(resources[category.event], default.resourceId)
            assertEquals(default, category.resolve("removed-option"))
        }
    }

    @Test fun originalDiceRemainsSelectableAlongsideRealWoodenRecordings() {
        val dice = SoundCatalog.categories.first { it.event == FeedbackEvent.DICE_ROLL }
        assertEquals("wooden_dice_roll", dice.defaultId)
        assertEquals(3, dice.options.size)
        assertEquals(R.raw.tabletop_dice_roll, dice.resolve("tabletop_dice_roll").resourceId)
        assertEquals(R.raw.wooden_dice_light, dice.resolve("wooden_dice_light").resourceId)
        assertEquals(R.raw.wooden_piece_tap,
            SoundCatalog.resolve(FeedbackEvent.PIECE_MOVE, "wooden_piece_tap").resourceId)
        assertEquals(R.raw.wooden_capture_knock,
            SoundCatalog.resolve(FeedbackEvent.CAPTURE, "wooden_capture_knock").resourceId)
    }

    @Test fun selectionUsesStableIdsInsteadOfListOrderAndAllowsAdditionalOptions() {
        val original = SoundCatalog.categories.first()
        val current = original.resolve(null)
        val alternative = current.copy(id = "alternative", label = "Alternative")
        val expanded = original.copy(options = listOf(alternative, current))
        assertEquals(current, expanded.resolve(null))
        assertEquals(alternative, expanded.resolve("alternative"))
        assertEquals(current, expanded.resolve("removed-option"))
    }

    @Test fun anotherCategorysOptionFallsBackToTheCategoryDefault() {
        val dice = SoundCatalog.resolve(FeedbackEvent.DICE_ROLL, null)
        assertEquals(SoundCatalog.resolve(FeedbackEvent.CAPTURE, null),
            SoundCatalog.resolve(FeedbackEvent.CAPTURE, dice.id))
    }

    @Test fun originalOptionsKeepTheirGainAndPitch() {
        val expectedGains = listOf(0.85f, 0.90f, 1.20f, 0.24f, 0.23f, 0.24f, 0.25f, 0.55f)
        assertEquals(expectedGains, SoundCatalog.categories.map {
            it.resolve(if (it.event == FeedbackEvent.DICE_ROLL) "tabletop_dice_roll" else null).gain
        })
        val dice = SoundCatalog.resolve(FeedbackEvent.DICE_ROLL, "tabletop_dice_roll")
        val move = SoundCatalog.resolve(FeedbackEvent.PIECE_MOVE, null)
        assertEquals(0.97f, dice.pitchMin)
        assertEquals(1.03f, dice.pitchMax)
        assertEquals(0.98f, move.pitchMin)
        assertEquals(1.02f, move.pitchMax)
        SoundCatalog.categories.drop(2).forEach {
            assertEquals(1f, it.resolve(null).pitchMin)
            assertEquals(1f, it.resolve(null).pitchMax)
        }
    }
}
