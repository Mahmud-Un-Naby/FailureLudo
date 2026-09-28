package com.failureludo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.failureludo.data.FeedbackSettings
import com.failureludo.ui.screens.FeedbackSettingsDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PawnSpeedSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun controlsChangeIndependentlyAndResetPreservesOtherSettings() {
        var settings = FeedbackSettings(soundEnabled = false, masterVolume = 0.25f)
        compose.setContent {
            var current by remember { mutableStateOf(settings) }
            MaterialTheme {
                FeedbackSettingsDialog(current, { settings = it; current = it }, {}, { _, _ -> }, {})
            }
        }
        compose.onNodeWithContentDescription("Forward speed").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(2.5f) }
        compose.runOnIdle {
            assertEquals(2.5f, settings.forwardPawnSpeed)
            assertEquals(1f, settings.backwardPawnSpeed)
        }
        compose.onNodeWithContentDescription("Backward speed").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        compose.runOnIdle {
            assertEquals(2.5f, settings.forwardPawnSpeed)
            assertEquals(0.5f, settings.backwardPawnSpeed)
        }
        compose.onNodeWithText("Reset pawn speeds").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1f, settings.forwardPawnSpeed)
            assertEquals(1f, settings.backwardPawnSpeed)
            assertFalse(settings.soundEnabled)
            assertEquals(0.25f, settings.masterVolume)
        }
    }

    @Test fun reducedMotionDisablesSlidersAndKeepsTheirSavedValues() {
        compose.setContent {
            MaterialTheme {
                FeedbackSettingsDialog(FeedbackSettings(reducedMotion = true,
                    forwardPawnSpeed = 2f, backwardPawnSpeed = 3f), {}, {}, { _, _ -> }, {})
            }
        }
        compose.onNodeWithContentDescription("Forward speed").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Forward speed: 2×").assertExists()
        compose.onNodeWithContentDescription("Backward speed").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Backward speed: 3×").assertExists()
    }
}
