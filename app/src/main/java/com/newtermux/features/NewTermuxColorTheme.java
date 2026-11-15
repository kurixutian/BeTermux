package com.newtermux.features;

import android.content.Context;

import com.newtermux.compose.WarpThemeState;
import com.newtermux.theme.CuratedThemes;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileWriter;

/**
 * Manages BeTermux's curated terminal color themes and optional custom ANSI overrides.
 * Themes are written to ~/.termux/colors.properties and reloaded by the terminal emulator.
 */
public class NewTermuxColorTheme {

    private static final String PREFS_NAME = "newtermux_color_theme";
    private static final String KEY_THEME = "selected_theme";
    public static final String DEFAULT_THEME = CuratedThemes.KEY_WARM_DARK;

    public static final String THEME_KEY_CUSTOM = CuratedThemes.KEY_CUSTOM;

    public static final String[] THEME_KEYS = {
        CuratedThemes.KEY_WARM_DARK,
        CuratedThemes.KEY_SOLARIZED_DARK,
        CuratedThemes.KEY_HIGH_CONTRAST,
        THEME_KEY_CUSTOM
    };

    public static final String[] THEME_NAMES = {
        "Warm Dark",
        "Solarized Dark",
        "High Contrast"
    };

    // Warm Dark (default) — warm near-black charcoal bg, off-white text, amber cursor
    private static final String THEME_WARM_DARK =
        "background=#121210\n" +
        "foreground=#faf9f6\n" +
        "cursor=#d49b54\n" +
        "color0=#1a1917\n" +
        "color1=#e56b5e\n" +
        "color2=#5fb878\n" +
        "color3=#e0a94f\n" +
        "color4=#6ba6e8\n" +
        "color5=#c08cd6\n" +
        "color6=#56b6c2\n" +
        "color7=#d8d4cb\n" +
        "color8=#5e5a52\n" +
        "color9=#ff8577\n" +
        "color10=#7bd692\n" +
        "color11=#f0c272\n" +
        "color12=#8ec0f5\n" +
        "color13=#d6abf0\n" +
        "color14=#78d0db\n" +
        "color15=#faf9f6\n";

    // Solarized Dark — calibrated palette with readable warm-white foreground
    private static final String THEME_SOLARIZED_DARK =
        "background=#002b36\n" +
        "foreground=#fdf6e3\n" +
        "cursor=#268bd2\n" +
        "color0=#073642\n" +
        "color1=#dc322f\n" +
        "color2=#859900\n" +
        "color3=#b58900\n" +
        "color4=#268bd2\n" +
        "color5=#d33682\n" +
        "color6=#2aa198\n" +
        "color7=#eee8d5\n" +
        "color8=#0e414e\n" +
        "color9=#cb4b16\n" +
        "color10=#586e75\n" +
        "color11=#657b83\n" +
        "color12=#839496\n" +
        "color13=#6c71c4\n" +
        "color14=#93a1a1\n" +
        "color15=#faf9f6\n";

    // High Contrast — true black bg, AAA legibility, bright ANSI differentiation
    private static final String THEME_HIGH_CONTRAST =
        "background=#000000\n" +
        "foreground=#faf9f6\n" +
        "cursor=#66b2ff\n" +
        "color0=#141414\n" +
        "color1=#ff6b6b\n" +
        "color2=#5ce67e\n" +
        "color3=#ffd452\n" +
        "color4=#66b2ff\n" +
        "color5=#e58cff\n" +
        "color6=#70e0e0\n" +
        "color7=#f0efeb\n" +
        "color8=#8a8a85\n" +
        "color9=#ff8f8f\n" +
        "color10=#82f29c\n" +
        "color11=#ffe27a\n" +
        "color12=#8fc7ff\n" +
        "color13=#f0b3ff\n" +
        "color14=#99f0f0\n" +
        "color15=#ffffff\n";

