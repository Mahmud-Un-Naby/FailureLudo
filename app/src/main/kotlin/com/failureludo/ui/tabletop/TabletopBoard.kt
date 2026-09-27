package com.failureludo.ui.tabletop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.failureludo.engine.Board
import com.failureludo.engine.Piece
import com.failureludo.engine.PlayerColor
import com.failureludo.ui.components.BoardCoordinates
import com.failureludo.ui.components.TappedCellPieces
import com.failureludo.ui.components.buildPieceLayouts
import com.failureludo.ui.components.resolveTappedCellPieces
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object TabletopStyle {
    val Ink = Color(0xFF352440)
    val Panel = Color(0xFF503858)
    val Paper = Color(0xFFF8F3E7)
    val Muted = Color(0xFFE1CEDF)
    val Gold = Color(0xFFE5C17B)
}

/** Shares tested selection geometry with the engine-facing board, but owns its artwork. */
@Composable
fun TabletopBoard(
    pieces: Map<PlayerColor, List<Piece>>,
    movable: Set<Pair<PlayerColor, Int>>,
    palette: Map<PlayerColor, Color>,
    animatedCells: Map<Pair<PlayerColor, Int>, Pair<Int, Int>>,
    fromCells: Map<Pair<PlayerColor, Int>, Pair<Int, Int>>,
    progress: Float,
    onTap: (TappedCellPieces) -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    capturedKeys: Set<Pair<PlayerColor, Int>> = emptySet(),
    captureCells: List<Pair<Int, Int>> = emptyList(),
    captureProgress: Float = 1f
) {
    // Only selectable pawns keep a frame clock alive; draw-time reads avoid recomposition.
    val pulse: State<Float> = if (movable.isNotEmpty() && !reducedMotion) {
        rememberInfiniteTransition(label = "Legal pawn pulse").animateFloat(
            initialValue = 1f, targetValue = 1.20f,
            animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "Pawn scale")
    } else rememberUpdatedState(1f)
    val legalPieces = pieces.values.flatten().filter { it.color to it.id in movable }
    Canvas(modifier
        .semantics {
            contentDescription = "Ludo board. ${legalPieces.size} movable pawns."
            customActions = legalPieces.map { piece ->
                CustomAccessibilityAction("Move ${piece.color.displayName} pawn ${piece.id + 1}") {
                    val cell = BoardCoordinates.cellFor(piece)
                    val atCell = pieces.values.flatten().filter { BoardCoordinates.cellFor(it) == cell }
                    onTap(TappedCellPieces(atCell, atCell.filter { it.color to it.id in movable }, piece))
                    true
                }
            }
        }
        .pointerInput(pieces, movable, animatedCells) {
            detectTapGestures { tap ->
                if (movable.isNotEmpty()) {
                    val cell = size.width / 15f
                    val layouts = buildPieceLayouts(pieces, movable, emptyMap(), cell, size.width.toFloat())
                    resolveTappedCellPieces(tap, layouts, cell, 24.dp.toPx())?.let(onTap)
                }
            }
        }) {
        val c = size.width / 15f
        drawTable(c, palette)
        val visible = pieces.mapValues { (_, pawns) -> pawns.filter { !it.isFinished || it.color to it.id in animatedCells } }
        val layouts = buildPieceLayouts(visible, movable, animatedCells, c, size.width)
        fun travelOffset(key: Pair<PlayerColor, Int>): Offset {
            val to = animatedCells[key] ?: return Offset.Zero
            val from = fromCells[key] ?: return Offset.Zero
            return Offset((from.second - to.second) * c * (1f - progress),
                (from.first - to.first) * c * (1f - progress))
        }
        // Pair links travel with their pawns instead of jumping ahead to the destination.
        layouts.filter { it.piece.pairKey != null }.groupBy { it.piece.pairKey }.values.forEach { pair ->
            if (pair.size == 2 && pair[0].cell == pair[1].cell) {
                drawLine(TabletopStyle.Ink.copy(alpha = .65f),
                    pair[0].center + travelOffset(pair[0].piece.color to pair[0].piece.id),
                    pair[1].center + travelOffset(pair[1].piece.color to pair[1].piece.id),
                    strokeWidth = c * .19f, cap = StrokeCap.Round)
            }
        }
        layouts.sortedWith(compareBy({ it.isMovable }, { it.center.y })).forEach { layout ->
            val key = layout.piece.color to layout.piece.id
            val to = animatedCells[key]
            val from = fromCells[key]
            val traveling = to != null && from != null && to != from
            val delta = travelOffset(key)
            val captured = key in capturedKeys
            val hopping = traveling && !captured && !reducedMotion
            // One high hop followed by a small landing rebound, with a grounded shadow.
            val hop = if (hopping) {
                if (progress < .78f) sin(progress / .78f * PI).toFloat()
                else sin((progress - .78f) / .22f * PI).toFloat() * .15f
            } else 0f
            val lift = hop * c * .43f
            val at = layout.center + delta
            val tint = palette[layout.piece.color] ?: Color.Red
            val radius = layout.radius * 1.28f * if (layout.isMovable) pulse.value else 1f
            if (captured && traveling && !reducedMotion) {
                val behind = delta * .4f
                repeat(3) { index ->
                    drawCircle(tint.copy(alpha = .16f / (index + 1)), radius * (.6f - index * .12f),
                        at + behind * (index + 1).toFloat())
                }
            }
            val recoil = if (captured && captureProgress < .4f && !reducedMotion)
                sin(captureProgress / .4f * PI).toFloat() * -18f else 0f
            val squash = if (hopping && progress > .72f)
                sin((progress - .72f) / .28f * PI).toFloat() * .12f else 0f
            rotate(recoil, at) {
                scale(1f + squash, 1f - squash, at + Offset(0f, radius * .65f)) {
                    drawPawn(at, radius, tint, layout.isMovable, lift, layout.piece.color.ordinal)
                }
            }
        }
        if (!reducedMotion && captureProgress < 1f) {
            captureCells.distinct().forEach { (row, col) ->
                drawCaptureImpact(Offset((col + .5f) * c, (row + .5f) * c), c, captureProgress)
            }
        }
    }
}

