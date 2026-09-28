package com.failureludo.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.failureludo.data.online.RoomPlayer
import com.failureludo.ui.theme.*
import com.failureludo.viewmodel.WaitingRoomState
import com.failureludo.viewmodel.WaitingRoomViewModel
import kotlinx.coroutines.flow.collectLatest

private val colorMap = mapOf(
    "RED"    to LudoRed,
    "BLUE"   to LudoBlue,
    "YELLOW" to LudoYellow,
    "GREEN"  to LudoGreen
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaitingRoomScreen(
    roomId: String,
    viewModel: WaitingRoomViewModel,
    onGameStarting: (roomId: String) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(roomId) { viewModel.init(roomId) }
    BackHandler { viewModel.leaveRoom(onBack) }

    LaunchedEffect(state) {
        if (state is WaitingRoomState.GameStarting) {
            onGameStarting((state as WaitingRoomState.GameStarting).roomId)
        }
        if (state is WaitingRoomState.Disbanded) onBack()
    }

    LaunchedEffect(Unit) {
        viewModel.errors.collectLatest { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Waiting Room") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.leaveRoom(onBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Leave")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = OnPrimary,
                    navigationIconContentColor = OnPrimary
                )
            )
        },
        containerColor = Background
    ) { padding ->
        when (val s = state) {
            is WaitingRoomState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is WaitingRoomState.Waiting -> {
                val room = s.room
                val isHost = room.hostUid == s.currentUid

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // ── Room code display ─────────────────────────────────────
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Room Code",
                                style = MaterialTheme.typography.labelMedium,
                                color = Secondary
                            )
                            Text(
                                text = room.roomCode,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 8.sp,
                                color = Primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(room.roomCode))
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy", style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "Join my Ludo game! Code: ${room.roomCode}")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share room code"))
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = "Share",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Share", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    // ── Players list ──────────────────────────────────────────
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Players (${room.players.size}/${room.maxPlayers})",
                                style = MaterialTheme.typography.titleSmall,
                                color = Secondary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(room.players) { player ->
                                    PlayerRow(player)
                                }
                                // Empty slots
                                val emptySlots = room.maxPlayers - room.players.size
                                items(emptySlots) {
                                    EmptySlotRow()
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // ── Start game button (host only) ─────────────────────────
                    if (isHost) {
                        Button(
                            onClick = { viewModel.startGame() },
                            enabled = room.hasMinPlayers,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Text(
                                text = if (room.hasMinPlayers) "Start Game" else "Waiting for players…",
                                style = MaterialTheme.typography.titleMedium,
                                color = OnPrimary
                            )
                        }
                    } else {
                        Text(
                            text = "Waiting for the host to start…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Secondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            is WaitingRoomState.GameStarting -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Primary)
                        Spacer(Modifier.height(12.dp))
                        Text("Starting game…", color = Secondary)
                    }
                }
            }

            else -> {} // Disbanded is handled by LaunchedEffect above
        }
    }
}

@Composable
private fun PlayerRow(player: RoomPlayer) {
    val dotColor = colorMap[player.color] ?: Color.Gray
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Color dot (empty if color not yet assigned)
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (player.color.isNotEmpty()) dotColor else Color.Transparent)
                .then(
                    if (player.color.isEmpty()) Modifier.background(
                        Color.Gray.copy(alpha = 0.3f), CircleShape
                    ) else Modifier
                )
        )
        Icon(
            imageVector = if (player.platform == "android") Icons.Default.PhoneAndroid else Icons.Default.Web,
            contentDescription = player.platform,
            tint = Secondary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = player.name,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurface,
            modifier = Modifier.weight(1f)
        )
        if (player.isHost) {
            Text(
                text = "HOST",
                style = MaterialTheme.typography.labelSmall,
                color = Primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EmptySlotRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.2f))
        )
        Text(
            text = "Waiting for player…",
            style = MaterialTheme.typography.bodyMedium,
            color = Secondary.copy(alpha = 0.5f)
        )
    }
}
