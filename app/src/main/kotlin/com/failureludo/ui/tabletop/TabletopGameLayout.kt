package com.failureludo.ui.tabletop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.failureludo.engine.*

@Composable
fun TabletopGameLayout(
    state: GameState, palette: Map<PlayerColor, Color>, diceValue: Int?, rollId: Long,
    rolling: Boolean, reducedMotion: Boolean, canRoll: Boolean, inputBlocked: Boolean,
    onRoll: () -> Unit, board: @Composable (Modifier) -> Unit, modifier: Modifier = Modifier,
    statusOverride: String? = null,
    showAvatars: Boolean = false, actionCountdown: AvatarCountdown? = null
) {
    val status = statusOverride ?: when {
        rolling -> "Rolling…"
        inputBlocked -> "Moving…"
        state.turnPhase == TurnPhase.GAME_OVER -> "Game complete"
        state.turnPhase == TurnPhase.NO_MOVES_AVAILABLE -> "No legal moves · next turn"
        state.currentPlayer.type == PlayerType.BOT -> "Thinking…"
        state.turnPhase == TurnPhase.WAITING_FOR_PIECE_SELECTION ->
            if (reducedMotion) "Choose a highlighted pawn" else "Choose a pulsing pawn"
        else -> "Tap your corner die to roll"
    }
    val players = state.players.associateBy { it.color }
    @Composable fun Seat(color: PlayerColor, modifier: Modifier = Modifier) {
        val player = players[color]
        val active = player?.isActive == true && player.id == state.currentPlayer.id
        CornerPlayer(player, color, active, palette[color] ?: Color.Gray, state.mode,
            value = if (active) diceValue else null,
            rollId = if (active) rollId else 0L, rolling = active && rolling,
            reducedMotion = reducedMotion, enabled = active && canRoll, onRoll = onRoll,
            modifier = modifier, showAvatar = showAvatars,
            countdown = actionCountdown?.takeIf { it.color == color })
    }
    @Composable fun Status(modifier: Modifier = Modifier) {
        Text("${state.currentPlayer.name} · $status", modifier = modifier,
            color = TabletopStyle.Muted, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
    // The board itself supplies its edge. No thick frame or clipping of oversized pawns.
    BoxWithConstraints(modifier.padding(horizontal = 4.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        if (maxWidth > maxHeight * 1.25f) {
            val boardSide = minOf((maxHeight - 28.dp).coerceAtLeast(0.dp),
                (maxWidth - 224.dp).coerceAtLeast(0.dp))
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f).height(boardSide), verticalArrangement = Arrangement.SpaceBetween) {
                    Seat(PlayerColor.RED)
                    Seat(PlayerColor.GREEN)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    board(Modifier.size(boardSide))
                    Status(Modifier.width(boardSide).padding(top = 4.dp))
                }
                Column(Modifier.weight(1f).height(boardSide), verticalArrangement = Arrangement.SpaceBetween) {
                    Seat(PlayerColor.BLUE)
                    Seat(PlayerColor.YELLOW)
                }
            }
        } else {
            // Measure text/player rows first, including font scaling, and give all remaining
            // space to the square board. Four seat positions remain stable in 2/3-player games.
            Layout(content = {
                Seat(PlayerColor.RED)
                Seat(PlayerColor.BLUE)
                Seat(PlayerColor.GREEN)
                Seat(PlayerColor.YELLOW)
                Status()
                board(Modifier)
            }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
                val gap = 4.dp.roundToPx()
                val seatWidth = ((constraints.maxWidth - gap) / 2).coerceAtLeast(0)
                val seats = measurables.take(4).map {
                    it.measure(Constraints(maxWidth = seatWidth, maxHeight = constraints.maxHeight))
                }
                val statusLine = measurables[4].measure(Constraints(maxWidth = constraints.maxWidth))
                val topHeight = maxOf(seats[0].height, seats[1].height)
                val bottomHeight = maxOf(seats[2].height, seats[3].height)
                val side = minOf(constraints.maxWidth,
                    (constraints.maxHeight - topHeight - bottomHeight - statusLine.height - gap * 3).coerceAtLeast(0))
                val boardPlaceable = measurables[5].measure(Constraints.fixed(side, side))
                val totalHeight = topHeight + side + bottomHeight + statusLine.height + gap * 3
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val top = ((constraints.maxHeight - totalHeight) / 2).coerceAtLeast(0)
                    seats[0].placeRelative(0, top)
                    seats[1].placeRelative(constraints.maxWidth - seats[1].width, top)
                    boardPlaceable.placeRelative((constraints.maxWidth - side) / 2, top + topHeight + gap)
                    val bottom = top + topHeight + side + gap * 2
                    seats[2].placeRelative(0, bottom)
                    seats[3].placeRelative(constraints.maxWidth - seats[3].width, bottom)
                    statusLine.placeRelative((constraints.maxWidth - statusLine.width) / 2,
                        bottom + bottomHeight + gap)
                }
            }
        }
    }
}

@Composable
private fun CornerPlayer(
    player: Player?, color: PlayerColor, active: Boolean, tint: Color, mode: GameMode,
    value: Int?, rollId: Long, rolling: Boolean, reducedMotion: Boolean,
    enabled: Boolean, onRoll: () -> Unit, modifier: Modifier = Modifier,
    showAvatar: Boolean = false, countdown: AvatarCountdown? = null
) {
    val occupied = player?.isActive == true
    val right = color == PlayerColor.BLUE || color == PlayerColor.YELLOW
    val shape = RoundedCornerShape(12.dp)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        @Composable fun Die() {
            Box(Modifier.size(48.dp).background(TabletopStyle.Panel, shape)
                .border(1.dp, if (active) tint else TabletopStyle.Muted.copy(alpha = .25f), shape),
                contentAlignment = Alignment.Center) {
                if (active) TabletopDice(value, rollId, rolling, reducedMotion, enabled, onRoll,
                    Modifier.size(48.dp).semantics { contentDescription = "${player?.name} die" })
            }
        }
        if (!right) Die()
        Row(Modifier.weight(1f).background(TabletopStyle.Panel.copy(alpha = .85f), shape)
            .border(1.dp, if (active) tint else tint.copy(alpha = .24f), shape)
            .padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (showAvatar) 4.dp else 0.dp)) {
            if (showAvatar && occupied) PlayerAvatar(player!!.name, tint, countdown, reducedMotion)
            Column(Modifier.weight(1f)) {
                Text(if (occupied) player!!.name else "Empty seat", color = if (occupied) TabletopStyle.Paper else TabletopStyle.Muted,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = if (!occupied) "—" else buildString {
                    append(if (active) "Playing" else if (player!!.type == PlayerType.BOT) "Bot" else "Human")
                    append(" · ${player!!.finishedPieceCount}/4")
                    if (mode == GameMode.TEAM) append(" · T${color.teamIndex + 1}")
                }
                Text(detail, color = if (active) tint else TabletopStyle.Muted, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (right) Die()
    }
}