    public static String getCurrentTheme(Context context) {
        String raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, DEFAULT_THEME);
        return CuratedThemes.normalizeKey(raw);
    }

    public static void applyTheme(Context context, String themeKey) {
        String normalized = CuratedThemes.normalizeKey(themeKey);
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, normalized).apply();
        if (THEME_KEY_CUSTOM.equals(normalized)) {
            writeColorsFileRaw(context, getCustomThemeContent(context));
        } else {
            writeColorsFile(context, normalized);
        }
        WarpThemeState.notifyChanged();
    }

    public static File getColorsFile(Context context) {
        if (context != null) {
            return new File(context.getFilesDir(), "home/.termux/colors.properties");
        }
        return TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE;
    }

    private static void writeColorsFile(Context context, String themeKey) {
        String content = getThemeContent(themeKey);
        writeColorsFileRaw(context, content);
    }

    public static String getThemeName(String key) {
        String normalized = CuratedThemes.normalizeKey(key);
        if (THEME_KEY_CUSTOM.equals(normalized)) return "Custom ANSI";
        for (int i = 0; i < THEME_NAMES.length; i++) {
            if (THEME_KEYS[i].equals(normalized)) return THEME_NAMES[i];
        }
        return normalized;
    }

    /** Returns {bg, fg, surface, green/success, cursor/accent} for drawing a preview card. */
    public static int[] getPreviewColors(String key) {
        switch (CuratedThemes.normalizeKey(key)) {
            case CuratedThemes.KEY_SOLARIZED_DARK:
                return new int[]{0xFF002B36, 0xFFFAF9F6, 0xFF073642, 0xFF859900, 0xFF268BD2};
            case CuratedThemes.KEY_HIGH_CONTRAST:
                return new int[]{0xFF000000, 0xFFFAF9F6, 0xFF141414, 0xFF5CE67E, 0xFF66B2FF};
            default:
                return new int[]{0xFF121210, 0xFFFAF9F6, 0xFF1A1917, 0xFF5FB878, 0xFFD49B54};
        }
    }

    public static int getBackgroundColorInt(String key) {
        return getPreviewColors(key)[0];
    }

    public static int getSurfaceColorInt(String key) {
        return getPreviewColors(key)[2];
    }

    public static int getSurfaceElevatedColorInt(String key) {
        switch (CuratedThemes.normalizeKey(key)) {
            case CuratedThemes.KEY_SOLARIZED_DARK: return 0xFF0E414E;
            case CuratedThemes.KEY_HIGH_CONTRAST:  return 0xFF141414;
            default:                               return 0xFF22201D;
        }
    }

    public static int getBorderSubtleColorInt(String key) {
        switch (CuratedThemes.normalizeKey(key)) {
            case CuratedThemes.KEY_SOLARIZED_DARK: return 0xFF184A57;
            case CuratedThemes.KEY_HIGH_CONTRAST:  return 0xFF707070;
            default:                               return 0xFF2D2B27;
        }
    }

    public static String getThemeContent(String key) {
        switch (CuratedThemes.normalizeKey(key)) {
            case CuratedThemes.KEY_SOLARIZED_DARK: return THEME_SOLARIZED_DARK;
            case CuratedThemes.KEY_HIGH_CONTRAST:  return THEME_HIGH_CONTRAST;
            default:                               return THEME_WARM_DARK;
        }
    }

    public static String getThemeContent(Context context, String key) {
        String normalized = CuratedThemes.normalizeKey(key);
        if (THEME_KEY_CUSTOM.equals(normalized) && context != null) {
            return getCustomThemeContent(context);
        }
        return getThemeContent(normalized);
    }

    public static void applyCustomTheme(Context ctx, String content) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("custom_theme_content", content)
            .putString(KEY_THEME, THEME_KEY_CUSTOM)
            .apply();
        writeColorsFileRaw(ctx, content);
        WarpThemeState.notifyChanged();
    }

    public static String getCustomThemeContent(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("custom_theme_content", THEME_WARM_DARK);
    }

    static void writeColorsFileRaw(Context context, String content) {
        if (context != null) {
            try {
                File appColors = new File(context.getFilesDir(), "home/.termux/colors.properties");
                File parent = appColors.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (FileWriter fw = new FileWriter(appColors)) {
                    fw.write(content);
                }
            } catch (Exception ignored) {}
        }
        try {
            File dir = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            try (FileWriter fw = new FileWriter(TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE)) {
                fw.write(content);
            }
        } catch (Exception ignored) {}
    }
}
