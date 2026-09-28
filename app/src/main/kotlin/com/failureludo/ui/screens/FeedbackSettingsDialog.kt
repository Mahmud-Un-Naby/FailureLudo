package com.failureludo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.failureludo.data.FeedbackSettings
import com.failureludo.data.PawnMovementSpeed
import com.failureludo.feedback.FeedbackEvent
import com.failureludo.feedback.SoundCatalog

@Composable
internal fun FeedbackSettingsDialog(
    settings: FeedbackSettings,
    onSettingsChange: (FeedbackSettings) -> Unit,
    onTestCaptureSound: () -> Unit,
    onPreviewSound: (FeedbackEvent, String) -> Unit,
    onDismiss: () -> Unit
) {
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    if (showAdvanced) {
        AdvancedSoundSettingsDialog(settings, onSettingsChange, onPreviewSound) { showAdvanced = false }
        return
    }
    AlertDialog(
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFFFFF8FC),
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pawn speed", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() })
                Text("Saved for all games. Changes apply from the next move. Higher values are faster. Default: 2×.")
                PawnSpeedSlider(
                    label = "Forward speed",
                    speed = settings.forwardPawnSpeed,
                    enabled = !settings.reducedMotion,
                    onSpeedChange = { onSettingsChange(settings.copy(forwardPawnSpeed = it)) }
                )
                PawnSpeedSlider(
                    label = "Backward speed",
                    speed = settings.backwardPawnSpeed,
                    enabled = !settings.reducedMotion,
                    onSpeedChange = { onSettingsChange(settings.copy(backwardPawnSpeed = it)) }
                )
                Text("Backward speed controls captured pawns returning to base.",
                    style = MaterialTheme.typography.bodySmall)
                if (settings.reducedMotion) {
                    Text("Reduced motion uses minimal animation. Turn it off to adjust pawn speeds.",
                        style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = {
                    onSettingsChange(settings.copy(
                        forwardPawnSpeed = PawnMovementSpeed.DEFAULT,
                        backwardPawnSpeed = PawnMovementSpeed.DEFAULT
                    ))
                }) { Text("Reset pawn speeds") }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sound effects")
                    Switch(
                        checked = settings.soundEnabled,
                        onCheckedChange = { enabled ->
                            onSettingsChange(settings.copy(soundEnabled = enabled))
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reduced motion")
                    Switch(
                        checked = settings.reducedMotion,
                        onCheckedChange = { enabled ->
                            onSettingsChange(settings.copy(reducedMotion = enabled))
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Haptics")
                    Switch(
                        checked = settings.hapticsEnabled,
                        onCheckedChange = { enabled ->
                            onSettingsChange(settings.copy(hapticsEnabled = enabled))
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-select single move", modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.singleMoveAssistEnabled,
                        onCheckedChange = { enabled ->
                            onSettingsChange(settings.copy(singleMoveAssistEnabled = enabled))
                        }
                    )
                }

                Text("Master volume: ${(settings.masterVolume * 100f).toInt()}%")
                Slider(
                    value = settings.masterVolume,
                    onValueChange = { value ->
                        onSettingsChange(settings.copy(masterVolume = value.coerceIn(0f, 1f)))
                    },
                    valueRange = 0f..1f
                )

                OutlinedButton(onClick = { showAdvanced = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Advanced settings")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onTestCaptureSound,
                        enabled = settings.soundEnabled
                    ) {
                        Text("Test capture sound")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun PawnSpeedSlider(label: String, speed: Float, enabled: Boolean, onSpeedChange: (Float) -> Unit) {
    // Keep dragging local; persist once on release instead of writing on every frame.
    var pendingSpeed by remember(speed) { mutableFloatStateOf(PawnMovementSpeed.sanitize(speed)) }
    val speedLabel = "${pendingSpeed.toString().removeSuffix(".0")}×"
    Column {
        Text("$label: $speedLabel")
        Slider(
            value = pendingSpeed,
            onValueChange = { pendingSpeed = it },
            onValueChangeFinished = { onSpeedChange(pendingSpeed) },
            enabled = enabled,
            valueRange = PawnMovementSpeed.MIN..PawnMovementSpeed.MAX,
            steps = ((PawnMovementSpeed.MAX - PawnMovementSpeed.MIN) / PawnMovementSpeed.INCREMENT).toInt() - 1,
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = "$speedLabel speed"
            }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1× · Original", style = MaterialTheme.typography.labelSmall)
            Text("6× · Fastest", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AdvancedSoundSettingsDialog(
    settings: FeedbackSettings,
    onSettingsChange: (FeedbackSettings) -> Unit,
    onPreviewSound: (FeedbackEvent, String) -> Unit,
    onBack: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onBack,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFFFFF8FC),
        title = { Text("Advanced settings") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Sound choices", style = MaterialTheme.typography.titleMedium)
                Text("Choose a sound for each action. Your choices apply to all games and are saved automatically.")
                if (!settings.soundEnabled) Text("Enable sound effects in Settings to preview sounds.")
                SoundCatalog.categories.forEach { category ->
                    val selected = category.resolve(settings.soundSelections[category.event])
                    Text(category.event.label, style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() })
                    Column(Modifier.selectableGroup()) {
                        category.options.forEach { option ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Row(
                                    Modifier.weight(1f).heightIn(min = 48.dp).selectable(
                                        selected = selected.id == option.id,
                                        role = Role.RadioButton,
                                        onClick = {
                                            onSettingsChange(settings.copy(soundSelections =
                                                settings.soundSelections + (category.event to option.id)))
                                        }
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selected.id == option.id, onClick = null)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(option.label)
                                        if (option.id == category.defaultId) {
                                            Text("Default", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                TextButton(
                                    onClick = { onPreviewSound(category.event, option.id) },
                                    enabled = settings.soundEnabled,
                                    modifier = Modifier.semantics {
                                        contentDescription = "Preview ${category.event.label}: ${option.label}"
                                    }
                                ) { Text("Preview") }
                            }
                        }
                    }
                }
                TextButton(onClick = { onSettingsChange(settings.copy(soundSelections = emptyMap())) }) {
                    Text("Reset sound choices")
                }
            }
        },
        confirmButton = { TextButton(onClick = onBack) { Text("Back to settings") } }
    )
}
