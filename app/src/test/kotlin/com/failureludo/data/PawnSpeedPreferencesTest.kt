package com.failureludo.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.failureludo.feedback.FeedbackEvent
import org.junit.Assert.*
import org.junit.Test

class PawnSpeedPreferencesTest {
    private val forwardKey = floatPreferencesKey("forward_pawn_speed")
    private val backwardKey = floatPreferencesKey("backward_pawn_speed")

    @Test fun existingInstallationsKeepOriginalTiming() {
        val settings = emptyPreferences().toFeedbackSettings()
        assertEquals(1f, settings.forwardPawnSpeed)
        assertEquals(1f, settings.backwardPawnSpeed)
    }

    @Test fun differentSpeedsSurvivePersistenceAndOtherSettingsChanges() {
        val prefs = mutablePreferencesOf()
        prefs.writeFeedbackSettings(FeedbackSettings(forwardPawnSpeed = 2.75f, backwardPawnSpeed = 0.5f))
        val restored = prefs.toPreferences().toFeedbackSettings()
        assertEquals(2.75f, restored.forwardPawnSpeed)
        assertEquals(0.5f, restored.backwardPawnSpeed)
        prefs.writeFeedbackSettings(restored.copy(soundEnabled = false, reducedMotion = true))
        assertEquals(2.75f, prefs.toFeedbackSettings().forwardPawnSpeed)
        assertEquals(0.5f, prefs.toFeedbackSettings().backwardPawnSpeed)
    }

    @Test fun changingAndResettingSpeedsPreservesExistingFeedbackChoices() {
        val prefs = mutablePreferencesOf()
        prefs.writeFeedbackSettings(FeedbackSettings(soundEnabled = false, hapticsEnabled = false,
            masterVolume = 0.35f, singleMoveAssistEnabled = true,
            soundSelections = mapOf(FeedbackEvent.CAPTURE to "snake_capture_hiss")))
        val original = prefs.toFeedbackSettings()
        prefs.writeFeedbackSettings(original.copy(forwardPawnSpeed = 4f, backwardPawnSpeed = 0.5f))
        prefs.writeFeedbackSettings(prefs.toFeedbackSettings().copy(
            forwardPawnSpeed = PawnMovementSpeed.DEFAULT, backwardPawnSpeed = PawnMovementSpeed.DEFAULT))
        assertEquals(original, prefs.toFeedbackSettings())
    }

    @Test fun invalidStoredSpeedsAreBoundedIndependently() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -2f, 0f, 99f).forEach { bad ->
            val expected = if (!bad.isFinite()) 1f else bad.coerceIn(0.5f, 4f)
            val prefs = mutablePreferencesOf(forwardKey to bad, backwardKey to 2f)
            assertEquals(expected, prefs.toFeedbackSettings().forwardPawnSpeed)
            assertEquals(2f, prefs.toFeedbackSettings().backwardPawnSpeed)
            prefs[forwardKey] = 3f
            prefs[backwardKey] = bad
            assertEquals(3f, prefs.toFeedbackSettings().forwardPawnSpeed)
            assertEquals(expected, prefs.toFeedbackSettings().backwardPawnSpeed)
        }
    }

    @Test fun invalidInputsAreSanitizedBeforeWriting() {
        val prefs = mutablePreferencesOf()
        prefs.writeFeedbackSettings(FeedbackSettings(forwardPawnSpeed = Float.NaN, backwardPawnSpeed = -1f))
        assertEquals(1f, prefs[forwardKey])
        assertEquals(0.5f, prefs[backwardKey])
    }
}
