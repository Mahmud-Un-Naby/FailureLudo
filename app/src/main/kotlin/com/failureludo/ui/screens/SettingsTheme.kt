package com.failureludo.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.failureludo.R

private val SettingsColors = darkColorScheme(
    primary = Color(0xFFE5C17B),
    onPrimary = Color(0xFF30200F),
    primaryContainer = Color(0xFF66502C),
    onPrimaryContainer = Color(0xFFFFE8B5),
    secondary = Color(0xFFD9ADCA),
    onSecondary = Color(0xFF382334),
    secondaryContainer = Color(0xFF654258),
    onSecondaryContainer = Color(0xFFFFE3EF),
    background = Color(0xFF281D26),
    onBackground = Color(0xFFF8F0E4),
    surface = Color(0xFF382A35),
    onSurface = Color(0xFFF8F0E4),
    surfaceVariant = Color(0xFF4C3A47),
    onSurfaceVariant = Color(0xFFDDCBD3),
    outline = Color(0xFFB39B9F),
    outlineVariant = Color(0xFF79616B)
)

@Composable
internal fun SettingsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SettingsColors, typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes, content = content)
}

/** Bundled artwork; no network access or runtime generation. */
@Composable
internal fun SettingsBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(SettingsColors.background)) {
        Image(painterResource(R.drawable.settings_tabletop), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        // A restrained veil keeps text legible while preserving the painted materials.
        Box(Modifier.matchParentSize().background(Color(0xFF211720).copy(alpha = .22f)))
        content()
    }
}
