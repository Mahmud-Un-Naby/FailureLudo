package com.failureludo.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.failureludo.engine.*
import com.failureludo.feedback.FeedbackEvent
import com.failureludo.feedback.GameFeedbackManager
import com.failureludo.online.OnlineRoom
import com.failureludo.ui.tabletop.*
import com.failureludo.viewmodel.OnlineGameViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

@Composable
fun OnlineGameBoardScreen(roomId: String, viewModel: OnlineGameViewModel, onGameOver: () -> Unit, onQuit: () -> Unit) {
    val session by viewModel.state.collectAsState()
    val settings by viewModel.feedbackSettings.collectAsState()
    val palette by viewModel.palette.collectAsState()
    val homeChoice by viewModel.homeEntryChoice.collectAsState()
    val currentSettings by rememberUpdatedState(settings)
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val feedback = remember(context) { GameFeedbackManager(context) }
    LaunchedEffect(feedback, settings) { feedback.prepare(settings) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }
    var presented by remember(roomId) { mutableStateOf<OnlineRoom?>(null) }
    var animating by remember(roomId) { mutableStateOf(false) }
    var rolling by remember(roomId) { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(1f) }
    val animated = remember { mutableStateMapOf<Pair<PlayerColor, Int>, Pair<Int, Int>>() }
    val from = remember { mutableStateMapOf<Pair<PlayerColor, Int>, Pair<Int, Int>>() }
    var captured by remember { mutableStateOf<Set<Pair<PlayerColor, Int>>>(emptySet()) }
    var captureCells by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    val captureProgress = remember { Animatable(1f) }
    var showExit by remember { mutableStateOf(false) }
    var stack by remember { mutableStateOf<Pair<Long, List<StackMoveOption>>?>(null) }
    BackHandler { showExit = true }

    // Animate only consecutive confirmed revisions. Reconnect gaps snap to the server
    // snapshot rather than inventing intermediate movement or replaying old sounds.
    LaunchedEffect(roomId, viewModel) {
        viewModel.state.map { it.room?.takeIf { room -> room.code == roomId && room.game != null } }
            .filterNotNull().distinctUntilChangedBy { it.revision }.collect { next ->
                val previous = presented
                val game = next.game!!
                val consecutive = previous != null && next.revision == previous.revision + 1
                presented = next
                if (!consecutive) return@collect
                animating = true
                try {
                    if (next.lastAction?.type == "ROLL") {
                        rolling = true
                        feedback.emitSound(FeedbackEvent.DICE_ROLL, currentSettings)
                        delay(if (currentSettings.reducedMotion) 100 else 640)
                        rolling = false
                    }
                    val old = previous!!.game!!
                    val positions = old.players.flatMap { player -> player.pieces.map { (it.color to it.id) to it.position } }.toMap()
                    val plan = buildAnimationPlan(game, positions, old.moveCounter)
                    if (plan.paths.isNotEmpty()) {
                        val timing = PawnAnimationTiming(currentSettings)
                        captured = plan.capturedKeys
                        fun frame(step: Int, value: Float) {
                            plan.paths.forEach { (key, cells) ->
                                from[key] = cells.getOrElse((step - 1).coerceAtLeast(0)) { cells.last() }
                                animated[key] = cells.getOrElse(step) { cells.last() }
                            }
                            progress = value
                        }
                        frame(0, 1f)
                        val forwardLast = (plan.movingPieceStepCount - 1).coerceAtLeast(0)
                        val last = plan.paths.maxOf { it.second.lastIndex }
                        animatePawnPath(1, forwardLast, timing.stepDurationMillis(plan, 1, false), currentSettings.reducedMotion,
                            onFrame = { frame(it.stepIndex, it.progress) }, onLanding = { step ->
                                feedback.emitSound(if (plan.hasCapture && step == forwardLast) FeedbackEvent.CAPTURE else FeedbackEvent.PIECE_MOVE, currentSettings)
                            })
                        if (plan.hasCapture) {
                            if (currentSettings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            captureCells = plan.capturedKeys.mapNotNull { animated[it] }
                            captureProgress.snapTo(0f)
                            captureProgress.animateTo(1f, tween(if (currentSettings.reducedMotion) 1 else 240))
                        }
                        animatePawnPath(forwardLast + 1, last, timing.stepDurationMillis(plan, forwardLast + 1, false),
                            currentSettings.reducedMotion, onFrame = { frame(it.stepIndex, it.progress) })
                    }
                    val events = game.eventLog.takeLast(next.lastAction?.eventCount ?: 0)
                    if (events.any { it is GameEvent.PieceFinished }) feedback.emitSound(FeedbackEvent.PIECE_FINISH, currentSettings)
                    if (events.any { it is GameEvent.ExtraRollGranted }) feedback.emitSound(FeedbackEvent.EXTRA_ROLL, currentSettings)
                    if (events.any { it is GameEvent.TurnSkipped || it is GameEvent.ConsecutiveSixesForfeit }) feedback.emitSound(FeedbackEvent.TURN_SKIP, currentSettings)
                    if (game.isGameOver && !old.isGameOver) feedback.emitSound(FeedbackEvent.WIN, currentSettings)
                } finally {
                    animated.clear(); from.clear(); captured = emptySet(); captureCells = emptyList()
                    rolling = false; animating = false; progress = 1f
                }
            }
    }

    val room = presented
    val game = room?.game
    val latest = session.room
    val caughtUp = room != null && room.revision == latest?.revision
    val myTurn = game != null && room.members.find { it.uid == session.uid }?.color == game.currentPlayer.color
    val controls = session.canAct && caughtUp && !animating && myTurn && game?.isGameOver == false
    val movable = if (controls && game?.turnPhase == TurnPhase.WAITING_FOR_PIECE_SELECTION)
        game.movablePieces.map { it.color to it.id }.toSet() else emptySet()
    Column(Modifier.fillMaxSize().background(TabletopStyle.Ink).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { showExit = true }) { Text("Lobby", color = TabletopStyle.Paper) }
            Text("Room $roomId", color = TabletopStyle.Paper, modifier = Modifier.padding(12.dp))
        }
        Surface(color = TabletopStyle.Panel, contentColor = TabletopStyle.Paper) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { OnlineConnectionStatus(session, viewModel::retry) }
        }
        if (game == null) {
            Text("Loading your game…", color = TabletopStyle.Paper, modifier = Modifier.padding(24.dp))
        } else {
            val actor = if (animating) room.members.find { it.uid == room.lastAction?.uid } else null
            val display = if (actor != null) game.copy(currentPlayerIndex = game.players.indexOfFirst { it.color == actor.color }) else game
            Text(if (!session.connected) "Waiting for connection" else if (myTurn) "Your turn" else "Waiting for ${game.currentPlayer.name}",
                color = TabletopStyle.Muted, modifier = Modifier.padding(horizontal = 16.dp))
            TabletopGameLayout(state = display, palette = palette,
                diceValue = if (rolling) room.lastAction?.dice else game.diceByPlayer[game.currentPlayer.id],
                rollId = room.revision, rolling = rolling, reducedMotion = settings.reducedMotion,
                canRoll = controls && game.turnPhase == TurnPhase.WAITING_FOR_ROLL,
                inputBlocked = animating || session.busy || session.pending, onRoll = viewModel::rollDice,
                statusOverride = when {
                    !session.connected -> "Waiting for connection"
                    session.pending || session.busy -> "Waiting for confirmation"
                    !myTurn && !animating -> "Waiting for opponent"
                    else -> null
                },
                modifier = Modifier.fillMaxWidth().weight(1f), board = { modifier ->
                    TabletopBoard(pieces = game.players.filter { it.isActive }.associate { it.color to it.pieces },
                        movable = movable, palette = palette, animatedCells = animated, fromCells = from,
                        progress = progress, reducedMotion = settings.reducedMotion, capturedKeys = captured,
                        captureCells = captureCells, captureProgress = captureProgress.value,
                        onTap = { tapped ->
                            if (controls) {
                                val decision = resolveStackTapDecision(tapped, game.mode)
                                decision.autoPiece?.let(viewModel::selectPiece)
                                if (decision.options.isNotEmpty()) stack = room.revision to decision.options
                            }
                        }, modifier = modifier)
                })
        }
    }
    stack?.takeIf { controls && it.first == latest?.revision }?.let { (_, options) ->
        AlertDialog(onDismissRequest = { stack = null }, title = { Text("Choose pawn move") },
            text = {
                Column {
                    options.forEach { option ->
                        TextButton(onClick = { stack = null; viewModel.selectPiece(option.piece) }) { Text(option.label) }
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { stack = null }) { Text("Cancel") } })
    }
    homeChoice?.takeIf { controls && it.first == latest?.revision }?.let { (_, piece) ->
        val current = latest!!.game!!
        val canDefer = GameRules.canDeferHomeEntry(piece, current.lastDice!!.value, piece.color, current.players, current.mode)
        AlertDialog(onDismissRequest = viewModel::dismissHomeEntryChoice, title = { Text("Choose pawn path") },
            text = { Column {
                TextButton(onClick = { viewModel.resolveHomeEntryChoice(true) }) { Text("Enter finish") }
                TextButton(enabled = canDefer, onClick = { viewModel.resolveHomeEntryChoice(false) }) { Text("Keep circulating") }
            } }, confirmButton = {}, dismissButton = { TextButton(onClick = viewModel::dismissHomeEntryChoice) { Text("Cancel") } })
    }
    if (game?.isGameOver == true && !animating && caughtUp) {
        AlertDialog(onDismissRequest = {}, title = { Text("Game over") },
            text = { Text(game.players.filter { it.id in game.winners.orEmpty() }.joinToString(" & ") { it.name } + " wins!") },
            confirmButton = { TextButton(onClick = onGameOver) { Text("Back to lobby") } })
    } else if (showExit) {
        AlertDialog(onDismissRequest = { showExit = false }, title = { Text("Return to lobby?") },
            text = { Text("Your seat stays in this game. Resume it from the lobby; other players may wait for your turn.") },
            confirmButton = { TextButton(onClick = onQuit) { Text("Lobby") } },
            dismissButton = { TextButton(onClick = { showExit = false }) { Text("Stay") } })
    }
}
