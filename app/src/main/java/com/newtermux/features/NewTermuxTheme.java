package com.newtermux.features;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages the user-chosen accent color for NewTermux UI elements.
 */
public class NewTermuxTheme {

    private static final String PREFS_NAME = "newtermux_theme";
    private static final String KEY_ACCENT_COLOR = "accent_color";
    public static final int DEFAULT_COLOR = 0xFFD49B54; // Warm Sand (Warm Dark default)

    public static final int[] COLORS = {
        0xFFD49B54, // Warm Sand
        0xFF268BD2, // Solarized Blue
        0xFF66B2FF, // High Contrast Blue
        0xFF4CAF50, // Green
        0xFFFF9800, // Orange
        0xFFF44336, // Red
        0xFF00BCD4, // Teal
        0xFFFFC107, // Gold
        0xFFFAF9F6, // Off-White
    };

    public static final String[] COLOR_NAMES = {
        "Warm Sand", "Solarized Blue", "Sky Blue", "Green", "Orange", "Red", "Teal", "Gold", "Off-White"
    };

    public static int getAccentColor(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_ACCENT_COLOR, DEFAULT_COLOR);
    }

    public static void setAccentColor(Context context, int color) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_ACCENT_COLOR, color).apply();
        com.newtermux.compose.WarpThemeState.notifyChanged();
    }

    public static String getColorName(int color) {
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i] == color) return COLOR_NAMES[i];
        }
        return String.format("#%06X", color & 0xFFFFFF);
    }

    /** Returns true if the current accent_color doesn't match any of the 9 presets. */
    public static boolean isCustomAccentActive(Context context) {
        int cur = getAccentColor(context);
        for (int c : COLORS) if (c == cur) return false;
        return true;
    }
}
