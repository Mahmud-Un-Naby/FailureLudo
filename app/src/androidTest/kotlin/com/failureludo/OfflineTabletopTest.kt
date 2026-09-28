package com.failureludo

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.pressBack
import org.junit.Rule
import org.junit.Test

/** Exercises real navigation and the roll gate, without authentication or a backend. */
class OfflineTabletopTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun customSetupKeepsSingleSeatsAndExposesSelectedColors() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("New game").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("New game").performScrollTo().performClick()
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithText("Players & teams").performScrollTo().performClick()
        compose.onNodeWithText("Team").performClick()
        compose.onAllNodesWithText("Team 1").assertCountEquals(2)
        compose.onAllNodesWithText("Team 2").assertCountEquals(2)
        compose.onNodeWithText("Single").performClick()
        compose.onNodeWithContentDescription("Back to settings").performClick()
        compose.onNodeWithText("Seats & colors").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Top left, playing").assertExists()
        compose.onNodeWithContentDescription("Bottom right, playing").assertExists()
        compose.onNodeWithContentDescription("Top right, empty").assertExists()
        compose.onNodeWithContentDescription("Bottom left, empty").assertExists()
        compose.onNodeWithText("Reset all colors").performScrollTo().performClick()
        compose.onNode(hasContentDescription(", Red", substring = true) and isSelected()).assertExists()
        compose.onNodeWithContentDescription("Player-1, Blue").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Player-1, Blue").assertIsSelected()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Your game").assertExists()
        compose.onNodeWithContentDescription("Board preview with 2 players").assertExists()
        // Reopening settings must keep the custom draft, including its seat selection.
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithText("Seats & colors").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Top right, empty").assertExists()
        compose.onNodeWithContentDescription("Player-1, Blue").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Quick setup").performClick()
        compose.onNodeWithText("2 players").assertIsSelected()
    }

    @Test fun localGameOpensAndRollSettlesWithoutAuthentication() {
        compose.runOnIdle { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(5_000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("New game").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("New game").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("2 players").assertIsSelected()
        compose.onNodeWithContentDescription("Top left, playing").assertIsDisplayed()
        compose.onNodeWithContentDescription("Bottom right, playing").assertIsDisplayed()
        compose.onNodeWithText("Heuristic").assertDoesNotExist()
        compose.onNodeWithText("Start game").performClick()
        compose.onNodeWithContentDescription("Roll dice").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("Dice:", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Ludo board.", substring = true).assertIsDisplayed()
        compose.runOnIdle { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(5_000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        compose.onNodeWithContentDescription("Ludo board.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("CURRENT TURN").assertIsDisplayed()
        // Exercise the system Back path with target 36, not just the toolbar exit button.
        pressBack()
        compose.onNodeWithText("Quit Game?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Ludo board.", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Motion").performScrollTo().performClick()
        compose.onNodeWithText("Reduced motion").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Play assistance").assertExists()
        pressBack()
        compose.onNodeWithText("Game Feedback").assertDoesNotExist()
        compose.onNodeWithText("Quit Game?").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Ludo board.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("CURRENT TURN").assertIsDisplayed()
    }
}
