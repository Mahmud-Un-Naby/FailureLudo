package com.failureludo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.failureludo.data.online.OnlineSessionState
import com.failureludo.engine.GameMode
import com.failureludo.online.DEFAULT_ACTION_TIMEOUT_MILLIS
import com.failureludo.online.OnlineRoom
import com.failureludo.online.RoomStatus
import com.failureludo.viewmodel.OnlineLobbyViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineLobbyScreen(viewModel: OnlineLobbyViewModel, onBack: () -> Unit, onRoomReady: (OnlineRoom) -> Unit) {
    val state by viewModel.state.collectAsState()
    var code by rememberSaveable { mutableStateOf("") }
    var players by rememberSaveable { mutableIntStateOf(4) }
    var team by rememberSaveable { mutableStateOf(false) }
    var entering by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.room?.code, state.busy, state.pending) {
        if (entering && !state.busy && !state.pending && state.room != null) {
            entering = false
            onRoomReady(state.room!!)
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Play online") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Back") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Play with friends. No Google account needed.")
            OnlineConnectionStatus(state, viewModel::retry)
            val room = state.room
            if (room != null) {
                Text("Room ${room.code} · ${room.members.size}/${room.maxPlayers} players")
                if (room.canForget(state.uid)) {
                    Button(onClick = viewModel::forgetRoom, enabled = !state.busy && !state.pending) { Text("New room") }
                    if (room.status == RoomStatus.FINISHED) TextButton(onClick = { onRoomReady(room) }) { Text("View result") }
                    else if (room.hasResigned(state.uid)) {
                        Text("Your seat was forfeited. You can watch this game.")
                        TextButton(onClick = { onRoomReady(room) }) { Text("Watch game") }
                    }
                } else Button(onClick = { onRoomReady(room) }, enabled = !state.busy) { Text("Resume room") }
            } else if (state.configured) {
                Text("Create a room", style = MaterialTheme.typography.titleLarge)
                Text("${DEFAULT_ACTION_TIMEOUT_MILLIS / 1000} seconds for each roll and pawn choice. A bot covers missed actions. After two minutes AFK, you lose your seat; in team games, your teammate takes over.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 4).forEach { count ->
                        FilterChip(selected = players == count, enabled = !team && !state.busy && !state.pending,
                            onClick = { players = count }, label = { Text("$count players") })
                    }
                }
                Row {
                    Checkbox(checked = team, enabled = !state.busy && !state.pending,
                        onCheckedChange = { team = it; if (it) players = 4 })
                    Text("Team game · 4 players", Modifier.padding(top = 12.dp))
                }
                Button(onClick = { entering = true; viewModel.createRoom(players, if (team) GameMode.TEAM else GameMode.FREE_FOR_ALL) },
                    enabled = !state.busy && !state.pending && state.uid != null) { Text("Create room") }
                HorizontalDivider()
                OutlinedTextField(value = code, onValueChange = { code = it.uppercase(Locale.ROOT).filter(Char::isLetterOrDigit).take(8) },
                    label = { Text("Eight-character room code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { entering = true; viewModel.joinRoom(code) },
                    enabled = !state.busy && !state.pending && state.uid != null && code.length == 8) { Text("Join room") }
            }
        }
    }
}

@Composable
internal fun OnlineConnectionStatus(state: OnlineSessionState, onRetry: () -> Unit) {
    when {
        state.busy -> LinearProgressIndicator(Modifier.fillMaxWidth())
        !state.configured -> Text("Online play is not available in this build yet. Offline games are ready to play.")
        state.error != null || state.pending || (state.room != null && !state.connected) -> {
            Text(state.error ?: if (state.pending) "An action is waiting for confirmation." else "Reconnecting to your room…")
            if (state.pending) Text("Retry will recover the same action.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) { Text("Retry connection") }
        }
    }
}
