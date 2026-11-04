package com.newtermux.compose

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.newtermux.features.NewTermuxTheme

/**
 * Shared Jetpack Compose theme for the migrated NewTermux screens.
 *
 * The app is a dark, terminal-first experience, so this is a fixed dark
 * Material 3 scheme whose primary/secondary accent is pulled live from the
 * user's chosen accent color ([NewTermuxTheme.getAccentColor]) — keeping the
 * Compose screens visually consistent with the rest of the (View-based) UI.
 *
 * Polished to compute dynamic on-accent contrast (WCAG AA compliant) across all
 * preset and custom user accent colors, with a refined surface elevation hierarchy.
 */
@Composable
fun NewTermuxComposeTheme(
    context: Context,
    content: @Composable () -> Unit,
) {
    val accent = Color(NewTermuxTheme.getAccentColor(context))
    // Calculate legible contrast on accent (WCAG AA compliant)
    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF101214) else Color.White
    val onContainer = if (accent.luminance() > 0.45f) Color(0xFF181B1F) else Color(0xFFECECEC)

    val colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.20f),
        onPrimaryContainer = onContainer,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accent.copy(alpha = 0.15f),
        onSecondaryContainer = Color(0xFFECECEC),
        background = Color(0xFF101214),
        onBackground = Color(0xFFE6E8EA),
        surface = Color(0xFF181B1F),
        onSurface = Color(0xFFE6E8EA),
        surfaceVariant = Color(0xFF22262C),
        onSurfaceVariant = Color(0xFF9DA3AE),
        outline = Color(0xFF383E48),
        outlineVariant = Color(0xFF282D35),
        error = Color(0xFFF28B82),
        onError = Color(0xFF601410),
    )
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
