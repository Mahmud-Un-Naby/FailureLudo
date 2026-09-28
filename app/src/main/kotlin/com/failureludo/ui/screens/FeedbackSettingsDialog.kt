package com.failureludo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.failureludo.data.FeedbackSettings
import com.failureludo.data.PawnMovementSpeed
import com.failureludo.engine.GameMode
import com.failureludo.engine.PlayerColor
import com.failureludo.feedback.FeedbackEvent
import com.failureludo.feedback.SoundCatalog
import com.failureludo.viewmodel.SetupState
import kotlin.math.roundToInt

private enum class SettingsPage(val title: String, val subtitle: String) {
    HOME("Settings", "Make the table feel like yours."),
    SOUND("Sound", "Volume and sound choices for every game."),
    MOTION("Motion", "Choose a comfortable pace. Applies from the next move."),
    ASSISTANCE("Play assistance", "Small touches that make play easier."),
    PLAYERS("Players & teams", "Names, opponents and teams for this game."),
    APPEARANCE("Seats & colors", "Arrange this game around your table.")
}

@Composable
internal fun FeedbackSettingsDialog(
    settings: FeedbackSettings,
    onSettingsChange: (FeedbackSettings) -> Unit,
    onTestCaptureSound: () -> Unit,
    onPreviewSound: (FeedbackEvent, String) -> Unit,
    onDismiss: () -> Unit,
    gameSetup: SetupState? = null,
    onGameSetupChange: (SetupState) -> Unit = {}
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.HOME) }
    var soundEvent by rememberSaveable { mutableStateOf<FeedbackEvent?>(null) }
    val category = SoundCatalog.categories.firstOrNull { it.event == soundEvent }
    val goBack: (() -> Unit)? = when {
        category != null -> ({ soundEvent = null })
        page != SettingsPage.HOME -> ({ page = SettingsPage.HOME })
        else -> null
    }
    SettingsWindow(
        title = category?.event?.label ?: page.title,
        subtitle = if (category != null) "Choose a sound, or preview it before deciding." else page.subtitle,
        pageKey = soundEvent ?: page,
        onBack = goBack,
        onClose = onDismiss
    ) {
        if (category != null) {
            if (!settings.soundEnabled || settings.masterVolume <= 0f) {
                SettingsCard { Text("Previews are muted. Turn on sound and raise the volume on the Sound page.") }
            }
            val selected = category.resolve(settings.soundSelections[category.event])
            SettingsCard {
                Column(Modifier.selectableGroup()) {
                    category.options.forEach { option ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f).heightIn(min = 72.dp).selectable(
                                selected = selected.id == option.id, role = Role.RadioButton,
                                onClick = { onSettingsChange(settings.copy(soundSelections =
                                    settings.soundSelections + (category.event to option.id))) }
                            ), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selected.id == option.id, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                                    if (option.id == category.defaultId) Text("Default",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            IconButton(
                                onClick = { onPreviewSound(category.event, option.id) },
                                enabled = settings.soundEnabled && settings.masterVolume > 0f
                            ) { Icon(Icons.Default.PlayArrow, "Preview ${category.event.label}: ${option.label}") }
                        }
                    }
                }
                TextButton(onClick = { onSettingsChange(settings.copy(
                    soundSelections = settings.soundSelections - category.event)) }) { Text("Use default sound") }
            }
        } else when (page) {
            SettingsPage.HOME -> {
                if (gameSetup != null) {
                    SettingsHeading("This game")
                    val seats = if (gameSetup.mode == GameMode.TEAM) PlayerColor.entries else gameSetup.activeColors
                    SettingsDestination("Players & teams", "${seats.size} players · ${if (gameSetup.mode == GameMode.TEAM) "Team" else "Single"}",
                        Icons.Default.Group) { page = SettingsPage.PLAYERS }
                    SettingsDestination("Seats & colors", "Corner positions and player colors",
                        Icons.Default.Palette) { page = SettingsPage.APPEARANCE }
                }
                SettingsHeading("All games")
                SettingsDestination("Sound", if (settings.soundEnabled) "${(settings.masterVolume * 100).roundToInt()}% volume · Choose & preview sounds" else "Muted · Choose & preview sounds",
                    Icons.AutoMirrored.Filled.VolumeUp) { page = SettingsPage.SOUND }
                SettingsDestination("Motion", if (settings.reducedMotion) "Reduced motion is on" else "Forward ${speedLabel(settings.forwardPawnSpeed)} · Return ${speedLabel(settings.backwardPawnSpeed)}",
                    Icons.Default.Speed) { page = SettingsPage.MOTION }
                SettingsDestination("Play assistance", "Haptics ${if (settings.hapticsEnabled) "on" else "off"} · Move selection",
                    Icons.Default.TouchApp) { page = SettingsPage.ASSISTANCE }
            }
            SettingsPage.SOUND -> {
                SettingsCard {
                    SettingsToggle("Sound effects", "Hear rolls, moves and captures.", settings.soundEnabled) {
                        onSettingsChange(settings.copy(soundEnabled = it))
                    }
                    HorizontalDivider()
                    var volume by remember(settings.masterVolume) { mutableFloatStateOf(settings.masterVolume) }
                    Text("Master volume: ${(volume * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall)
                    Slider(value = volume, onValueChange = { volume = it },
                        onValueChangeFinished = { onSettingsChange(settings.copy(masterVolume = volume)) },
                        enabled = settings.soundEnabled, valueRange = 0f..1f,
                        modifier = Modifier.semantics { contentDescription = "Master volume" })
                    OutlinedButton(onClick = onTestCaptureSound,
                        enabled = settings.soundEnabled && settings.masterVolume > 0f) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Test capture sound")
                    }
                }
                SettingsHeading("Sound choices")
                SoundCatalog.categories.forEach { sound ->
                    SettingsDestination(sound.event.label, sound.resolve(settings.soundSelections[sound.event]).label,
                        Icons.Default.MusicNote) { soundEvent = sound.event }
                }
                TextButton(onClick = { onSettingsChange(settings.copy(soundSelections = emptyMap())) }) {
                    Text("Reset sound choices")
                }
            }
            SettingsPage.MOTION -> {
                SettingsCard {
                    SettingsToggle("Reduced motion", "Use minimal animation for pawn moves and effects.", settings.reducedMotion) {
                        onSettingsChange(settings.copy(reducedMotion = it))
                    }
                }
                SettingsCard {
                    SettingsHeading("Pawn speed")
                    Text(if (settings.reducedMotion) "Turn off reduced motion to adjust speeds. Your choices are kept."
                        else "Below 1× is slower than the original speed; above 1× is faster. Default: 2×.", style = MaterialTheme.typography.bodyMedium)
                    PawnSpeedSlider("Forward speed", settings.forwardPawnSpeed, !settings.reducedMotion) {
                        onSettingsChange(settings.copy(forwardPawnSpeed = it))
                    }
                    HorizontalDivider()
                    PawnSpeedSlider("Backward speed", settings.backwardPawnSpeed, !settings.reducedMotion) {
                        onSettingsChange(settings.copy(backwardPawnSpeed = it))
                    }
                    Text("Backward speed controls captured pawns returning to base.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onSettingsChange(settings.copy(
                        forwardPawnSpeed = PawnMovementSpeed.DEFAULT, backwardPawnSpeed = PawnMovementSpeed.DEFAULT))
                    }) { Text("Reset pawn speeds") }
                }
            }
            SettingsPage.ASSISTANCE -> SettingsCard {
                SettingsToggle("Haptics", "Feel a light vibration for game events.", settings.hapticsEnabled) {
                    onSettingsChange(settings.copy(hapticsEnabled = it))
                }
                HorizontalDivider()
                SettingsToggle("Auto-select single move", "Play automatically when only one legal move is available.", settings.singleMoveAssistEnabled) {
                    onSettingsChange(settings.copy(singleMoveAssistEnabled = it))
                }
            }
            SettingsPage.PLAYERS -> if (gameSetup != null) GamePlayersSettings(gameSetup, onGameSetupChange)
            SettingsPage.APPEARANCE -> if (gameSetup != null) GameAppearanceSettings(gameSetup, onGameSetupChange)
        }
    }
}

