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

    @Test fun existingInstallationsUseTheCurrentSoundsWithoutStoredChoices() {
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
