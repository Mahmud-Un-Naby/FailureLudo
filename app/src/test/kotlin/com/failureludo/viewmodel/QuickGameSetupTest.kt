package com.failureludo.viewmodel

import com.failureludo.engine.GameEngine
import com.failureludo.engine.GameMode
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.PlayerType
import org.junit.Assert.*
import org.junit.Test

class QuickGameSetupTest {
    @Test fun twoPlayersStartInOppositeCornersWithSequentialNames() {
        val setup = quickGameSetup()
        val game = GameEngine.newGame(activeColors = setup.activeColors,
            playerTypes = setup.playerTypes, playerNames = setup.playerNames, mode = setup.mode)
        assertEquals(listOf(PlayerColor.RED, PlayerColor.YELLOW), setup.activeColors)
        assertEquals("Player-1", setup.playerNames[PlayerColor.RED])
        assertEquals("Player-2", setup.playerNames[PlayerColor.YELLOW])
        assertEquals(setup.activeColors, game.players.filter { it.isActive }.map { it.color })
        assertTrue(setup.playerTypes.values.all { it == PlayerType.HUMAN })
        assertEquals(GameMode.FREE_FOR_ALL, setup.mode)
    }

    @Test fun quickGameSupportsThreeAndFourPeople() {
        for (count in 3..4) {
            val setup = quickGameSetup(count)
            assertEquals(count, setup.activeColors.distinct().size)
            assertTrue(setup.playerTypes.values.all { it == PlayerType.HUMAN })
        }
    }

    @Test fun computerPresetsHaveOneHumanAndNamedComputerOpponents() {
        for (count in 2..4) {
            val setup = quickGameSetup(count, vsComputer = true)
            val game = GameEngine.newGame(activeColors = setup.activeColors,
                playerTypes = setup.playerTypes, playerNames = setup.playerNames, mode = setup.mode)
            val players = game.players.filter { it.isActive }
            assertEquals(count, players.size)
            assertEquals(1, players.count { it.type == PlayerType.HUMAN })
            assertEquals(count - 1, players.count { it.type == PlayerType.BOT })
            assertEquals("You", game.currentPlayer.name)
            assertEquals(PlayerType.HUMAN, game.currentPlayer.type)
            assertEquals(count, players.map { it.name }.distinct().size)
            assertEquals(BotBehaviorMode.HEURISTIC, setup.botBehaviorMode)
        }
        assertTrue(quickGameSetup().playerTypes.values.all { it == PlayerType.HUMAN })
    }

    @Test fun oldExperimentalPreferencesUseRegularComputerForNewGames() {
        val saved = SetupState(
            activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE),
            botBehaviorMode = BotBehaviorMode.AI_UNDER_DEVELOPMENT,
            playerTypes = mapOf(PlayerColor.RED to PlayerType.BOT, PlayerColor.BLUE to PlayerType.HUMAN),
            playerNames = mapOf(PlayerColor.RED to "  ", PlayerColor.BLUE to " Sam ")
        )
        val next = saved.forNewGame()
        assertEquals(BotBehaviorMode.HEURISTIC, next.botBehaviorMode)
        assertEquals(saved.activeColors, next.activeColors)
        assertEquals(saved.playerTypes, next.playerTypes)
        assertEquals("Player-1", next.playerNames[PlayerColor.RED])
        assertEquals("Sam", next.playerNames[PlayerColor.BLUE])
        assertEquals(BotBehaviorMode.AI_UNDER_DEVELOPMENT, saved.botBehaviorMode)
    }
}
