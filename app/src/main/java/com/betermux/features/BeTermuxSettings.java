package com.betermux.features;

import android.content.Context;
import android.content.SharedPreferences;

public class BeTermuxSettings {
    private static final String PREFS_NAME = "betermux_settings";

    // Keys
    public static final String KEY_KEYBOARD_SUGGESTIONS = "keyboard_suggestions";
    public static final String KEY_ZSH_PLUGINS = "zsh_plugins";
    public static final String KEY_SESSION_TABS             = "session_tabs";
    public static final String KEY_AUTOCORRECT               = "autocorrect_enabled";
    public static final String KEY_STARTUP_SCRIPT_ENABLED    = "startup_script_enabled";
    public static final String KEY_URL_DETECTION_ENABLED     = "url_detection_enabled";
    public static final String KEY_SESSION_RENAME_ENABLED    = "session_rename_enabled";
    public static final String KEY_TEXT_EXPANSION_ENABLED    = "text_expansion_enabled";
    public static final String KEY_EXTRA_KEYS_VISIBLE        = "extra_keys_visible";
    public static final String KEY_KEEP_ALIVE_BACKGROUND     = "keep_alive_background";
    // Internal one-time gate: set once we have shown the battery-optimization nudge.
    public static final String KEY_BATTERY_OPT_PROMPTED      = "battery_opt_prompted";
    // Screen layout per orientation (String values below), set in Settings → Appearance.
    public static final String KEY_LAYOUT_PORTRAIT           = "layout_portrait";
    public static final String KEY_LAYOUT_LANDSCAPE          = "layout_landscape";
    public static final String LAYOUT_STANDARD               = "standard";     // toolbar + session previews on top
    public static final String LAYOUT_STATUS_LINE            = "status_line";  // one tmux-style line at the bottom
    public static final String LAYOUT_DUAL_SCREEN            = "dual_screen";  // landscape: terminal on top, controls on the second display

    // Block view (Phase 1b)
    public static final String KEY_BLOCK_VIEW_ENABLED        = "block_view_enabled";
    public static final String KEY_BLOCK_DENSITY             = "block_density";
    public static final String DENSITY_COMFORT               = "comfort";
    public static final String DENSITY_COMPACT               = "compact";

