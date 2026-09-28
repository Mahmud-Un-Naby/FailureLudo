package com.failureludo.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.failureludo.feedback.FeedbackEvent
import com.failureludo.feedback.SoundCatalog
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class FeedbackSettings(
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val masterVolume: Float = 0.8f,
    val singleMoveAssistEnabled: Boolean = false,
    val reducedMotion: Boolean = false,
    val soundSelections: Map<FeedbackEvent, String> = emptyMap(),
    val forwardPawnSpeed: Float = PawnMovementSpeed.DEFAULT,
    val backwardPawnSpeed: Float = PawnMovementSpeed.DEFAULT
)

private val Context.feedbackPreferencesDataStore by preferencesDataStore(name = "feedback_preferences")

private object Keys {
    val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
    val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
    val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    val MASTER_VOLUME = floatPreferencesKey("master_volume")
    val SINGLE_MOVE_ASSIST_ENABLED = booleanPreferencesKey("single_move_assist_enabled")
    val FORWARD_PAWN_SPEED = floatPreferencesKey("forward_pawn_speed")
    val BACKWARD_PAWN_SPEED = floatPreferencesKey("backward_pawn_speed")
}

class GamePreferencesStore(private val context: Context) {
    val feedbackSettings: Flow<FeedbackSettings> = context.feedbackPreferencesDataStore.data
        .map { prefs -> prefs.toFeedbackSettings() }

    suspend fun updateFeedbackSettings(settings: FeedbackSettings) {
        context.feedbackPreferencesDataStore.edit { prefs ->
            prefs.writeFeedbackSettings(settings)
        }
    }
}

internal fun MutablePreferences.writeFeedbackSettings(settings: FeedbackSettings) {
    writeSoundSelections(settings.soundSelections)
    this[Keys.REDUCED_MOTION] = settings.reducedMotion
    this[Keys.SOUND_ENABLED] = settings.soundEnabled
    this[Keys.MUSIC_ENABLED] = settings.musicEnabled
    this[Keys.HAPTICS_ENABLED] = settings.hapticsEnabled
    this[Keys.MASTER_VOLUME] = settings.masterVolume.coerceIn(0f, 1f)
    this[Keys.SINGLE_MOVE_ASSIST_ENABLED] = settings.singleMoveAssistEnabled
    this[Keys.FORWARD_PAWN_SPEED] = PawnMovementSpeed.sanitize(settings.forwardPawnSpeed)
    this[Keys.BACKWARD_PAWN_SPEED] = PawnMovementSpeed.sanitize(settings.backwardPawnSpeed)
}

internal fun Preferences.toFeedbackSettings(): FeedbackSettings = FeedbackSettings(
    soundSelections = readSoundSelections(),
    reducedMotion = this[Keys.REDUCED_MOTION] ?: false,
    soundEnabled = this[Keys.SOUND_ENABLED] ?: true,
    musicEnabled = this[Keys.MUSIC_ENABLED] ?: false,
    hapticsEnabled = this[Keys.HAPTICS_ENABLED] ?: true,
    masterVolume = (this[Keys.MASTER_VOLUME] ?: 0.8f).coerceIn(0f, 1f),
    singleMoveAssistEnabled = this[Keys.SINGLE_MOVE_ASSIST_ENABLED] ?: false,
    forwardPawnSpeed = PawnMovementSpeed.sanitize(this[Keys.FORWARD_PAWN_SPEED] ?: PawnMovementSpeed.DEFAULT),
    backwardPawnSpeed = PawnMovementSpeed.sanitize(this[Keys.BACKWARD_PAWN_SPEED] ?: PawnMovementSpeed.DEFAULT)
)

// Store stable option IDs, never Android resource IDs or enum ordinals.
internal fun Preferences.readSoundSelections(): Map<FeedbackEvent, String> =
    SoundCatalog.categories.associate { category ->
        category.event to category.resolve(this[soundSelectionKey(category.event)]).id
    }

internal fun MutablePreferences.writeSoundSelections(selections: Map<FeedbackEvent, String>) {
    SoundCatalog.categories.forEach { category ->
        this[soundSelectionKey(category.event)] = category.resolve(selections[category.event]).id
    }
}

private fun soundSelectionKey(event: FeedbackEvent) =
    stringPreferencesKey("sound_selection_${event.settingsId}")
