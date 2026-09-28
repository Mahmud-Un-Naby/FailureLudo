package com.failureludo.viewmodel

import com.failureludo.engine.GameMode
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.PlayerType
import androidx.compose.ui.graphics.Color
import com.failureludo.ui.theme.LudoBlue
import com.failureludo.ui.theme.LudoGreen
import com.failureludo.ui.theme.LudoRed
import com.failureludo.ui.theme.LudoYellow

enum class BotBehaviorMode {
    AI_UNDER_DEVELOPMENT,
    HEURISTIC
}

/**
 * UI state for the Game Setup screen.
 * Captured before starting a game so it can be replayed.
 */
data class SetupState(
    val activeColors: List<PlayerColor> = listOf(
        PlayerColor.RED, PlayerColor.YELLOW
    ),
    val playerTypes: Map<PlayerColor, PlayerType> = PlayerColor.entries.associateWith { PlayerType.HUMAN },
    val playerNames: Map<PlayerColor, String> = defaultPlayerNames(),
    val playerColors: Map<PlayerColor, Color> = defaultPlayerColors(),
    val mode: GameMode = GameMode.FREE_FOR_ALL,
    val botBehaviorMode: BotBehaviorMode = BotBehaviorMode.HEURISTIC
)

fun defaultPlayerNames(): Map<PlayerColor, String> = mapOf(
    PlayerColor.RED to "Player-1",
    PlayerColor.BLUE to "Player-2",
    PlayerColor.YELLOW to "Player-3",
    PlayerColor.GREEN to "Player-4"
)

fun defaultPlayerColors(): Map<PlayerColor, Color> = mapOf(
    PlayerColor.RED to LudoRed,
    PlayerColor.BLUE to LudoBlue,
    PlayerColor.YELLOW to LudoYellow,
    PlayerColor.GREEN to LudoGreen
)

/** A fresh familiar game, independent of saved custom preferences. */
fun quickGameSetup(playerCount: Int = 2, vsComputer: Boolean = false): SetupState {
    require(playerCount in 2..4)
    val seats = when (playerCount) {
        2 -> listOf(PlayerColor.RED, PlayerColor.YELLOW)
        3 -> listOf(PlayerColor.RED, PlayerColor.BLUE, PlayerColor.YELLOW)
        else -> PlayerColor.entries.toList()
    }
    return SetupState(
        activeColors = seats,
        playerTypes = PlayerColor.entries.associateWith { seat ->
            if (vsComputer && seat != seats.first() && seat in seats) PlayerType.BOT else PlayerType.HUMAN
        },
        playerNames = defaultPlayerNames() + seats.mapIndexed { index, seat ->
            seat to if (!vsComputer) "Player-${index + 1}"
                else if (index == 0) "You"
                else if (playerCount == 2) "Computer" else "Computer $index"
        }.toMap()
    )
}

/** Keep the paused experimental policy out of new games, including Play again. */
fun SetupState.forNewGame(): SetupState = copy(
    botBehaviorMode = BotBehaviorMode.HEURISTIC,
    playerNames = PlayerColor.entries.associateWith { seat ->
        playerNames[seat]?.trim()?.takeIf { it.isNotEmpty() } ?: "Player-${seat.ordinal + 1}"
    }
)
