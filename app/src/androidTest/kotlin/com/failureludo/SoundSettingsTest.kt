package com.failureludo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.failureludo.data.FeedbackSettings
import com.failureludo.feedback.FeedbackEvent
import com.failureludo.ui.screens.FeedbackSettingsDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SoundSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun advancedChoicesCanBeOpenedAndPreviewedWithoutCreatingAGame() {
        var preview: Pair<FeedbackEvent, String>? = null
        compose.setContent {
            var settings by remember { mutableStateOf(FeedbackSettings()) }
            MaterialTheme {
                FeedbackSettingsDialog(settings, { settings = it }, {},
                    { event, id -> preview = event to id }, {})
            }
        }
        compose.onNodeWithText("Advanced settings").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Preview Capture: faaah")
            .performScrollTo().performClick()
        compose.runOnIdle { assertEquals(FeedbackEvent.CAPTURE to "original_capture", preview) }
        compose.onNodeWithText("Reset sound choices").performScrollTo().performClick()
        compose.onNodeWithText("Back to settings").performClick()
        compose.onNodeWithText("Sound effects").assertIsDisplayed()
    }

    @Test fun choosingAnAlternativeAndResettingUpdatesTheListedSelection() {
        var settings = FeedbackSettings()
        compose.setContent {
            var current by remember { mutableStateOf(settings) }
            MaterialTheme {
                FeedbackSettingsDialog(current, { settings = it; current = it }, {}, { _, _ -> }, {})
            }
        }
        compose.onNodeWithText("Advanced settings").performScrollTo().performClick()
        compose.onNodeWithText("Light wooden roll").performScrollTo().performClick()
        compose.onNodeWithText("Light wooden roll").assertIsSelected()
        compose.runOnIdle { assertEquals("wooden_dice_light", settings.soundSelections[FeedbackEvent.DICE_ROLL]) }
        compose.onNodeWithText("Reset sound choices").performScrollTo().performClick()
        compose.onNodeWithText("Wooden tumble").performScrollTo().assertIsSelected()
    }

    @Test fun muteDisablesPreviewsButLeavesChoicesAccessible() {
        compose.setContent {
            MaterialTheme {
                FeedbackSettingsDialog(FeedbackSettings(soundEnabled = false), {}, {}, { _, _ -> }, {})
            }
        }
        compose.onNodeWithText("Advanced settings").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Preview Dice roll: Wooden tumble")
            .performScrollTo().assertIsNotEnabled()
        compose.onAllNodes(isSelectable()).onFirst().assertIsEnabled().assertIsSelected()
    }
}
