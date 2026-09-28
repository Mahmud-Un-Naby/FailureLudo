package com.failureludo

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.failureludo.ui.screens.DeleteSavedGameDialog
import com.failureludo.ui.screens.HomeScreen
import com.failureludo.ui.screens.SavedGamesEmptyState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PreGameScreensTest {
    @get:Rule val compose = createComposeRule()

    @Test fun freshHomeCanReachImportWithoutStartingAGame() {
        var imported = false
        compose.setContent {
            MaterialTheme {
                var history by remember { mutableStateOf(false) }
                if (history) SavedGamesEmptyState { imported = true }
                else HomeScreen(onNewGame = {}, onResume = {}, onHistory = { history = true },
                    hasActiveGame = false, onRules = {}, onSettings = {}, isSessionRestored = true)
            }
        }
        compose.onNodeWithText("Resume").assertDoesNotExist()
        compose.onNodeWithText("Saved games").performScrollTo().performClick()
        compose.onNodeWithText("Import game").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, imported) }
    }

    @Test fun cancelDeletionPreservesGameAndConfirmDeletesOnce() {
        var deletes = 0
        var dismissed = false
        compose.setContent {
            MaterialTheme {
                var showDialog by remember { mutableStateOf(true) }
                TextButton(onClick = { showDialog = true }) { Text("Delete saved game") }
                if (showDialog) DeleteSavedGameDialog("Asha · Rafi",
                    onDismiss = { dismissed = true; showDialog = false },
                    onConfirm = { deletes++; showDialog = false })
            }
        }
        compose.onNodeWithText("Asha · Rafi").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertEquals(true, dismissed)
            assertEquals(0, deletes)
        }
        compose.onNodeWithText("Delete saved game?").assertDoesNotExist()
        compose.onNodeWithText("Delete saved game").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete saved game?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, deletes) }
    }
}
