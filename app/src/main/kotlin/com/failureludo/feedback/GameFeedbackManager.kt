package com.failureludo.feedback

import android.content.Context
import com.failureludo.data.FeedbackSettings

class GameFeedbackManager(context: Context) {
    private val audioManager = GameAudioManager(context)

    fun prepare(settings: FeedbackSettings) = audioManager.prepare(settings)

    fun emitSound(event: FeedbackEvent, settings: FeedbackSettings) {
        if (!settings.soundEnabled) return
        audioManager.play(SoundCatalog.resolve(event, settings.soundSelections[event]), settings.masterVolume)
    }

    fun previewSound(event: FeedbackEvent, optionId: String, settings: FeedbackSettings) {
        if (!settings.soundEnabled) return
        audioManager.prepare(settings)
        audioManager.preview(SoundCatalog.resolve(event, optionId), settings.masterVolume)
    }

    fun release() = audioManager.release()
}
