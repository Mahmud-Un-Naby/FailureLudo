package com.failureludo.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.failureludo.engine.GameMode
import com.failureludo.online.RoomStatus
import com.failureludo.viewmodel.WaitingRoomViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaitingRoomScreen(roomId: String, viewModel: WaitingRoomViewModel, onGameStarting: (String) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var confirmLeave by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val room = state.room?.takeIf { it.code == roomId }
    BackHandler { confirmLeave = true }
    LaunchedEffect(room?.status, state.busy) {
        if (room?.status == RoomStatus.PLAYING || room?.status == RoomStatus.FINISHED) onGameStarting(roomId)
        else if (!state.busy && state.uid != null && room == null && !state.pending && state.error == null) onBack()
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Waiting room") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Lobby") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OnlineConnectionStatus(state, viewModel::retry)
            if (room != null) {
                Text(room.code, style = MaterialTheme.typography.headlineLarge)
                Text(if (room.mode == GameMode.TEAM) "Team game" else "Free for all")
                Button(onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Join my Failure Ludo room: ${room.code}")
                    }, "Share room code"))
                }) { Text("Share code") }
                room.members.forEach { member ->
                    Text("${member.name} · ${member.color.displayName}" + if (member.uid == room.hostUid) " · Host" else "")
                }
                if (state.uid == room.hostUid) Button(onClick = viewModel::startGame,
                    enabled = state.canAct && room.members.size >= 2 && (room.mode != GameMode.TEAM || room.members.size == 4)) {
                    Text("Start game")
                } else Text("Waiting for the host to start…")
                TextButton(onClick = { confirmLeave = true }, enabled = state.canAct) { Text("Leave room") }
            }
        }
    }
    if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false }, title = { Text("Leave room?") },
        text = { Text("If you are the host, another player becomes host. An empty room closes.") },
        confirmButton = { TextButton(enabled = state.canAct, onClick = { confirmLeave = false; viewModel.leaveRoom() }) { Text("Leave") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } })
}