private fun DrawScope.drawTable(c: Float, palette: Map<PlayerColor, Color>) {
    drawRect(Brush.linearGradient(listOf(Color(0xFFE999B7), Color(0xFFAAA5E8), Color(0xFF85CDBD))))
    // A colored substrate shows through the gaps; playing cells retain their light faces.
    palette.values.forEachIndexed { index, tint ->
        val corners = listOf(Offset.Zero, Offset(size.width, 0f), Offset(size.width, size.height), Offset(0f, size.height))
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = .42f), Color.Transparent),
            corners[index], size.width * .72f), size.width * .72f, corners[index])
    }
    // Solid home courtyards replace the old full-board grid.
    val origins = listOf(0 to 0, 0 to 9, 9 to 9, 9 to 0)
    PlayerColor.entries.forEachIndexed { index, color ->
        val tint = palette[color] ?: Color.Gray
        val (row, col) = origins[index]
        val corner = Offset((col + .22f) * c, (row + .22f) * c)
        drawRoundRect(Brush.linearGradient(listOf(tint.copy(alpha = .85f), tint)), corner,
            Size(5.56f * c, 5.56f * c), CornerRadius(c * .42f))
        drawRoundRect(Color.White.copy(alpha = .25f), corner + Offset(c * .12f, c * .12f),
            Size(5.32f * c, 5.32f * c), CornerRadius(c * .34f), style = Stroke(c * .035f))
        val courtyard = Offset((col + .65f) * c, (row + .65f) * c)
        drawRoundRect(Brush.linearGradient(listOf(lerp(tint, TabletopStyle.Paper, .40f),
            lerp(tint, TabletopStyle.Ink, .12f))), courtyard,
            Size(4.7f * c, 4.7f * c), CornerRadius(c * .65f))
        // Engraved corner flourishes and a rosette fill the courtyard without busy track cells.
        val emblem = Offset((col + 3f) * c, (row + 3f) * c)
        repeat(8) { petal ->
            rotate(petal * 45f, emblem) {
                drawOval(Color.White.copy(alpha = .10f), emblem + Offset(-c * .25f, -c * .92f),
                    Size(c * .5f, c * 1.05f), style = Stroke(c * .035f))
            }
        }
        drawRoundRect(Color.White.copy(alpha = .30f), courtyard + Offset(c * .12f, c * .12f),
            Size(4.46f * c, 4.46f * c), CornerRadius(c * .53f), style = Stroke(c * .035f))
        BoardCoordinates.HOME_YARD_SPOTS.getValue(color).forEach { (r, cl) ->
            val center = Offset((cl + .5f) * c, (r + .5f) * c)
            drawCircle(TabletopStyle.Ink.copy(alpha = .18f), c * .68f, center + Offset(0f, c * .06f))
            drawCircle(lerp(tint, TabletopStyle.Paper, .78f), c * .65f, center)
            drawCircle(Color.White.copy(alpha = .55f), c * .65f, center, style = Stroke(c * .04f))
        }
        drawIdentity(Offset((col + 3f) * c, (row + 3f) * c), c * .32f, Color.White.copy(alpha = .85f), index)
    }
    BoardCoordinates.MAIN_TRACK.forEachIndexed { index, cell ->
        val entry = PlayerColor.entries.firstOrNull { it.entryPosition == index }
        val tint = entry?.let { palette[it] } ?: Color(0xFFE7E3D8)
        drawTile(cell, c, Color(0xFFFFFDF7))
        if (index in Board.SAFE_SQUARES) {
            val center = Offset((cell.second + .5f) * c, (cell.first + .5f) * c)
            drawStar(center, c * .24f, if (entry != null) tint else Color(0xFF8D9B94))
        }
    }
    PlayerColor.entries.forEach { color ->
        val tint = palette[color] ?: Color.Gray
        BoardCoordinates.HOME_COLUMNS.getValue(color).forEach { cell ->
            drawTile(cell, c, tint)
        }
    }
    val center = Offset(7.5f * c, 7.5f * c)
    val corners = listOf(Offset(6*c, 6*c), Offset(9*c, 6*c), Offset(9*c, 9*c), Offset(6*c, 9*c))
    // Home lanes point into a four-way finish medallion.
    val colors = listOf(PlayerColor.BLUE, PlayerColor.YELLOW, PlayerColor.GREEN, PlayerColor.RED)
    corners.forEachIndexed { i, point ->
        val next = corners[(i + 1) % 4]
        drawPath(Path().apply { moveTo(center.x, center.y); lineTo(point.x, point.y); lineTo(next.x, next.y); close() },
            (palette[colors[i]] ?: Color.Gray).copy(alpha = .85f))
    }
    drawCircle(TabletopStyle.Ink.copy(alpha = .15f), c * .71f, center + Offset(0f, c * .08f))
    drawCircle(TabletopStyle.Paper, c * .66f, center)
    drawStar(center, c * .37f, TabletopStyle.Gold)
}

