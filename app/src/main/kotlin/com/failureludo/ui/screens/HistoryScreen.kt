package com.failureludo.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.failureludo.ui.theme.gardenBackground
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.failureludo.data.history.FlnGameStatus
import com.failureludo.data.history.GameHistoryImportResult
import com.failureludo.data.history.GameHistoryRecord
import com.failureludo.data.history.GameHistoryRecordKind
import com.failureludo.ui.theme.Background
import com.failureludo.ui.theme.OnPrimary
import com.failureludo.ui.theme.Primary
import com.failureludo.viewmodel.GameViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit,
    onOpenGame: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<GameHistoryRecord?>(null) }
    val records by viewModel.historyRecords.collectAsState()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingExportGameId by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            errorMessage = "Import canceled."
            return@rememberLauncherForActivityResult
        }

        viewModel.importFlnFromUri(uri) { result ->
            errorMessage = importResultMessage(result)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val gameId = pendingExportGameId
        pendingExportGameId = null

        if (uri == null || gameId == null) {
            errorMessage = "Export canceled."
            return@rememberLauncherForActivityResult
        }

        viewModel.exportHistoryRecordToUri(gameId, uri) { exported ->
            errorMessage = if (exported) {
                "FLN exported successfully."
            } else {
                "Could not export this game record."
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshHistoryRecords()
    }

    pendingDelete?.let { record ->
        DeleteSavedGameDialog(record.playerNames.joinToString(" · ").ifBlank { "Saved game" },
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                viewModel.deleteHistoryRecord(record.gameId)
            })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved games") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    TextButton(onClick = { importLauncher.launch(arrayOf("text/plain", "application/octet-stream")) }) {
                        Text("Import", color = Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .gardenBackground()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (errorMessage != null) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (records.isEmpty()) {
                SavedGamesEmptyState {
                    importLauncher.launch(arrayOf("text/plain", "application/octet-stream"))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(records, key = { it.gameId }) { record ->
                        HistoryRecordCard(
                            record = record,
                            onOpen = {
                                val openAction: (String, (Boolean) -> Unit) -> Unit = if (record.status == FlnGameStatus.FINISHED) {
                                    viewModel::openHistoryRecordForReplay
                                } else {
                                    viewModel::openHistoryRecord
                                }

                                openAction(record.gameId) { opened ->
                                    if (opened) {
                                        errorMessage = null
                                        onOpenGame()
                                    } else {
                                        errorMessage = "Could not open this game record."
                                    }
                                }
                            },
                            onReplay = {
                                viewModel.openHistoryRecordForReplay(record.gameId) { opened ->
                                    if (opened) {
                                        errorMessage = null
                                        onOpenGame()
                                    } else {
                                        errorMessage = "Could not open replay for this game."
                                    }
                                }
                            },
                            onExport = {
                                pendingExportGameId = record.gameId
                                exportLauncher.launch("failureludo-${record.gameId.take(8)}.fln")
                            },
                            onDelete = {
                                pendingDelete = record
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordCard(
    record: GameHistoryRecord,
    onOpen: () -> Unit,
    onReplay: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val statusLabel = when {
        record.kind == GameHistoryRecordKind.UNSUPPORTED -> "Unsupported game"
        record.status == FlnGameStatus.ACTIVE -> "In progress"
        record.status == FlnGameStatus.FINISHED -> "Finished"
        else -> "Saved game"
    }

    val openLabel = if (record.status == FlnGameStatus.ACTIVE) {
        "Resume"
    } else if (record.status == FlnGameStatus.FINISHED) {
        "Replay"
    } else {
        "Open"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = record.playerNames.joinToString(" · ").ifBlank { "Saved game" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodyMedium
            )


            Text(
                text = "Updated: ${formatEpoch(record.updatedAtEpochMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            if (record.kind == GameHistoryRecordKind.PLAYABLE) {
                val moveCount = record.playableDocument?.moves?.size ?: 0
                Text(
                    text = "Moves: $moveCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            if (record.kind == GameHistoryRecordKind.UNSUPPORTED && record.unsupportedDetectedVersion != null) {
                Text(
                    text = "Detected FLN version: ${record.unsupportedDetectedVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (record.kind == GameHistoryRecordKind.PLAYABLE) {
                    Button(onClick = onOpen, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(openLabel)
                    }
                    if (record.status != FlnGameStatus.FINISHED) {
                        OutlinedButton(onClick = onReplay,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Replay") }
                    }
                } else {
                    Text("Cannot play this version", modifier = Modifier.weight(1f))
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Game options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (record.kind == GameHistoryRecordKind.PLAYABLE) {
                            DropdownMenuItem(text = { Text("Export") }, onClick = {
                                showMenu = false
                                onExport()
                            })
                        }
                        DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onDelete() })
                    }
                }
            }
        }
    }
}

private fun importResultMessage(result: GameHistoryImportResult): String {
    return when (result) {
        is GameHistoryImportResult.Imported -> {
            "Imported game ${result.record.gameId.take(8)}..."
        }

        is GameHistoryImportResult.Unsupported -> {
            "Imported as unsupported legacy record (${result.record.unsupportedDetectedVersion})."
        }

        is GameHistoryImportResult.Invalid -> {
            "Import failed: ${result.reason}"
        }
    }
}

private fun formatEpoch(epochMs: Long): String {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return formatter.format(Date(epochMs))
}


@Composable
internal fun SavedGamesEmptyState(onImport: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Text("No saved games yet", style = MaterialTheme.typography.headlineSmall)
        Text("Import a game to continue playing.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onImport, modifier = Modifier.heightIn(min = 52.dp)) { Text("Import game") }
    }
}

@Composable
internal fun DeleteSavedGameDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Delete saved game?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text("This saved game will be removed.")
        } },
        confirmButton = { TextButton(onClick = onConfirm) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
