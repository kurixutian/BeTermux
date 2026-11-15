package com.newtermux.compose

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.newtermux.features.NewTermuxTheme
import com.newtermux.glass.GlassPreferences
import com.newtermux.glass.GlassTokens
import com.newtermux.glass.LocalGlassEnabled

/** Allows any screen (e.g. Settings) to toggle Liquid Glass live across the current composition. */
val LocalGlassToggle = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

/**
 * Shared Jetpack Compose theme for BeTermux screens.
 *
 * Uses an obsidian-first dark Material 3 scheme paired with the user's live
 * accent color ([NewTermuxTheme.getAccentColor]) and provides [LocalGlassEnabled]
 * for progressive Liquid Glass rendering.
 */
@Composable
fun NewTermuxComposeTheme(
    context: Context,
    content: @Composable () -> Unit,
) {
    val accent = Color(NewTermuxTheme.getAccentColor(context))
    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF0D1117) else Color.White
    val onContainer = if (accent.luminance() > 0.45f) Color(0xFF141820) else Color(0xFFECECEC)

    var glassEnabled by remember { mutableStateOf(GlassPreferences.isGlassEnabled(context)) }
    val setGlassEnabled: (Boolean) -> Unit = remember(context) {
        { enabled ->
            GlassPreferences.setGlassEnabled(context, enabled)
            glassEnabled = enabled
        }
    }

    val colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.22f),
        onPrimaryContainer = onContainer,
        secondary = Color(0xFF06B6D4),
        onSecondary = Color(0xFF06161B),
        secondaryContainer = Color(0xFF06B6D4).copy(alpha = 0.16f),
        onSecondaryContainer = Color(0xFFE0F7FA),
        background = GlassTokens.BACKGROUND,
        onBackground = Color(0xFFE6EDF3),
        surface = GlassTokens.SURFACE_BASE,
        onSurface = Color(0xFFE6EDF3),
        surfaceVariant = Color(0xFF1C212B),
        onSurfaceVariant = Color(0xFF9BA3B0),
        outline = Color(0xFF343B47),
        outlineVariant = Color(0xFF252B35),
        error = Color(0xFFF28B82),
        onError = Color(0xFF601410),
    )

    CompositionLocalProvider(
        LocalGlassEnabled provides glassEnabled,
        LocalGlassToggle provides setGlassEnabled,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
