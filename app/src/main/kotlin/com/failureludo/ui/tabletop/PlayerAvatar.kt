package com.failureludo.ui.tabletop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.failureludo.engine.PlayerColor

/** Confirmed online action timer; the server determines its deadline. */
data class AvatarCountdown(val color: PlayerColor, val deadlineAtMillis: Long,
                           val remainingMillis: Long, val durationMillis: Long)

@Composable
internal fun PlayerAvatar(name: String, tint: Color, countdown: AvatarCountdown?, reducedMotion: Boolean) {
    val fraction = countdown?.let { (it.remainingMillis.toFloat() / it.durationMillis.coerceAtLeast(1)).coerceIn(0f, 1f) }
    val progress = remember(countdown?.deadlineAtMillis, countdown?.color) { Animatable(fraction ?: 0f) }
    LaunchedEffect(countdown, reducedMotion) {
        progress.snapTo(fraction ?: 0f)
        if (countdown != null && !reducedMotion && countdown.remainingMillis > 0) {
            progress.animateTo(0f, tween(countdown.remainingMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                easing = LinearEasing))
        }
    }
    val seconds = countdown?.let { (it.remainingMillis + 999) / 1000 }
    Box(Modifier.size(40.dp).semantics {
        contentDescription = if (seconds == null) "$name profile" else "$name, $seconds seconds left for this action"
    }, contentAlignment = Alignment.Center) {
        // Guest identity has no uploaded photo; show an initial in its profile slot.
        Box(Modifier.size(30.dp).background(tint.copy(alpha = .22f), CircleShape), contentAlignment = Alignment.Center) {
            Text(name.trim().take(1).uppercase(), color = TabletopStyle.Paper, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (countdown != null) Canvas(Modifier.matchParentSize()) {
            val width = 3.dp.toPx()
            val inset = width / 2
            val arcSize = Size(size.width - width, size.height - width)
            drawArc(TabletopStyle.Paper.copy(alpha = .14f), -90f, 360f, false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(width))
            val remaining = progress.value
            val color = when {
                remaining <= .15f -> Color(0xFFFF746C)
                remaining <= .3f -> Color(0xFFFFCC66)
                else -> tint
            }
            drawArc(color, -90f, 360f * remaining, false, topLeft = Offset(inset, inset),
                size = arcSize, style = Stroke(width, cap = StrokeCap.Round))
        }
    }
}