private fun DrawScope.drawTile(cell: Pair<Int, Int>, c: Float, color: Color) {
    val at = Offset((cell.second + .065f) * c, (cell.first + .065f) * c)
    drawRoundRect(Color(0xFFD8D3C6), at + Offset(0f, c * .04f), Size(c * .87f, c * .87f), CornerRadius(c * .10f))
    drawRoundRect(color, at, Size(c * .87f, c * .83f), CornerRadius(c * .10f))
}

private fun DrawScope.drawStar(center: Offset, r: Float, color: Color) {
    val path = Path()
    repeat(10) { i ->
        val angle = -PI / 2 + i * PI / 5
        val radius = if (i % 2 == 0) r else r * .44f
        val x = center.x + cos(angle).toFloat() * radius
        val y = center.y + sin(angle).toFloat() * radius
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

internal fun DrawScope.drawIdentity(center: Offset, r: Float, color: Color, index: Int) {
    when (index) {
        0 -> drawCircle(color, r, center, style = Stroke(r * .4f))
        1 -> drawRoundRect(color, center - Offset(r, r), Size(r*2, r*2), CornerRadius(r*.2f), style = Stroke(r*.4f))
        2 -> drawPath(Path().apply {
            moveTo(center.x, center.y-r); lineTo(center.x+r, center.y+r)
            lineTo(center.x-r, center.y+r); close()
        }, color, style = Stroke(r*.4f))
        else -> {
            drawLine(color, center-Offset(r, 0f), center+Offset(r, 0f), r*.45f, StrokeCap.Round)
            drawLine(color, center-Offset(0f, r), center+Offset(0f, r), r*.45f, StrokeCap.Round)
        }
    }
}

internal fun DrawScope.drawPawn(at: Offset, r: Float, color: Color, selectable: Boolean, lift: Float, identity: Int) {
    drawOval(Color.Black.copy(alpha = if (lift > 0) .13f else .22f),
        at + Offset(-r*.85f, r*.43f), Size(r*1.8f, r*.65f))
    val p = at - Offset(0f, lift)
    if (selectable) {
        // The glow grows with the pawn pulse and remains steady with reduced motion.
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .65f),
            color.copy(alpha = .30f), Color.Transparent), p, r * 1.65f), r * 1.65f, p)
        drawOval(Color.White.copy(alpha = .95f), at + Offset(-r * .78f, r * .58f),
            Size(r * 1.56f, r * .34f), style = Stroke(r * .10f))
    }
    val body = Path().apply {
        moveTo(p.x-r*.35f, p.y-r*.22f)
        cubicTo(p.x-r*.28f, p.y+r*.1f, p.x-r*.78f, p.y+r*.30f, p.x-r*.78f, p.y+r*.62f)
        quadraticTo(p.x, p.y+r*.94f, p.x+r*.78f, p.y+r*.62f)
        cubicTo(p.x+r*.78f, p.y+r*.30f, p.x+r*.28f, p.y+r*.1f, p.x+r*.35f, p.y-r*.22f)
        close()
    }
    val dark = Color(color.red*.55f, color.green*.55f, color.blue*.55f)
    drawPath(body, Brush.linearGradient(listOf(color, color, dark), p-Offset(r,r), p+Offset(r,r)))
    drawPath(body, dark, style = Stroke(r * .065f))
    drawOval(dark, p+Offset(-r*.78f, r*.51f), Size(r*1.56f, r*.34f))
    drawLine(Color.White.copy(alpha=.35f), p+Offset(-r*.48f,r*.59f), p+Offset(r*.35f,r*.64f), r*.07f, StrokeCap.Round)
    val head = p - Offset(0f, r*.46f)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha=.9f), color, dark), head-Offset(r*.22f,r*.2f), r*.8f), r*.48f, head)
    drawIdentity(p+Offset(0f,r*.29f), r*.17f, Color.White.copy(alpha=.9f), identity)
}

/** Expanding echo rings and a short starburst stay local to the collision cell. */
private fun DrawScope.drawCaptureImpact(at: Offset, cell: Float, progress: Float) {
    repeat(2) { echo ->
        val t = ((progress - echo * .16f) / (1f - echo * .16f)).coerceIn(0f, 1f)
        if (t > 0f && t < 1f) {
            drawCircle(TabletopStyle.Gold.copy(alpha = (1f - t) * .8f), cell * (.3f + t * 1.15f),
                at, style = Stroke(cell * .07f * (1f - t) + 1f))
        }
    }
    repeat(8) { ray ->
        val angle = ray * PI.toFloat() / 4f
        val direction = Offset(cos(angle), sin(angle))
        drawLine(TabletopStyle.Paper.copy(alpha = (1f - progress) * .95f),
            at + direction * cell * (.25f + progress * .7f),
            at + direction * cell * (.55f + progress * .9f),
            cell * .07f * (1f - progress), StrokeCap.Round)
    }
}
