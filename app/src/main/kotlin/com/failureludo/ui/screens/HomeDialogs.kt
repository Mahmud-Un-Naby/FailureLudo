package com.failureludo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import com.failureludo.feedback.FeedbackEvent
import com.failureludo.feedback.GameFeedbackManager
import com.failureludo.viewmodel.GameViewModel
import com.failureludo.viewmodel.SetupState

@Composable
internal fun HomeSettingsDialog(
    viewModel: GameViewModel,
    gameSetup: SetupState? = null,
    onGameSetupChange: (SetupState) -> Unit = {},
    onDismiss: () -> Unit
) {
    val settings by viewModel.feedbackSettings.collectAsState()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val feedback = remember(context) {
        GameFeedbackManager(context)
    }
    LaunchedEffect(feedback, settings) { feedback.prepare(settings) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }
    FeedbackSettingsDialog(
        settings = settings,
        onSettingsChange = viewModel::updateFeedbackSettings,
        onTestCaptureSound = {
            feedback.emitSound(FeedbackEvent.CAPTURE, settings)
            if (settings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onPreviewSound = { event, optionId -> feedback.previewSound(event, optionId, settings) },
        onDismiss = onDismiss,
        gameSetup = gameSetup,
        onGameSetupChange = onGameSetupChange
    )
}

@Composable
internal fun RulesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFFFFF8FC),
        title = { Text("Rules") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RuleSection("Getting started", "Roll a 6 to bring a pawn out. Tap a highlighted pawn to move clockwise. Reach the center with an exact roll. In Single, finish all four pawns first to win.")
                RuleSection("Extra rolls", "Moving on a 6 or capturing earns another roll. With no legal move, your turn ends, even on a 6. A third consecutive 6 cancels only that roll.")
                RuleSection("Safe squares and captures", "Star squares are safe. On other track squares, singles capture singles and pairs capture pairs. Captured pawns return to base. A single on its own locked pair is protected from enemy singles. Reinforcing a friendly single sitting on an enemy pair captures that pair before your singles bond.")
                RuleSection("Pairs and blocked moves", "Two friendly pawns can form a locked pair. Pairs move only on even rolls, by half the die value. A third friendly pawn remains independent; a fourth cannot join on a non-safe track square. Enemy singles cannot pass a locked pair, but may land on it. Pairs can pass pairs.")
                RuleSection("Unlocking and finishing", "Pairs untie on safe squares. Same-color pairs also untie when entering their home lane. When offered a route, choose Enter Finish or Keep Circulating for another lap. Mixed-color teammate pairs must untie on a safe square before entering a home lane.")
                RuleSection("Teams", "Opposite corners are teammates. Both players must finish all their pawns to win. Once each teammate has used a 6 to bring a pawn out at least once, either can use their rolls to move either teammate’s pawns. Sharing stays unlocked after captures.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun RuleSection(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Text(body, style = MaterialTheme.typography.bodyMedium)
}
