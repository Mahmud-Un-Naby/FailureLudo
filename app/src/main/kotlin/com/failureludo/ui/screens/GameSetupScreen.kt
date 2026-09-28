package com.failureludo.ui.screens

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.failureludo.ui.tabletop.TabletopBoard
import com.failureludo.engine.Piece
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.failureludo.engine.GameMode
import com.failureludo.engine.PlayerColor
import com.failureludo.engine.PlayerType
import com.failureludo.ui.theme.*
import com.failureludo.viewmodel.SetupState
import com.failureludo.viewmodel.defaultPlayerColors
import com.failureludo.viewmodel.GameViewModel
import com.failureludo.viewmodel.quickGameSetup

private const val MAX_PLAYER_NAME_LENGTH = 18

@Composable
fun GameSetupScreen(
    viewModel: GameViewModel,
    onStartGame: () -> Unit,
    onBack: () -> Unit
) {
    SettingsTheme { GameSetupContent(viewModel, onStartGame, onBack) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GameSetupContent(viewModel: GameViewModel, onStartGame: () -> Unit, onBack: () -> Unit) {
    val setup by viewModel.setupState.collectAsState()
    var customGame by rememberSaveable { mutableStateOf(false) }
    var playerCount by rememberSaveable { mutableIntStateOf(2) }
    var vsComputer by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val currentSetup = if (customGame) setup else quickGameSetup(playerCount, vsComputer)
    val colorsToShow = if (currentSetup.mode == GameMode.TEAM) PlayerColor.entries else currentSetup.activeColors

    if (showSettings) HomeSettingsDialog(
        viewModel = viewModel,
        gameSetup = currentSetup,
        onGameSetupChange = {
            viewModel.updateSetup(it)
            customGame = true
        },
        onDismiss = { showSettings = false }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New game") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface {
                Button(
                    onClick = {
                        viewModel.updateSetup(currentSetup)
                        viewModel.startGame()
                        onStartGame()
                    },
                    enabled = !customGame || colorsToShow.size >= 2,
                    modifier = Modifier.navigationBarsPadding().imePadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().heightIn(min = 52.dp)
                ) { Text("Start game") }
            }
        }
    ) { padding ->
        SettingsBackdrop(Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!customGame) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(selected = !vsComputer, onClick = { vsComputer = false },
                            label = { Text("Pass & play") })
                        FilterChip(selected = vsComputer, onClick = { vsComputer = true },
                            label = { Text("Vs computer") })
                    }
                    Text("Players", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        (2..4).forEach { count ->
                            FilterChip(selected = playerCount == count, onClick = { playerCount = count },
                                label = { Text("$count players") })
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Your game", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${colorsToShow.size} players · ${if (currentSetup.mode == GameMode.TEAM) "Team" else "Single"}",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { customGame = false }) { Text("Quick setup") }
                    }
                }
                SeatPreview(colorsToShow, currentSetup.playerColors, currentSetup.mode == GameMode.TEAM,
                    currentSetup.playerNames, currentSetup.playerTypes)
                SettingsDestination("Settings", "Players, teams, colors & more", Icons.Default.Settings) {
                    showSettings = true
                }
            }
        }
    }
}

