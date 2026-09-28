package com.failureludo.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.failureludo.feedback.FeedbackEvent
import com.failureludo.feedback.SoundCatalog
import org.junit.Assert.*
import org.junit.Test

class SoundPreferencesTest {
    private val defaults = SoundCatalog.categories.associate { it.event to it.defaultId }

    @Test fun installationsWithoutStoredChoicesUseWoodenDiceByDefault() {
        assertEquals(defaults, emptyPreferences().readSoundSelections())
    }

    @Test fun choicesRoundTripAsStableStringsWithoutChangingOtherPreferences() {
        val muted = booleanPreferencesKey("sound_enabled")
        val preferences = mutablePreferencesOf(muted to false)
        preferences.writeSoundSelections(defaults)
        assertEquals(defaults, preferences.readSoundSelections())
        assertEquals(false, preferences[muted])
        assertEquals("original_capture", preferences[stringPreferencesKey("sound_selection_capture")])
    }

    @Test fun obsoleteAndWrongCategoryIdsFallBackWithoutAffectingValidChoices() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("sound_selection_capture") to "deleted_capture",
            stringPreferencesKey("sound_selection_dice_roll") to "original_capture",
            stringPreferencesKey("sound_selection_win") to "tabletop_win"
        )
        assertEquals(defaults, preferences.readSoundSelections())
    }

    @Test fun alternativesRoundTripIndependentlyAndResetReturnsToWoodenDice() {
        val preferences = mutablePreferencesOf()
        val selected = defaults + mapOf(
            FeedbackEvent.DICE_ROLL to "wooden_dice_light",
            FeedbackEvent.PIECE_MOVE to "wooden_piece_tap",
            FeedbackEvent.CAPTURE to "wooden_capture_knock"
        )
        preferences.writeSoundSelections(selected)
        assertEquals(selected, preferences.readSoundSelections())
        preferences.writeSoundSelections(emptyMap())
        assertEquals("wooden_dice_roll", preferences.readSoundSelections()[FeedbackEvent.DICE_ROLL])
        assertEquals("original_capture", preferences.readSoundSelections()[FeedbackEvent.CAPTURE])
    }

    @Test fun snakeCaptureChoicePersistsAndDoesNotChangeOtherCategories() {
        val preferences = mutablePreferencesOf()
        val selected = defaults + (FeedbackEvent.CAPTURE to "snake_capture_hiss")
        preferences.writeSoundSelections(selected)
        assertEquals(selected, preferences.readSoundSelections())
    }

    @Test fun savedOriginalDiceChoiceSurvivesTheNewDefault() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("sound_selection_dice_roll") to "tabletop_dice_roll"
        )
        assertEquals("tabletop_dice_roll", preferences.readSoundSelections()[FeedbackEvent.DICE_ROLL])
    }

    @Test fun resetClearsStaleSelectionsAndPreservesOtherSettings() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("sound_selection_capture") to "deleted_capture",
            booleanPreferencesKey("haptics_enabled") to false
        )
        preferences.writeSoundSelections(emptyMap())
        assertEquals(defaults, preferences.readSoundSelections())
        assertEquals(false, preferences[booleanPreferencesKey("haptics_enabled")])
        assertEquals(defaults[FeedbackEvent.CAPTURE],
            preferences[stringPreferencesKey("sound_selection_capture")])
    }
}
