package com.failureludo.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
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

    @Test fun existingInstallationsKeepTheirVolumeUntilCategoriesAreAdjusted() {
        val settings = mutablePreferencesOf(floatPreferencesKey("master_volume") to 0.6f).toFeedbackSettings()
        SoundCatalog.categories.forEach {
            assertEquals(1f, settings.categoryVolume(it.event), 0f)
            assertEquals(0.6f, settings.effectiveVolume(it.event), 0f)
        }
    }

    @Test fun categoryVolumesRoundTripIndependentlyAndCombineWithMasterVolume() {
        val preferences = mutablePreferencesOf()
        preferences.writeFeedbackSettings(FeedbackSettings(masterVolume = 0.6f,
            soundSelections = mapOf(FeedbackEvent.DICE_ROLL to "wooden_dice_light"),
            soundVolumes = mapOf(FeedbackEvent.DICE_ROLL to 0.25f, FeedbackEvent.CAPTURE to 0f)))
        val restored = preferences.toFeedbackSettings()
        assertEquals(0.25f, preferences[floatPreferencesKey("sound_volume_dice_roll")]!!, 0f)
        assertEquals(0.15f, restored.effectiveVolume(FeedbackEvent.DICE_ROLL), 0.0001f)
        assertEquals(0f, restored.effectiveVolume(FeedbackEvent.CAPTURE), 0f)
        assertEquals(0.6f, restored.effectiveVolume(FeedbackEvent.PIECE_MOVE), 0f)
        assertEquals("wooden_dice_light", restored.soundSelections[FeedbackEvent.DICE_ROLL])

        preferences.writeFeedbackSettings(restored.copy(soundEnabled = false))
        val muted = preferences.toFeedbackSettings()
        assertEquals(0f, muted.effectiveVolume(FeedbackEvent.DICE_ROLL), 0f)
        assertEquals(0.25f, muted.categoryVolume(FeedbackEvent.DICE_ROLL), 0f)

        preferences.writeFeedbackSettings(restored.copy(soundVolumes = emptyMap()))
        val reset = preferences.toFeedbackSettings()
        SoundCatalog.categories.forEach { assertEquals(1f, reset.categoryVolume(it.event), 0f) }
        assertEquals(restored.masterVolume, reset.masterVolume, 0f)
        assertEquals(restored.soundSelections, reset.soundSelections)
    }

    @Test fun invalidCategoryVolumesAreSanitizedOnReadAndWrite() {
        val cases = listOf(-1f to 0f, 2f to 1f, Float.NaN to 1f, Float.POSITIVE_INFINITY to 1f)
        cases.forEach { (invalid, expected) ->
            val preferences = mutablePreferencesOf(floatPreferencesKey("sound_volume_capture") to invalid)
            assertEquals(expected, preferences.toFeedbackSettings().categoryVolume(FeedbackEvent.CAPTURE), 0f)
            preferences.writeFeedbackSettings(FeedbackSettings(soundVolumes = mapOf(FeedbackEvent.CAPTURE to invalid)))
            assertEquals(expected, preferences[floatPreferencesKey("sound_volume_capture")]!!, 0f)
        }
    }

}
