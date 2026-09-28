package com.failureludo.ui.tabletop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.sin

/** Decorative overlay driven by the existing capture clock; never participates in pawn hit testing. */
internal fun DrawScope.drawCaptureSkull(at: Offset, cell: Float, progress: Float) {
    val t = progress.coerceIn(0f, 1f)
    val alpha = minOf(t / .10f, (1f - t) / .35f, 1f).coerceIn(0f, 1f)
    if (alpha == 0f) return
    val pop = when {
        t < .22f -> .55f + .65f * (1f - (1f - t / .22f) * (1f - t / .22f))
        t < .42f -> 1.20f - .20f * ((t - .22f) / .20f)
        else -> 1f
    }
    val unit = cell * 1.25f * pop
    // Keep edge captures legible while the skull floats above the collision.
    val center = Offset(
        at.x.coerceIn(cell, size.width - cell),
        (at.y - cell * (.18f + .70f * t)).coerceIn(cell, size.height - cell)
    )
    val ink = TabletopStyle.Ink.copy(alpha = alpha)
    val bone = Color(0xFFFFF4D5).copy(alpha = alpha)
    withTransform({
        translate(center.x, center.y)
        scale(unit, unit, Offset.Zero)
        rotate(sin(t * PI * 2).toFloat() * 8f * (1f - t), Offset.Zero)
    }) {
        // Rounded crossbones frame the jaw without obscuring the eye sockets.
        for (direction in listOf(-1f, 1f)) {
            val start = Offset(-.65f, .40f - direction * .23f)
            val end = Offset(.65f, .40f + direction * .23f)
            drawLine(ink, start, end, .16f, StrokeCap.Round)
            drawLine(bone, start, end, .095f, StrokeCap.Round)
        }
        val head = Path().apply {
            moveTo(0f, -.60f)
            cubicTo(.35f, -.60f, .53f, -.43f, .53f, -.16f)
            cubicTo(.53f, .06f, .43f, .17f, .28f, .21f)
            lineTo(.25f, .46f)
            quadraticTo(0f, .55f, -.25f, .46f)
            lineTo(-.28f, .21f)
            cubicTo(-.43f, .17f, -.53f, .06f, -.53f, -.16f)
            cubicTo(-.53f, -.43f, -.35f, -.60f, 0f, -.60f)
            close()
        }
        drawPath(head, Brush.verticalGradient(
            listOf(Color.White.copy(alpha = alpha), bone), startY = -.60f, endY = .48f))
        drawPath(head, ink, style = Stroke(.055f))
        drawOval(ink, Offset(-.36f, -.25f), Size(.28f, .29f))
        drawOval(ink, Offset(.08f, -.25f), Size(.28f, .29f))
        val nose = Path().apply {
            moveTo(0f, .06f)
            lineTo(-.09f, .21f)
            quadraticTo(0f, .16f, .09f, .21f)
            close()
        }
        drawPath(nose, ink)
        drawLine(ink, Offset(-.24f, .31f), Offset(.24f, .31f), .035f, StrokeCap.Round)
        for (x in listOf(-.08f, .08f)) {
            drawLine(ink, Offset(x, .31f), Offset(x, .48f), .03f, StrokeCap.Round)
        }
    }
}