private fun speedLabel(speed: Float) = "${speed.toString().removeSuffix(".0")}×"

@Composable
private fun PawnSpeedSlider(label: String, speed: Float, enabled: Boolean, onSpeedChange: (Float) -> Unit) {
    // Persist on release; dragging should not write preferences every frame.
    var pendingSpeed by remember(speed) { mutableFloatStateOf(PawnMovementSpeed.sanitize(speed)) }
    val valueLabel = speedLabel(pendingSpeed)
    Column {
        Text("$label: $valueLabel", style = MaterialTheme.typography.titleMedium)
        Slider(value = pendingSpeed, onValueChange = { pendingSpeed = it },
            onValueChangeFinished = { onSpeedChange(pendingSpeed) }, enabled = enabled,
            valueRange = PawnMovementSpeed.MIN..PawnMovementSpeed.MAX,
            steps = ((PawnMovementSpeed.MAX - PawnMovementSpeed.MIN) / PawnMovementSpeed.INCREMENT).toInt() - 1,
            modifier = Modifier.semantics { contentDescription = label; stateDescription = "$valueLabel speed" })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0.25× · Slowest", style = MaterialTheme.typography.labelSmall)
            Text("6× · Fastest", style = MaterialTheme.typography.labelSmall)
        }
    }
}
