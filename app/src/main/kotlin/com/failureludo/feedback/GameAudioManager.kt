package com.failureludo.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.failureludo.data.FeedbackSettings
import kotlin.random.Random

class GameAudioManager(context: Context) {
    private val appContext = context.applicationContext
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    // Cache by resource so options sharing an asset load it only once.
    private val loadedSoundIds = mutableMapOf<Int, Int>()
    private val readySoundIds = mutableSetOf<Int>()
    private var released = false
    private var pendingPreview: Pair<SoundOption, Float>? = null
    private var previewStreamId = 0

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            synchronized(this) {
                if (!released && sampleId in loadedSoundIds.values) {
                    if (status == 0) {
                        readySoundIds += sampleId
                        pendingPreview?.let { (option, volume) ->
                            if (loadedSoundIds[option.resourceId] == sampleId) {
                                pendingPreview = null
                                previewStreamId = playReady(option, volume)
                            }
                        }
                    } else {
                        Log.w("GameAudioManager", "Sound sample failed to load: $status")
                        val resourceId = loadedSoundIds.entries.first { it.value == sampleId }.key
                        loadedSoundIds.remove(resourceId)
                        if (pendingPreview?.first?.resourceId == resourceId) pendingPreview = null
                    }
                }
            }
        }
        prepare(FeedbackSettings())
    }

    /** Keep only selected assets resident as the catalog grows. Never queue late gameplay cues. */
    @Synchronized
    fun prepare(settings: FeedbackSettings) {
        if (released) return
        pendingPreview = null
        if (!settings.soundEnabled) {
            soundPool.stop(previewStreamId)
            previewStreamId = 0
        }
        val selectedResources = SoundCatalog.categories.map {
            it.resolve(settings.soundSelections[it.event]).resourceId
        }.toSet()
        loadedSoundIds.keys.toList().filter { it !in selectedResources }.forEach { resourceId ->
            val sampleId = loadedSoundIds.remove(resourceId) ?: return@forEach
            readySoundIds.remove(sampleId)
            soundPool.unload(sampleId)
        }
        selectedResources.forEach(::load)
    }

    private fun load(resourceId: Int): Int {
        loadedSoundIds[resourceId]?.let { return it }
        val sampleId = soundPool.load(appContext, resourceId, 1)
        if (sampleId != 0) loadedSoundIds[resourceId] = sampleId
        else Log.w("GameAudioManager", "Sound resource could not be loaded: $resourceId")
        return sampleId
    }

    @Synchronized
    fun play(option: SoundOption, volumeScale: Float): Boolean {
        if (released) return false
        load(option.resourceId)
        return playReady(option, volumeScale) != 0
    }

    @Synchronized
    fun preview(option: SoundOption, volumeScale: Float) {
        if (released) return
        soundPool.stop(previewStreamId)
        pendingPreview = null
        val sampleId = load(option.resourceId)
        if (sampleId in readySoundIds) previewStreamId = playReady(option, volumeScale)
        else if (sampleId != 0) pendingPreview = option to volumeScale
    }

    private fun playReady(option: SoundOption, volumeScale: Float): Int {
        val sampleId = loadedSoundIds[option.resourceId] ?: return 0
        if (sampleId !in readySoundIds) return 0
        val volume = (volumeScale.coerceIn(0f, 1f) * option.gain).coerceIn(0f, 1f)
        if (volume <= 0f) return 0
        val pitch = if (option.pitchMin == option.pitchMax) option.pitchMin
        else Random.nextDouble(option.pitchMin.toDouble(), option.pitchMax.toDouble()).toFloat()
        return soundPool.play(sampleId, volume, volume, 1, 0, pitch)
    }

    @Synchronized
    fun release() {
        if (released) return
        released = true
        pendingPreview = null
        loadedSoundIds.clear()
        readySoundIds.clear()
        soundPool.release()
    }
}
