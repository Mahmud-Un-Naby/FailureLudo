package com.failureludo.feedback

import androidx.annotation.RawRes
import com.failureludo.R

/** IDs are persisted user preferences: keep them stable when renaming labels or files. */
enum class FeedbackEvent(val settingsId: String, val label: String) {
    DICE_ROLL("dice_roll", "Dice roll"),
    PIECE_MOVE("piece_move", "Pawn movement"),
    CAPTURE("capture", "Capture"),
    PIECE_FINISH("piece_finish", "Pawn finish"),
    EXTRA_ROLL("extra_roll", "Extra roll"),
    TURN_SKIP("turn_skip", "Skipped turn"),
    INVALID_ACTION("invalid_action", "Invalid action"),
    WIN("win", "Victory")
}

data class SoundOption(
    val id: String,
    val label: String,
    @RawRes val resourceId: Int,
    val gain: Float,
    val pitchMin: Float = 1f,
    val pitchMax: Float = 1f
)

data class SoundCategory(
    val event: FeedbackEvent,
    val defaultId: String,
    val options: List<SoundOption>
) {
    init {
        require(options.isNotEmpty())
        require(options.map { it.id }.distinct().size == options.size)
        require(options.any { it.id == defaultId })
    }

    fun resolve(selectedId: String?): SoundOption =
        options.firstOrNull { it.id == selectedId } ?: options.first { it.id == defaultId }
}

/** Add bundled audio and an option here; playback, persistence and the picker use this list. */
object SoundCatalog {
    val categories: List<SoundCategory> = listOf(
        SoundCategory(FeedbackEvent.DICE_ROLL, "tabletop_dice_roll", listOf(
            SoundOption("tabletop_dice_roll", "Tabletop", R.raw.tabletop_dice_roll, 0.85f, 0.97f, 1.03f)
        )),
        SoundCategory(FeedbackEvent.PIECE_MOVE, "tabletop_piece_move", listOf(
            SoundOption("tabletop_piece_move", "Tabletop", R.raw.tabletop_piece_move, 0.90f, 0.98f, 1.02f)
        )),
        SoundCategory(FeedbackEvent.CAPTURE, "original_capture", listOf(
            SoundOption("original_capture", "Tut tut faah", R.raw.sfx_capture, 1.20f)
        )),
        SoundCategory(FeedbackEvent.PIECE_FINISH, "tabletop_piece_finish", listOf(
            SoundOption("tabletop_piece_finish", "Tabletop", R.raw.tabletop_piece_finish, 0.24f)
        )),
        SoundCategory(FeedbackEvent.EXTRA_ROLL, "tabletop_extra_roll", listOf(
            SoundOption("tabletop_extra_roll", "Tabletop", R.raw.tabletop_extra_roll, 0.23f)
        )),
        SoundCategory(FeedbackEvent.TURN_SKIP, "tabletop_turn_skip", listOf(
            SoundOption("tabletop_turn_skip", "Tabletop", R.raw.tabletop_turn_skip, 0.24f)
        )),
        SoundCategory(FeedbackEvent.INVALID_ACTION, "tabletop_invalid_action", listOf(
            SoundOption("tabletop_invalid_action", "Tabletop", R.raw.tabletop_invalid_action, 0.25f)
        )),
        SoundCategory(FeedbackEvent.WIN, "tabletop_win", listOf(
            SoundOption("tabletop_win", "Tabletop", R.raw.tabletop_win, 0.55f)
        ))
    )

    private val byEvent = categories.associateBy { it.event }

    fun resolve(event: FeedbackEvent, selectedId: String?): SoundOption =
        byEvent.getValue(event).resolve(selectedId)
}