    // Workspaces, Dual Sidebars & Hibernation (Phases 1-4 rollback flags)
    public static final String KEY_WORKSPACES_ENABLED            = "workspaces_enabled";
    public static final String KEY_DUAL_SIDEBARS_ENABLED         = "dual_sidebars_enabled";
    public static final String KEY_RIGHT_FILE_EXPLORER_ENABLED   = "right_file_explorer_enabled";
    public static final String KEY_WORKSPACE_HIBERNATION_ENABLED = "workspace_hibernation_enabled";

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isKeyboardSuggestionsEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_KEYBOARD_SUGGESTIONS, false);
    }
    public static void setKeyboardSuggestions(Context ctx, boolean v) {
        prefs(ctx).edit().putBoolean(KEY_KEYBOARD_SUGGESTIONS, v).apply();
    }

    public static boolean isZshPluginsEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_ZSH_PLUGINS, false);
    }
    public static boolean isSessionTabsEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_SESSION_TABS, true);
    }
    public static boolean isAutocorrectEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_AUTOCORRECT, true);
    }
    public static boolean isStartupScriptEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_STARTUP_SCRIPT_ENABLED, false);
    }
    public static boolean isUrlDetectionEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_URL_DETECTION_ENABLED, true);
    }
    public static boolean isSessionRenameEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_SESSION_RENAME_ENABLED, true);
    }
    public static boolean isTextExpansionEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_TEXT_EXPANSION_ENABLED, false);
    }
    public static boolean isExtraKeysVisible(Context ctx) {
        return prefs(ctx).getBoolean(KEY_EXTRA_KEYS_VISIBLE, true);
    }
    /** Whether the process should hold a foreground wake lock while sessions are alive so it
     *  survives being backgrounded by a fullscreen game. Defaults ON. */
    public static boolean isKeepAliveInBackground(Context ctx) {
        return prefs(ctx).getBoolean(KEY_KEEP_ALIVE_BACKGROUND, true);
    }

    // One-time battery-optimization nudge gate.
    public static boolean wasBatteryOptPrompted(Context ctx) {
        return prefs(ctx).getBoolean(KEY_BATTERY_OPT_PROMPTED, false);
    }
    public static void setBatteryOptPrompted(Context ctx, boolean v) {
        prefs(ctx).edit().putBoolean(KEY_BATTERY_OPT_PROMPTED, v).apply();
    }

    // Screen layout for portrait or landscape. Defaults to the standard layout.
    public static String getLayout(Context ctx, boolean landscape) {
        return prefs(ctx).getString(landscape ? KEY_LAYOUT_LANDSCAPE : KEY_LAYOUT_PORTRAIT, LAYOUT_STANDARD);
    }
    public static void setLayout(Context ctx, boolean landscape, String layout) {
        prefs(ctx).edit().putString(landscape ? KEY_LAYOUT_LANDSCAPE : KEY_LAYOUT_PORTRAIT, layout).apply();
    }

    public static boolean isBlockViewEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_BLOCK_VIEW_ENABLED, true);
    }
    public static void setBlockViewEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_BLOCK_VIEW_ENABLED, enabled).apply();
    }

    public static String getBlockDensity(Context ctx) {
        String raw = prefs(ctx).getString(KEY_BLOCK_DENSITY, DENSITY_COMFORT);
        return DENSITY_COMPACT.equals(raw) ? DENSITY_COMPACT : DENSITY_COMFORT;
    }
    public static void setBlockDensity(Context ctx, String density) {
        String normalized = DENSITY_COMPACT.equals(density) ? DENSITY_COMPACT : DENSITY_COMFORT;
        prefs(ctx).edit().putString(KEY_BLOCK_DENSITY, normalized).apply();
    }

    public static final String KEY_INPUT_BAR_POSITION = "input_bar_position";
    public static final String INPUT_POSITION_BOTTOM  = "bottom";
    public static final String INPUT_POSITION_TOP     = "top";

    public static String getInputBarPosition(Context ctx) {
        String raw = prefs(ctx).getString(KEY_INPUT_BAR_POSITION, INPUT_POSITION_BOTTOM);
        return INPUT_POSITION_TOP.equals(raw) ? INPUT_POSITION_TOP : INPUT_POSITION_BOTTOM;
    }

    public static void setInputBarPosition(Context ctx, String position) {
        String normalized = INPUT_POSITION_TOP.equals(position) ? INPUT_POSITION_TOP : INPUT_POSITION_BOTTOM;
        prefs(ctx).edit().putString(KEY_INPUT_BAR_POSITION, normalized).apply();
    }

    public static final String KEY_UI_ZOOM_PERCENT     = "ui_zoom_percent";
    public static final int UI_ZOOM_MIN_PERCENT        = 85;
    public static final int UI_ZOOM_MAX_PERCENT        = 130;
    public static final int UI_ZOOM_DEFAULT_PERCENT    = 100;

    public static int getUiZoomPercent(Context ctx) {
        int raw = prefs(ctx).getInt(KEY_UI_ZOOM_PERCENT, UI_ZOOM_DEFAULT_PERCENT);
        return Math.max(UI_ZOOM_MIN_PERCENT, Math.min(UI_ZOOM_MAX_PERCENT, raw));
    }

    public static void setUiZoomPercent(Context ctx, int percent) {
        int clamped = Math.max(UI_ZOOM_MIN_PERCENT, Math.min(UI_ZOOM_MAX_PERCENT, percent));
        prefs(ctx).edit().putInt(KEY_UI_ZOOM_PERCENT, clamped).apply();
    }

    public static float getUiZoomFactor(Context ctx) {
        return getUiZoomPercent(ctx) / 100f;
    }

    // Localhost servers: project folder -> assigned port (JSON object persisted in prefs)
    public static final String KEY_LOCALHOST_PORTS = "localhost_project_ports";

    public static int getLocalhostPort(Context ctx, String projectPath) {
        if (projectPath == null) return -1;
        try {
            org.json.JSONObject obj = new org.json.JSONObject(prefs(ctx).getString(KEY_LOCALHOST_PORTS, "{}"));
            return obj.optInt(projectPath, -1);
        } catch (Exception e) {
            return -1;
        }
    }

    public static void setLocalhostPort(Context ctx, String projectPath, int port) {
        try {
            org.json.JSONObject obj = new org.json.JSONObject(prefs(ctx).getString(KEY_LOCALHOST_PORTS, "{}"));
            obj.put(projectPath, port);
            prefs(ctx).edit().putString(KEY_LOCALHOST_PORTS, obj.toString()).apply();
        } catch (Exception ignored) { }
    }

    // AI Assistant (Phase 5)
    public static final String KEY_AI_ENABLED       = "ai_enabled";
    public static final String KEY_AI_PROVIDER      = "ai_provider";
    public static final String KEY_AI_MODEL         = "ai_model";
    public static final String KEY_AI_BASE_URL      = "ai_base_url";
    public static final String KEY_AI_MAX_TOKENS    = "ai_max_tokens";
    public static final String AI_PROVIDER_OPENAI   = "openai";
    public static final String AI_PROVIDER_ANTHROPIC = "anthropic";
    public static final String AI_PROVIDER_GEMINI   = "gemini";
    public static final int AI_MAX_TOKENS_MIN       = 128;
    public static final int AI_MAX_TOKENS_MAX       = 8192;
    public static final int AI_MAX_TOKENS_DEFAULT   = 1024;

    public static boolean isAiEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_AI_ENABLED, false);
    }

    public static void setAiEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_AI_ENABLED, enabled).apply();
    }

    public static String getAiProvider(Context ctx) {
        String raw = prefs(ctx).getString(KEY_AI_PROVIDER, AI_PROVIDER_OPENAI);
        if (AI_PROVIDER_ANTHROPIC.equals(raw) || AI_PROVIDER_GEMINI.equals(raw)) {
            return raw;
        }
        return AI_PROVIDER_OPENAI;
    }

    public static void setAiProvider(Context ctx, String provider) {
        String normalized = (AI_PROVIDER_ANTHROPIC.equals(provider) || AI_PROVIDER_GEMINI.equals(provider))
            ? provider : AI_PROVIDER_OPENAI;
        prefs(ctx).edit().putString(KEY_AI_PROVIDER, normalized).apply();
    }

    public static String getAiModel(Context ctx) {
        String raw = prefs(ctx).getString(KEY_AI_MODEL, "");
        return raw == null ? "" : raw.trim();
    }

    public static void setAiModel(Context ctx, String model) {
        prefs(ctx).edit().putString(KEY_AI_MODEL, model == null ? "" : model.trim()).apply();
    }

    public static String getAiBaseUrl(Context ctx) {
        String raw = prefs(ctx).getString(KEY_AI_BASE_URL, "");
        return raw == null ? "" : raw.trim();
    }

    public static void setAiBaseUrl(Context ctx, String baseUrl) {
        prefs(ctx).edit().putString(KEY_AI_BASE_URL, baseUrl == null ? "" : baseUrl.trim()).apply();
    }

    public static int getAiMaxTokens(Context ctx) {
        int raw = prefs(ctx).getInt(KEY_AI_MAX_TOKENS, AI_MAX_TOKENS_DEFAULT);
        return Math.max(AI_MAX_TOKENS_MIN, Math.min(AI_MAX_TOKENS_MAX, raw));
    }

    public static void setAiMaxTokens(Context ctx, int maxTokens) {
        int clamped = Math.max(AI_MAX_TOKENS_MIN, Math.min(AI_MAX_TOKENS_MAX, maxTokens));
        prefs(ctx).edit().putInt(KEY_AI_MAX_TOKENS, clamped).apply();
    }

    // Pending command — written by Settings, consumed and cleared by TermuxActivity.onResume()
    public static String getPendingCommand(Context ctx) {
        return prefs(ctx).getString("pending_command", null);
    }
    public static void setPendingCommand(Context ctx, String cmd) {
        prefs(ctx).edit().putString("pending_command", cmd).apply();
    }
    public static void clearPendingCommand(Context ctx) {
        prefs(ctx).edit().remove("pending_command").apply();
    }

    // Workspaces, Dual Sidebars & Hibernation (Phases 1-4 rollback flags)
    public static boolean isWorkspacesEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_WORKSPACES_ENABLED, true);
    }
    public static void setWorkspacesEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_WORKSPACES_ENABLED, enabled).apply();
    }

    public static boolean isDualSidebarsEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_DUAL_SIDEBARS_ENABLED, true);
    }
    public static void setDualSidebarsEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_DUAL_SIDEBARS_ENABLED, enabled).apply();
    }

    public static boolean isRightFileExplorerEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_RIGHT_FILE_EXPLORER_ENABLED, true);
    }
    public static void setRightFileExplorerEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_RIGHT_FILE_EXPLORER_ENABLED, enabled).apply();
    }

    public static boolean isWorkspaceHibernationEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_WORKSPACE_HIBERNATION_ENABLED, true);
    }
    public static void setWorkspaceHibernationEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_WORKSPACE_HIBERNATION_ENABLED, enabled).apply();
    }

    // Generic setter for all boolean keys (used by preference listener)
    public static void set(Context ctx, String key, boolean value) {
        prefs(ctx).edit().putBoolean(key, value).apply();
    }

    // Generic getter — uses per-key defaults
    public static boolean get(Context ctx, String key) {
        switch (key) {
            case KEY_KEYBOARD_SUGGESTIONS: return isKeyboardSuggestionsEnabled(ctx);
            case KEY_ZSH_PLUGINS:                  return isZshPluginsEnabled(ctx);
            case KEY_SESSION_TABS:                 return isSessionTabsEnabled(ctx);
            case KEY_AUTOCORRECT:                  return isAutocorrectEnabled(ctx);
            case KEY_STARTUP_SCRIPT_ENABLED:       return isStartupScriptEnabled(ctx);
            case KEY_URL_DETECTION_ENABLED:        return isUrlDetectionEnabled(ctx);
            case KEY_SESSION_RENAME_ENABLED:       return isSessionRenameEnabled(ctx);
            case KEY_TEXT_EXPANSION_ENABLED:       return isTextExpansionEnabled(ctx);
            case KEY_EXTRA_KEYS_VISIBLE:           return isExtraKeysVisible(ctx);
            case KEY_KEEP_ALIVE_BACKGROUND:        return isKeepAliveInBackground(ctx);
            case KEY_AI_ENABLED:                   return isAiEnabled(ctx);
            case KEY_WORKSPACES_ENABLED:           return isWorkspacesEnabled(ctx);
            case KEY_DUAL_SIDEBARS_ENABLED:        return isDualSidebarsEnabled(ctx);
            case KEY_RIGHT_FILE_EXPLORER_ENABLED:  return isRightFileExplorerEnabled(ctx);
            case KEY_WORKSPACE_HIBERNATION_ENABLED: return isWorkspaceHibernationEnabled(ctx);
            default:                               return prefs(ctx).getBoolean(key, false);
        }
    }
}