/** Game-specific pages are only exposed before starting a game. */
@Composable
internal fun GamePlayersSettings(setup: SetupState, onChange: (SetupState) -> Unit) {
    val seats = if (setup.mode == GameMode.TEAM) PlayerColor.entries else setup.activeColors
    SettingsCard {
        GameModeSection(setup.mode) { onChange(setup.copy(mode = it)) }
        if (setup.mode == GameMode.TEAM) {
            Text("Opposite corners play together. Both teammates must finish to win.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    SeatPreview(seats, setup.playerColors, setup.mode == GameMode.TEAM, setup.playerNames, setup.playerTypes)
    seats.forEach { color ->
        PlayerRow(color = color, tint = playerColor(color, setup.playerColors),
            name = setup.playerNames[color].orEmpty(), type = setup.playerTypes[color] ?: PlayerType.HUMAN,
            onTypeChange = { onChange(setup.copy(playerTypes = setup.playerTypes + (color to it))) },
            onNameChange = { onChange(setup.copy(playerNames = setup.playerNames + (color to it))) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GameAppearanceSettings(setup: SetupState, onChange: (SetupState) -> Unit) {
    val seats = if (setup.mode == GameMode.TEAM) PlayerColor.entries else setup.activeColors
    var selectedSeat by rememberSaveable { mutableStateOf(seats.first()) }
    val seatToEdit = selectedSeat.takeIf { it in seats } ?: seats.first()
    SeatPreview(seats, setup.playerColors, setup.mode == GameMode.TEAM, setup.playerNames, setup.playerTypes)
    SettingsCard {
        if (setup.mode == GameMode.FREE_FOR_ALL) {
            PlayerCountSection(setup.activeColors, setup.playerColors) { onChange(setup.copy(activeColors = it)) }
        } else {
            SettingsHeading("Four seats, two teams")
            Text("Team games use all four corners.", style = MaterialTheme.typography.bodyMedium)
        }
    }
    SettingsCard {
        SettingsHeading("Player colors")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            seats.forEach { seat ->
                FilterChip(selected = seat == seatToEdit, onClick = { selectedSeat = seat },
                    leadingIcon = { PlayerMarker(seat, playerColor(seat, setup.playerColors)) },
                    label = { Text(seatLabel(seat)) })
            }
        }
        PlayerColorSection(listOf(seatToEdit), setup.playerNames, setup.playerColors,
            onColorChange = { seat, selected ->
                val updated = setup.playerColors.toMutableMap()
                updated.entries.firstOrNull { it.key != seat && it.value == selected }?.key?.let {
                    updated[it] = updated[seat] ?: playerColor(seat)
                }
                updated[seat] = selected
                onChange(setup.copy(playerColors = updated))
            },
            onResetDefaults = { onChange(setup.copy(playerColors = defaultPlayerColors())) })
    }
}

@Composable
private fun PlayerMarker(seat: PlayerColor, tint: Color) {
    Box(Modifier.size(22.dp).background(tint, CircleShape)
        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), CircleShape)
        .semantics { contentDescription = "${seatLabel(seat)} color" })
}

@Composable
private fun SeatPreview(seats: List<PlayerColor>, palette: Map<PlayerColor, Color>, teams: Boolean,
    names: Map<PlayerColor, String> = emptyMap(),
    types: Map<PlayerColor, PlayerType> = emptyMap()) {
    val pieces = remember(seats) { seats.associateWith { color -> List(4) { Piece(it, color) } } }
    val corners = listOf(PlayerColor.RED to Alignment.TopStart, PlayerColor.BLUE to Alignment.TopEnd,
        PlayerColor.GREEN to Alignment.BottomStart, PlayerColor.YELLOW to Alignment.BottomEnd)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp)).border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))) {
            TabletopBoard(pieces = pieces, movable = emptySet(), palette = palette,
                animatedCells = emptyMap(), fromCells = emptyMap(), progress = 1f, onTap = {},
                reducedMotion = true, modifier = Modifier.fillMaxSize().clearAndSetSemantics {
                    contentDescription = "Board preview with ${seats.size} players"
                })
            corners.forEach { (seat, alignment) ->
                val active = seat in seats
                Box(Modifier.align(alignment).size(maxWidth * .4f)
                    .background(if (active) Color.Transparent else Color(0xFF211B26).copy(alpha = .92f))
                    .semantics { contentDescription = "${seatLabel(seat)}, ${if (active) "playing" else "empty"}" },
                    contentAlignment = Alignment.Center) {
                    if (!active) Text("Empty", color = Color(0xFFF8F0E4),
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        SettingsCard {
            listOf(listOf(PlayerColor.RED, PlayerColor.BLUE), listOf(PlayerColor.GREEN, PlayerColor.YELLOW)).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { seat ->
                        val active = seat in seats
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(10.dp).background(playerColor(seat, palette), CircleShape))
                                Text(if (active) names[seat]?.takeIf { it.isNotBlank() }
                                    ?: "Player-${seat.ordinal + 1}" else "Empty",
                                    style = MaterialTheme.typography.labelLarge)
                            }
                            val team = if (seat == PlayerColor.RED || seat == PlayerColor.YELLOW) "Team 1" else "Team 2"
                            Text(if (!active) seatLabel(seat) else if (teams) team
                                else if (types[seat] == PlayerType.BOT) "Computer" else "Person",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ── Sub-components ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameModeSection(selected: GameMode, onSelect: (GameMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GameMode.entries.forEach { mode ->
                val label = when (mode) {
                    GameMode.FREE_FOR_ALL -> "Single"
                    GameMode.TEAM        -> "Team"
                }
                FilterChip(
                    selected = selected == mode,
                    onClick  = { onSelect(mode) },
                    label    = { Text(label) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor     = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}

@Composable
private fun PlayerCountSection(
    activeColors: List<PlayerColor>,
    palette: Map<PlayerColor, Color>,
    onColorsChange: (List<PlayerColor>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Seats (${activeColors.size})", style = MaterialTheme.typography.titleMedium)
        listOf(listOf(PlayerColor.RED, PlayerColor.BLUE), listOf(PlayerColor.GREEN, PlayerColor.YELLOW)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { seat ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        leadingIcon = { PlayerMarker(seat, playerColor(seat, palette)) },
                        selected = seat in activeColors,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer),
                        trailingIcon = if (seat in activeColors) ({
                            Icon(Icons.Default.Check, "Selected", Modifier.size(18.dp))
                        }) else null,
                        onClick = {
                            val next = if (seat in activeColors) {
                                if (activeColors.size > 2) activeColors - seat else activeColors
                            } else activeColors + seat
                            onColorsChange(next.sortedBy { it.ordinal })
                        },
                        label = { Text(seatLabel(seat)) }
                    )
                }
            }
        }
        Text("Pick 2–4 seats", style = MaterialTheme.typography.bodySmall)
    }
}

private fun seatLabel(seat: PlayerColor): String = when (seat) {
    PlayerColor.RED -> "Top left"
    PlayerColor.BLUE -> "Top right"
    PlayerColor.YELLOW -> "Bottom right"
    PlayerColor.GREEN -> "Bottom left"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerColorSection(
    seats: List<PlayerColor>,
    playerNames: Map<PlayerColor, String>,
    playerColors: Map<PlayerColor, Color>,
    onColorChange: (PlayerColor, Color) -> Unit,
    onResetDefaults: () -> Unit
) {
    val selectableColors = remember {
        listOf(
            "Red" to LudoRed, "Blue" to LudoBlue,
            "Yellow" to LudoYellow, "Green" to LudoGreen,
            "Scarlet" to Color.hsv(0f, .78f, .86f),
            "Orange" to Color.hsv(30f, .80f, .90f),
            "Gold" to Color.hsv(50f, .75f, .92f),
            "Lime" to Color.hsv(85f, .70f, .84f),
            "Bright green" to Color.hsv(120f, .70f, .82f),
            "Teal" to Color.hsv(165f, .75f, .78f),
            "Sky blue" to Color.hsv(200f, .75f, .90f),
            "Indigo" to Color.hsv(235f, .73f, .88f),
            "Purple" to Color.hsv(275f, .70f, .84f),
            "Pink" to Color.hsv(310f, .70f, .84f),
            "Rose" to Color.hsv(340f, .72f, .88f),
            "Brown" to Color.hsv(15f, .55f, .72f)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Colors", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = onResetDefaults) {
                Text("Reset all colors")
            }
        }

        seats.forEach { seat ->
            val selected = playerColors[seat] ?: playerColor(seat)
            val displayName = playerNames[seat]?.takeIf { it.isNotBlank() } ?: "Player-${seat.ordinal + 1}"
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(selected)
                            .border(1.dp, Color.Black.copy(alpha = 0.25f), CircleShape)
                    )
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectableColors.forEach { (label, option) ->
                        val isSelected = option == selected
                        Box(
                            modifier = Modifier.size(48.dp)
                                .semantics { contentDescription = "$displayName, $label" }
                                .selectable(selected = isSelected, role = Role.RadioButton,
                                    onClick = { onColorChange(seat, option) }),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(option)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                contentAlignment = Alignment.Center) {
                                if (isSelected) Icon(Icons.Default.Check, contentDescription = null,
                                    tint = OnSurface, modifier = Modifier.size(24.dp)
                                        .background(Color.White, CircleShape).padding(2.dp))
                            }
                        }
                    }
                }
            }
        }

        Text(
            "Picking a used color swaps it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerRow(
    color: PlayerColor,
    tint: Color,
    name: String,
    type: PlayerType,
    onTypeChange: (PlayerType) -> Unit,
    onNameChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(22.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerMarker(color, tint)
                Text(seatLabel(color), style = MaterialTheme.typography.titleSmall)
            }
            OutlinedTextField(
                value = name,
                onValueChange = { onNameChange(it.take(MAX_PLAYER_NAME_LENGTH)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Name") },
                placeholder = { Text("Player-${color.ordinal + 1}") }
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = type == PlayerType.HUMAN,
                    leadingIcon = { Icon(Icons.Default.Person, null, Modifier.size(20.dp)) },
                    onClick = { onTypeChange(PlayerType.HUMAN) }, label = { Text("Person") })
                FilterChip(selected = type == PlayerType.BOT,
                    leadingIcon = { Icon(Icons.Default.SmartToy, null, Modifier.size(20.dp)) },
                    onClick = { onTypeChange(PlayerType.BOT) }, label = { Text("Computer") })
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

fun playerColor(color: PlayerColor, palette: Map<PlayerColor, Color>? = null): Color {
    val custom = palette?.get(color)
    if (custom != null) return custom

    return when (color) {
        PlayerColor.RED    -> LudoRed
        PlayerColor.BLUE   -> LudoBlue
        PlayerColor.YELLOW -> LudoYellow
        PlayerColor.GREEN  -> LudoGreen
    }
}

fun playerColorLight(color: PlayerColor, palette: Map<PlayerColor, Color>? = null): Color {
    if (palette == null) {
        return when (color) {
            PlayerColor.RED    -> LudoRedLight
            PlayerColor.BLUE   -> LudoBlueLight
            PlayerColor.YELLOW -> LudoYellowLight
            PlayerColor.GREEN  -> LudoGreenLight
        }
    }

    return lerp(playerColor(color, palette), Color.White, 0.72f)
}
