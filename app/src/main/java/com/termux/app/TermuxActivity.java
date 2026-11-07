package com.termux.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import com.termux.BuildConfig;
import com.termux.R;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.newtermux.deck.DeckBridge;
import com.newtermux.update.UpdateUi;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import android.Manifest;
import android.content.pm.PackageManager;

import com.newtermux.features.AutoCorrectHandler;
import com.newtermux.features.NewTermuxSettings;
import com.newtermux.features.NewTermuxTheme;
import com.newtermux.features.RootToggleManager;
import com.newtermux.features.SpeechInputManager;
import com.termux.app.terminal.MiniTerminalPipView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

/**
 * A terminal emulator activity.
 * <p/>
 * See
 * <ul>
 * <li>http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android</li>
 * <li>https://code.google.com/p/android/issues/detail?id=6426</li>
 * </ul>
 * about memory leaks.
 */
public final class TermuxActivity extends AppCompatActivity implements ServiceConnection {

    /**
     * The connection to the {@link TermuxService}. Requested in {@link #onCreate(Bundle)} with a call to
     * {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    TermuxService mTermuxService;

    /**
     * The {@link TerminalView} shown in  {@link TermuxActivity} that displays the terminal.
     */
    TerminalView mTerminalView;

    /**
     *  The {@link TerminalViewClient} interface implementation to allow for communication between
     *  {@link TerminalView} and {@link TermuxActivity}.
     */
    TermuxTerminalViewClient mTermuxTerminalViewClient;

    /**
     *  The {@link TerminalSessionClient} interface implementation to allow for communication between
     *  {@link TerminalSession} and {@link TermuxActivity}.
     */
    TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    /**
     * Termux app shared preferences manager.
     */
    private TermuxAppSharedPreferences mPreferences;

    /**
     * Termux app SharedProperties loaded from termux.properties
     */
    private TermuxAppSharedProperties mProperties;

    /**
     * The root view of the {@link TermuxActivity}.
     */
    TermuxActivityRootView mTermuxActivityRootView;

    /**
     * The space at the bottom of {@link @mTermuxActivityRootView} of the {@link TermuxActivity}.
     */
    View mTermuxActivityBottomSpaceView;

    /**
     * The terminal extra keys view.
     */
    ExtraKeysView mExtraKeysView;

    /**
     * The client for the {@link #mExtraKeysView}.
     */
    TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;

    /** Whether extra keys were placed in the right drawer at activity creation time. */
    private boolean mExtraKeysInDrawerModeAtCreation = false;

    /**
     * The termux sessions list controller.
     */
    TermuxSessionsListViewController mTermuxSessionListViewController;

    /**
     * The {@link TermuxActivity} broadcast receiver for various things like terminal style configuration changes.
     */
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();

    /**
     * The last toast shown, used cancel current toast before showing new in {@link #showToast(String, boolean)}.
     */
    Toast mLastToast;

    /**
     * If between onResume() and onStop(). Note that only one session is in the foreground of the terminal view at the
     * time, so if the session causing a change is not in the foreground it should probably be treated as background.
     */
    private boolean mIsVisible;

    /**
     * If onResume() was called after onCreate().
     */
    private boolean mIsOnResumeAfterOnCreate = false;

    /**
     * If activity was restarted like due to call to {@link #recreate()} after receiving
     * {@link TERMUX_ACTIVITY#ACTION_RELOAD_STYLE}, system dark night mode was changed or activity
     * was killed by android.
     */
    private boolean mIsActivityRecreated = false;

    /**
     * The {@link TermuxActivity} is in an invalid state and must not be run.
     */
    private boolean mIsInvalidState;

    private int mNavBarHeight;

    private float mTerminalToolbarDefaultHeight;


    // NewTermux features
    private SpeechInputManager mSpeechInputManager;
    private RootToggleManager mRootToggleManager;
    private AutoCorrectHandler mAutoCorrectHandler;
    private com.newtermux.features.PackageManagerMenu mPackageManagerMenu;
    private View mAutocorrectBar;
    private TextView mAutocorrectText;
    private String mPendingCorrection;
    private String mPendingOriginal;
    private ImageButton mBtnSTT;
    private ImageButton mBtnRootToggle;
    private LinearLayout mSessionPipContainer;
    private LinearLayout mStatusTabsContainer;
    private boolean mStatusLineMode = false;
    private boolean mSystemBarsHidden = false;
    // Toolbar button layout params saved while the buttons live in the status line
    private final java.util.Map<Integer, ViewGroup.LayoutParams> mToolbarButtonParams = new java.util.HashMap<>();
    private static final int REQUEST_RECORD_AUDIO = 201;

    // Tab status constants
    public static final int TAB_STATUS_STANDBY = 0;    // White
    public static final int TAB_STATUS_PROCESSING = 1; // Yellow
    public static final int TAB_STATUS_FINISHED = 2;   // Green

    // Tab type constants
    public static final int TAB_TYPE_TERMINAL = 0;
    public static final int TAB_TYPE_BROWSER = 1;
    public static final int TAB_TYPE_FILE = 2;

    // File Tab model
    public static class FileTab {
        public final File file;
        public final String path;
        public final String name;
        public int status;

        public FileTab(File file) {
            this.file = file;
            this.path = file.getAbsolutePath();
            this.name = file.getName();
            this.status = TAB_STATUS_STANDBY;
        }
    }

    // In-tab Browser fields
    private androidx.compose.ui.platform.ComposeView mBrowserContainer;
    private boolean mHasBrowserTab = false;
    private boolean mIsBrowserTabActive = false;
    private String mBrowserTabTitle = "Dev Browser";
    private int mBrowserTabStatus = TAB_STATUS_STANDBY;
    private Runnable mBrowserGoBack = null;
    private boolean mBrowserCanGoBack = false;
    private Runnable mBrowserReload = null;

    // In-tab File fields
    private androidx.compose.ui.platform.ComposeView mFileTabContainer;
    private final java.util.List<FileTab> mOpenFileTabs = new java.util.ArrayList<>();
    private int mActiveFileTabIndex = -1;

    // Live localhost ports
    private java.util.List<Integer> mLivePorts = java.util.Collections.emptyList();
    private volatile boolean mPortScanInFlight = false;

    private static class SessionActivityState {
        long lastActiveTimeMs = 0L;
        long lastCpuTicks = -1L;
    }

    private static final java.util.Map<TerminalSession, SessionActivityState> sSessionActivityMap =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static void markSessionActive(TerminalSession session) {
        if (session == null) return;
        synchronized (sSessionActivityMap) {
            SessionActivityState state = sSessionActivityMap.get(session);
            if (state == null) {
                state = new SessionActivityState();
                sSessionActivityMap.put(session, state);
            }
            state.lastActiveTimeMs = System.currentTimeMillis();
        }
    }

    private final android.os.Handler mTabStatusHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable mTabStatusCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mIsInvalidState && isVisible()) {
                refreshTabStatusDots();
                pollLiveLocalhostPorts();
                mTabStatusHandler.postDelayed(this, 1500);
            }
        }
    };

    private static class TabViewHolder {
        int type;
        TerminalSession session;
        FileTab fileTab;
        View dotView;
        TextView titleView;
        ImageView iconView;
        int currentStatus = -1;
    }

    // SAF launchers for Export Screen, Make Script, and Image Upload
    private ActivityResultLauncher<String> mScreenExportSaver;
    private ActivityResultLauncher<String> mScriptSaver;
    private ActivityResultLauncher<String> mImageUploadLauncher;
    private final List<String> mPendingScriptLines = new ArrayList<>();

    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;
    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;
    private static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 10;
    private static final int CONTEXT_MENU_AUTOFILL_USERNAME = 11;
    private static final int CONTEXT_MENU_AUTOFILL_PASSWORD = 2;
    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;
    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;
    private static final int CONTEXT_MENU_STYLING_ID = 5;
    private static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6;
    private static final int CONTEXT_MENU_HELP_ID = 7;
    private static final int CONTEXT_MENU_SETTINGS_ID = 8;
    private static final int CONTEXT_MENU_REPORT_ID = 9;

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";
    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";

    private static final String LOG_TAG = "TermuxActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.logDebug(LOG_TAG, "onCreate");
        mIsOnResumeAfterOnCreate = true;

        if (savedInstanceState != null)
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false);

        // Delete ReportInfo serialized object files from cache older than 14 days
        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false);

        // Load Termux app SharedProperties from disk
        mProperties = TermuxAppSharedProperties.getProperties();
        reloadProperties();

        setActivityTheme();

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_termux);

        // Load termux shared preferences
        // This will also fail if TermuxConstants.TERMUX_PACKAGE_NAME does not equal applicationId
        mPreferences = TermuxAppSharedPreferences.build(this, true);
        if (mPreferences == null) {
            // An AlertDialog should have shown to kill the app, so we don't continue running activity code
            mIsInvalidState = true;
            return;
        }

        setMargins();

        mTermuxActivityRootView = findViewById(R.id.activity_termux_root_view);
        mTermuxActivityRootView.setActivity(this);
        mTermuxActivityBottomSpaceView = findViewById(R.id.activity_termux_bottom_space_view);
        mTermuxActivityRootView.setOnApplyWindowInsetsListener(new TermuxActivityRootView.WindowInsetsListener());

        View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            mNavBarHeight = insets.getSystemWindowInsetBottom();
            return insets;
        });

        if (mProperties.isUsingFullScreen()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setTermuxTerminalViewAndClients();

        setTerminalToolbarView(savedInstanceState);

        mScreenExportSaver = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/plain"),
            uri -> {
                if (uri == null) return;
                TerminalSession session = getCurrentSession();
                if (session == null) return;
                String text = session.getEmulator().getScreen().getTranscriptText();
                new Thread(() -> {
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os != null) os.write(text.getBytes());
                        runOnUiThread(() -> Toast.makeText(this, "Screen exported", Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start();
            });

        mScriptSaver = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/x-shellscript"),
            uri -> {
                if (uri == null || mPendingScriptLines.isEmpty()) return;
                List<String> lines = new ArrayList<>(mPendingScriptLines);
                mPendingScriptLines.clear();
                new Thread(() -> {
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os == null) return;
                        os.write("#!/bin/zsh\n".getBytes());
                        for (String line : lines) {
                            os.write((line + "\n").getBytes());
                        }
                        runOnUiThread(() -> Toast.makeText(this, "Script saved", Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start();
            });

        mImageUploadLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) return;
                saveAndInsertImageUriToCli(uri, "upload");
            });

        setupDrawerCommandButtons();

        setupNewTermuxFeatures();

        registerForContextMenu(mTerminalView);

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        try {
            // Start the {@link TermuxService} and make it run regardless of who is bound to it
            Intent serviceIntent = new Intent(this, TermuxService.class);
            startService(serviceIntent);

            // Attempt to bind to the service, this will call the {@link #onServiceConnected(ComponentName, IBinder)}
            // callback if it succeeds.
            if (!bindService(serviceIntent, this, 0))
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG,"TermuxActivity failed to start TermuxService", e);
            Logger.showToast(this,
                getString(e.getMessage() != null && e.getMessage().contains("app is in background") ?
                    R.string.error_termux_service_start_failed_bg : R.string.error_termux_service_start_failed_general),
                true);
            mIsInvalidState = true;
            return;
        }

        // Send the {@link TermuxConstants#BROADCAST_TERMUX_OPENED} broadcast to notify apps that Termux
        // app has been opened.
        TermuxUtils.sendTermuxOpenedBroadcast(this);

        // Update notice overlay (pill, highlights dialog, download card)
        UpdateUi.attach(this);

        mFileTabContainer = findViewById(R.id.file_tab_container);
        handleFileIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleFileIntent(intent);
    }

    private void handleFileIntent(Intent intent) {
        if (intent == null) return;
        String filePath = intent.getStringExtra("OPEN_FILE_PATH");
        if (filePath != null && !filePath.isEmpty()) {
            File file = new File(filePath);
            if (file.exists()) {
                openFileTab(file);
                return;
            }
        }
        android.net.Uri uri = intent.getData();
        if (uri != null && "file".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null) {
                File file = new File(path);
                if (file.exists()) {
                    openFileTab(file);
                }
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        Logger.logDebug(LOG_TAG, "onStart");

        if (mIsInvalidState) return;

        mIsVisible = true;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStart();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStart();

        // Coming back to NewTermux reopens a deck the user closed. Not in onResume(): the Duo
        // pauses/resumes this activity whenever focus moves between its two screens.
        DeckBridge.setUserHidden(false);

        if (mPreferences.isTerminalMarginAdjustmentEnabled())
            addTermuxActivityRootViewGlobalLayoutListener();

        registerTermuxActivityBroadcastReceiver();

        // NOTE: the keep-alive wake lock is intentionally NOT released here anymore. It is now tied
        // to the session lifecycle in TermuxService (held while any session is alive), not to the
        // Activity lifecycle — releasing it on every foreground transition is what made it flap.
    }

    @Override
    public void onResume() {
        super.onResume();

        Logger.logVerbose(LOG_TAG, "onResume");

        if (mIsInvalidState) return;

        // Re-apply extra keys settings when returning from Settings
        boolean currentDrawerMode = com.newtermux.features.NewTermuxSettings.isExtraKeysInDrawer(this);
        if (currentDrawerMode != mExtraKeysInDrawerModeAtCreation) {
            recreate();
            return;
        }
        if (!currentDrawerMode) {
            ViewPager vp = getTerminalToolbarViewPager();
            if (vp != null) {
                vp.setVisibility(com.newtermux.features.NewTermuxSettings.isExtraKeysVisible(this)
                    ? View.VISIBLE : View.GONE);
            }
        }

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onResume();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();

        // Check if a crash happened on last run of the app or if a plugin crashed and show a
        // notification with the crash details if it did
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG);

        mIsOnResumeAfterOnCreate = false;
        applyAccentColor();
        applyFeatureSettings();
        DeckBridge.setActivity(this);
        applyLayoutMode(getResources().getConfiguration().orientation);
        UpdateUi.onResume(this);
        mTabStatusHandler.removeCallbacks(mTabStatusCheckRunnable);
        mTabStatusHandler.postDelayed(mTabStatusCheckRunnable, 1500);
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.checkForFontAndColors();

        // One-time nudge to allowlist NewTermux from battery optimization / Doze, so the keep-alive
        // foreground service actually survives backgrounding. Gated: only shows if keep-alive is on,
        // we are not already exempt, and we have not asked before.
        maybePromptBatteryOptimization();

        // Run any command injected by Settings (e.g. "pkg install zsh\n")
        String pendingCmd = com.newtermux.features.NewTermuxSettings.getPendingCommand(this);
        if (pendingCmd != null) {
            com.newtermux.features.NewTermuxSettings.clearPendingCommand(this);
            TerminalSession session = getCurrentSession();
            if (session != null) {
                byte[] bytes = pendingCmd.getBytes();
                session.write(bytes, 0, bytes.length);
            }
        }
    }

    /**
     * Show a one-time dialog asking the user to allowlist NewTermux from battery optimization /
     * Doze. Without the exemption, Android can still tear the process down in the background even
     * with a foreground service + wake lock. Gated so it is unobtrusive: only when keep-alive is
     * enabled, we are not already exempt, and we have not prompted before.
     */
    private void maybePromptBatteryOptimization() {
        if (!com.newtermux.features.NewTermuxSettings.isKeepAliveInBackground(this)) return;
        if (com.newtermux.features.NewTermuxSettings.wasBatteryOptPrompted(this)) return;
        if (PermissionUtils.checkIfBatteryOptimizationsDisabled(this)) return;

        // Mark as prompted up front so this only ever appears once, regardless of the user's choice.
        com.newtermux.features.NewTermuxSettings.setBatteryOptPrompted(this, true);

        new AlertDialog.Builder(this)
            .setTitle(R.string.battery_opt_prompt_title)
            .setMessage(R.string.battery_opt_prompt_message)
            .setPositiveButton(R.string.battery_opt_prompt_allow, (d, w) -> {
                try {
                    PermissionUtils.requestDisableBatteryOptimizations(this);
                } catch (Exception e) {
                    Logger.logError(LOG_TAG, "Failed to request battery optimization exemption: " + e);
                }
            })
            .setNegativeButton(R.string.battery_opt_prompt_later, null)
            .show();
    }

    private void applyAccentColor() {
        int color = NewTermuxTheme.getAccentColor(this);

        // App title
        android.widget.TextView tvTitle = findViewById(R.id.tv_app_title);
        if (tvTitle != null) tvTitle.setTextColor(color);

        // AC toggle button
        android.widget.TextView btnAC = findViewById(R.id.btn_autocorrect_toggle);
        if (btnAC != null) {
            boolean acOn = mTerminalView != null && mTerminalView.isKeyboardSuggestionsEnabled();
            btnAC.setTextColor(acOn ? color : getResources().getColor(R.color.nt_on_surface, getTheme()));
        }

        // STT button (idle state)
        if (mBtnSTT != null) mBtnSTT.setColorFilter(color);

        // Settings cog button
        ImageButton btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null) btnSettings.setColorFilter(color);

        // Extra keys active styling
        if (mExtraKeysView != null && mTermuxTerminalExtraKeys != null) {
            int activeBg = androidx.core.graphics.ColorUtils.setAlphaComponent(color, 0x44);
            mExtraKeysView.setButtonColors(0xFFE6EDF3, color, 0xFF1C2128, activeBg);
            mExtraKeysView.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
        }

        // Session chips
        updateSessionTabs();

        // Drawer command buttons
        setupDrawerCommandButtons();
    }

    private void applyFeatureSettings() {
        // Toolbar secondary actions are cleanly housed in the More (⋮) menu
        setVisible(R.id.btn_autocorrect_toggle, false);
        setVisible(R.id.btn_root_toggle, false);
        setVisible(R.id.btn_stt, false);
        setVisible(R.id.btn_packages_menu, false);
        setVisible(R.id.btn_clear_terminal, false);
        // Hide/show the whole scroll container (chip group lives inside it)
        setVisible(R.id.session_tabs_scroll, NewTermuxSettings.isSessionTabsEnabled(this));
        // Autocorrect initial enabled state
        if (mAutoCorrectHandler != null)
            mAutoCorrectHandler.setEnabled(NewTermuxSettings.isAutocorrectEnabled(this));
    }

    private void setVisible(int id, boolean visible) {
        View v = findViewById(id);
        if (v != null) v.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onStop() {
        super.onStop();

        Logger.logDebug(LOG_TAG, "onStop");

        if (mIsInvalidState) return;

        mTabStatusHandler.removeCallbacks(mTabStatusCheckRunnable);

        mIsVisible = false;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStop();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStop();

        removeTermuxActivityRootViewGlobalLayoutListener();

        unregisterTermuxActivityBroadcastReceiver();
        getDrawer().closeDrawers();

        // Give the second screen back (launcher, other apps' keyboard) while NewTermux is not in
        // front; onResume() reopens the deck.
        DeckBridge.hideDeck();

        // Snapshot the current tab list to disk as we go to background, so that if the process is
        // torn down while a game is in the foreground, the next launch can rehydrate the tabs. The
        // keep-alive wake lock is already held continuously by the service (session-driven).
        if (mTermuxService != null)
            SessionStatePersister.save(this, mTermuxService.getTermuxSessions());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Logger.logDebug(LOG_TAG, "onDestroy");

        if (mIsInvalidState) return;

        DeckBridge.hideDeck();
        DeckBridge.clearActivity(this);

        if (mSpeechInputManager != null) { mSpeechInputManager.destroy(); mSpeechInputManager = null; }
        if (mAutoCorrectHandler != null) { mAutoCorrectHandler.destroy(); mAutoCorrectHandler = null; }
        mTabStatusHandler.removeCallbacksAndMessages(null);

        if (mBrowserContainer != null) {
            mBrowserContainer.disposeComposition();
            mBrowserContainer = null;
        }
        if (mFileTabContainer != null) {
            mFileTabContainer.disposeComposition();
            mFileTabContainer = null;
        }

        if (mTermuxService != null) {
            // Do not leave service and session clients with references to activity.
            mTermuxService.unsetTermuxTerminalSessionClient();
            mTermuxService = null;
        }

        try {
            unbindService(this);
        } catch (Exception e) {
            // ignore.
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        Logger.logVerbose(LOG_TAG, "onSaveInstanceState");

        super.onSaveInstanceState(savedInstanceState);
        saveTerminalToolbarTextInput(savedInstanceState);
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true);
    }





    /**
     * Part of the {@link ServiceConnection} interface. The service is bound with
     * {@link #bindService(Intent, ServiceConnection, int)} in {@link #onCreate(Bundle)} which will cause a call to this
     * callback method.
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        Logger.logDebug(LOG_TAG, "onServiceConnected");

        mTermuxService = ((TermuxService.LocalBinder) service).service;

        final Intent intent = getIntent();
        setIntent(null);

        if (mTermuxService.isTermuxSessionsEmpty()) {
            if (mIsVisible) {
                TermuxInstaller.setupBootstrapIfNeeded(TermuxActivity.this, () -> {
                    if (mTermuxService == null) return; // Activity might have been destroyed.
                    try {
                        boolean launchFailsafe = false;
                        if (intent != null && intent.getExtras() != null) {
                            launchFailsafe = intent.getExtras().getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                        }
                        mTermuxTerminalSessionActivityClient.addNewSession(launchFailsafe, null);
                    } catch (WindowManager.BadTokenException e) {
                        // Activity finished - ignore.
                    }
                });
            } else {
                // The service connected while not in foreground - just bail out.
                finishActivityIfNotFinishing();
            }
        } else {
            // If termux was started from launcher "New session" shortcut and activity is recreated,
            // then the original intent will be re-delivered, resulting in a new session being re-added
            // each time.
            if (!mIsActivityRecreated && intent != null && Intent.ACTION_RUN.equals(intent.getAction())) {
                // Android 7.1 app shortcut from res/xml/shortcuts.xml.
                boolean isFailSafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                mTermuxTerminalSessionActivityClient.addNewSession(isFailSafe, null);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
            }
        }

        // Update the {@link TerminalSession} and {@link TerminalEmulator} clients.
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);

        // Now that the service is connected and sessions exist, populate the session chips.
        updateSessionTabs();

        // Ensure zsh plugins + shell are set up for existing installs that skipped first-run.
        new Thread(() -> TermuxInstaller.installZshPlugins(this)).start();

        // Request storage permission on every launch; no-op if already granted.
        // Demo build: skip entirely — demo has no real filesystem, setupStorageSymlinks
        // would try to access /data/data/com.termux/ which the demo package cannot reach.
        if (!BuildConfig.IS_DEMO) requestStoragePermission(false);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected");

        // Respect being stopped from the {@link TermuxService} notification action.
        finishActivityIfNotFinishing();
    }






    private void reloadProperties() {
        mProperties.loadTermuxPropertiesFromDisk();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadProperties();
    }



    private void setActivityTheme() {
        // Update NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());

        // Set activity night mode. If NightMode.SYSTEM is set, then android will automatically
        // trigger recreation of activity when uiMode/dark mode configuration is changed so that
        // day or night theme takes affect.
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
    }

    private void setMargins() {
        RelativeLayout relativeLayout = findViewById(R.id.activity_termux_root_relative_layout);
        int marginHorizontal = mProperties.getTerminalMarginHorizontal();
        int marginVertical = mProperties.getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }



    public void addTermuxActivityRootViewGlobalLayoutListener() {
        getTermuxActivityRootView().getViewTreeObserver().addOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (getTermuxActivityRootView() != null)
            getTermuxActivityRootView().getViewTreeObserver().removeOnGlobalLayoutListener(getTermuxActivityRootView());
    }



    private void setTermuxTerminalViewAndClients() {
        // Set termux terminal view and session clients
        mTermuxTerminalSessionActivityClient = new TermuxTerminalSessionActivityClient(this);
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);

        // Set termux terminal view
        mTerminalView = findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onCreate();

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onCreate();
    }




    private void setTerminalToolbarView(Bundle savedInstanceState) {
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView,
            mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient);

        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;

        boolean inDrawer = com.newtermux.features.NewTermuxSettings.isExtraKeysInDrawer(this);
        mExtraKeysInDrawerModeAtCreation = inDrawer;
        getDrawer().setDrawerLockMode(
            inDrawer ? DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
            Gravity.END);

        if (inDrawer) {
            // Route extra keys into the right drawer instead of the bottom ViewPager
            ExtraKeysView rightEkv = (ExtraKeysView) findViewById(R.id.right_drawer_extra_keys);
            if (rightEkv != null) {
                rightEkv.setExtraKeysViewClient(mTermuxTerminalExtraKeys);
                rightEkv.setButtonTextAllCaps(getProperties().shouldExtraKeysTextBeAllCaps());
                mExtraKeysView = rightEkv;
                rightEkv.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
                // GridLayout buttons use height=0 + FILL — the view needs a fixed height to expand into
                if (mTermuxTerminalExtraKeys.getExtraKeysInfo() != null) {
                    int rowCount = mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length;
                    int totalHeight = Math.round(mTerminalToolbarDefaultHeight * rowCount
                        * mProperties.getTerminalToolbarHeightScaleFactor());
                    ViewGroup.LayoutParams ekParams = rightEkv.getLayoutParams();
                    ekParams.height = totalHeight;
                    rightEkv.setLayoutParams(ekParams);
                }
            }
            // ViewPager stays GONE; no adapter set in drawer mode
        } else {
            if (com.newtermux.features.NewTermuxSettings.isExtraKeysVisible(this)) {
                terminalToolbarViewPager.setVisibility(View.VISIBLE);
            }
            setTerminalToolbarHeight();

            String savedTextInput = null;
            if (savedInstanceState != null)
                savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);

            terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
            terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
        }
    }

    private void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        layoutParams.height = Math.round(mTerminalToolbarDefaultHeight *
            (mTermuxTerminalExtraKeys.getExtraKeysInfo() == null ? 0 : mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length) *
            mProperties.getTerminalToolbarHeightScaleFactor());
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }

    public void toggleTerminalToolbar() {
        if (mExtraKeysInDrawerModeAtCreation) return; // in drawer mode — use right drawer swipe instead
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        final boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(this, (showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar)), true);
        terminalToolbarViewPager.setVisibility(showNow ? View.VISIBLE : View.GONE);
        if (showNow && isTerminalToolbarTextInputViewSelected()) {
            // Focus the text input view if just revealed.
            findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        }
    }

    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;

        final EditText textInputView = findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty()) savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }



    // -----------------------------------------------------------------------------------------
    // NewTermux Features: Speech-to-Text, Root Toggle, AutoCorrect
    // -----------------------------------------------------------------------------------------

    private void setupNewTermuxFeatures() {
        // Initialize managers
        mSpeechInputManager = new SpeechInputManager(this);
        mRootToggleManager = RootToggleManager.getInstance();
        mAutoCorrectHandler = new AutoCorrectHandler(this);
        mPackageManagerMenu = new com.newtermux.features.PackageManagerMenu(this);

        // Autocorrect UI
        mAutocorrectBar = findViewById(R.id.autocorrect_bar);
        mAutocorrectText = findViewById(R.id.autocorrect_text);
        View btnApply = findViewById(R.id.autocorrect_apply_button);
        View btnClose = findViewById(R.id.autocorrect_close_button);
        if (btnApply != null) btnApply.setOnClickListener(v -> applyAutocorrect());
        if (btnClose != null) btnClose.setOnClickListener(v -> hideAutocorrectBar());

        // Wire toolbar buttons
        mBtnSTT = findViewById(R.id.btn_stt);
        if (mBtnSTT != null) {
            mBtnSTT.setOnClickListener(v -> onSTTButtonClicked());
            if (!SpeechInputManager.isAvailable(this)) {
                mBtnSTT.setAlpha(0.4f);
            }
        }

        mBtnRootToggle = findViewById(R.id.btn_root_toggle);
        if (mBtnRootToggle != null) {
            if (RootToggleManager.isDeviceRooted()) {
                mBtnRootToggle.setVisibility(View.VISIBLE);
                mBtnRootToggle.setOnClickListener(v -> onRootToggleClicked());
            } else {
                mBtnRootToggle.setVisibility(View.GONE);
            }
        }

        View btnFiles = findViewById(R.id.btn_files);
        if (btnFiles != null) {
            btnFiles.setOnClickListener(v -> startActivity(new Intent(this, com.termux.app.activities.FileManagerActivity.class)));
        }

        mBrowserContainer = findViewById(R.id.browser_container);

        ImageButton btnExtraKeys = findViewById(R.id.btn_extra_keys);
        if (btnExtraKeys != null) {
            btnExtraKeys.setOnClickListener(v -> {
                ViewPager vp = getTerminalToolbarViewPager();
                if (vp != null) {
                    boolean show = (vp.getVisibility() != View.VISIBLE);
                    vp.setVisibility(show ? View.VISIBLE : View.GONE);
                    NewTermuxSettings.set(this, NewTermuxSettings.KEY_EXTRA_KEYS_VISIBLE, show);
                }
            });
        }

        View btnPackages = findViewById(R.id.btn_packages_menu);
        if (btnPackages != null) {
            btnPackages.setOnClickListener(v -> mPackageManagerMenu.show(v));
            btnPackages.setOnLongClickListener(v -> {
                startActivity(new Intent(this, com.termux.app.activities.PackageManagerActivity.class));
                return true;
            });
        }

        View btnClear = findViewById(R.id.btn_clear_terminal);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                TerminalSession session = getCurrentSession();
                if (session != null) {
                    session.write("clear\n");
                }
            });
        }

        ImageButton btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> openSettings());
        }

        android.widget.TextView btnAC = findViewById(R.id.btn_autocorrect_toggle);
        if (btnAC != null) {
            // Apply saved AC state
            boolean acEnabled = NewTermuxSettings.isKeyboardSuggestionsEnabled(this);
            mTerminalView.setKeyboardSuggestionsEnabled(acEnabled);
            if (mAutoCorrectHandler != null) mAutoCorrectHandler.setEnabled(acEnabled);

            btnAC.setOnClickListener(v -> {
                boolean nowEnabled = !mTerminalView.isKeyboardSuggestionsEnabled();
                mTerminalView.setKeyboardSuggestionsEnabled(nowEnabled);
                if (mAutoCorrectHandler != null) mAutoCorrectHandler.setEnabled(nowEnabled);
                NewTermuxSettings.setKeyboardSuggestions(this, nowEnabled);
                int color = getResources().getColor(
                    nowEnabled ? R.color.nt_primary : R.color.nt_on_surface, getTheme());
                btnAC.setTextColor(color);
            });
        }

        ImageButton btnNewSession = findViewById(R.id.btn_new_session);
        if (btnNewSession != null) {
            btnNewSession.setOnClickListener(v -> {
                com.newtermux.features.NtPopupMenu.showAsDropDown(this, v, null,
                    new String[]{"New Terminal Tab", "New Dev Browser Tab", "Files", "Upload / Paste Image"}, optIdx -> {
                        if (optIdx == 0) {
                            if (mTermuxTerminalSessionActivityClient != null) {
                                mTermuxTerminalSessionActivityClient.addNewSession(false, null);
                            }
                        } else if (optIdx == 1) {
                            openBrowserTab("https://www.google.com");
                        } else if (optIdx == 2) {
                            startActivity(new Intent(this, com.termux.app.activities.FileManagerActivity.class));
                        } else if (optIdx == 3) {
                            promptOrUploadImageToCli(v);
                        }
                    });
            });
        }

        ImageButton btnMoreMenu = findViewById(R.id.btn_more_menu);
        if (btnMoreMenu != null) {
            btnMoreMenu.setOnClickListener(v -> showToolbarMoreMenu(v));
        }

        // Session pip row
        mSessionPipContainer = findViewById(R.id.session_pip_container);

        // Status line (alternative layout, see applyLayoutMode())
        mStatusTabsContainer = findViewById(R.id.status_tabs_container);
        View btnStatusDrawer = findViewById(R.id.status_btn_drawer);
        if (btnStatusDrawer != null) {
            btnStatusDrawer.setOnClickListener(v -> toggleDrawer(Gravity.START));
        }
        View btnStatusDeck = findViewById(R.id.status_btn_deck);
        if (btnStatusDeck != null) {
            btnStatusDeck.setOnClickListener(v -> {
                DeckBridge.setUserHidden(false);
                applyLayoutMode(getResources().getConfiguration().orientation);
            });
        }
        View btnStatusKeys = findViewById(R.id.status_btn_keys);
        if (btnStatusKeys != null) {
            btnStatusKeys.setOnClickListener(v -> {
                if (mExtraKeysInDrawerModeAtCreation) {
                    toggleDrawer(Gravity.END);
                } else {
                    // Same pref that onResume() applies to the extra keys row
                    boolean show = !NewTermuxSettings.isExtraKeysVisible(this);
                    NewTermuxSettings.set(this, NewTermuxSettings.KEY_EXTRA_KEYS_VISIBLE, show);
                    ViewPager vp = getTerminalToolbarViewPager();
                    if (vp != null) vp.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        }

        updateSessionTabs();

        // STT result callback
        mSpeechInputManager.setCallback(new SpeechInputManager.SpeechCallback() {
            @Override
            public void onResult(String text) {
                runOnUiThread(() -> insertSpeechTextToTerminal(text));
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> showToast(error, false));
            }
            @Override
            public void onListeningStarted() {
                runOnUiThread(() -> updateSTTButtonState(true));
            }
            @Override
            public void onListeningStopped() {
                runOnUiThread(() -> updateSTTButtonState(false));
            }
        });
    }

    private static android.graphics.drawable.GradientDrawable createDotDrawable(int status) {
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        int color;
        switch (status) {
            case TAB_STATUS_PROCESSING:
                color = 0xFFE3B341; // Yellow (in progress / processing)
                break;
            case TAB_STATUS_FINISHED:
            case TAB_STATUS_STANDBY:
            default:
                color = 0xFF3FB950; // Green (finished / standby)
                break;
        }
        dot.setColor(color);
        return dot;
    }

    private static void applyStatusDotColor(View dotView, int status) {
        if (dotView != null) {
            dotView.setBackground(createDotDrawable(status));
        }
    }

    private static String[] readProcStatTokens(int pid) {
        java.io.BufferedReader reader = null;
        try {
            reader = new java.io.BufferedReader(new java.io.FileReader("/proc/" + pid + "/stat"), 256);
            String line = reader.readLine();
            if (line != null) {
                int closeParen = line.lastIndexOf(')');
                if (closeParen != -1 && closeParen + 2 < line.length()) {
                    String rest = line.substring(closeParen + 2).trim();
                    return rest.split("\\s+");
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    public static int getTerminalSessionStatus(TerminalSession session) {
        if (session == null) return TAB_STATUS_STANDBY;
        if (!session.isRunning() || session.getPid() <= 0) {
            return TAB_STATUS_FINISHED;
        }
        int pid = session.getPid();
        String[] tokens = readProcStatTokens(pid);
        if (tokens != null && tokens.length > 5) {
            try {
                int pgrp = Integer.parseInt(tokens[2]);
                int tpgid = Integer.parseInt(tokens[5]);
                if (tpgid <= 0 || tpgid == pgrp) {
                    return TAB_STATUS_STANDBY;
                }
                // A foreground process group (e.g., an agent CLI or command) owns the terminal.
                // Check whether it is actively working (recent terminal output, R/D state, or advancing CPU ticks)
                // versus sitting idle in standby waiting for user input.
                long now = System.currentTimeMillis();
                SessionActivityState state;
                synchronized (sSessionActivityMap) {
                    state = sSessionActivityMap.get(session);
                    if (state == null) {
                        state = new SessionActivityState();
                        state.lastActiveTimeMs = now;
                        sSessionActivityMap.put(session, state);
                    }
                }
                String[] fgTokens = (tpgid == pid) ? tokens : readProcStatTokens(tpgid);
                if (fgTokens != null && fgTokens.length > 14) {
                    String procState = fgTokens[0];
                    long utime = Long.parseLong(fgTokens[11]);
                    long stime = Long.parseLong(fgTokens[12]);
                    long cutime = Long.parseLong(fgTokens[13]);
                    long cstime = Long.parseLong(fgTokens[14]);
                    long totalTicks = utime + stime + cutime + cstime;
                    if ("R".equals(procState) || "D".equals(procState)) {
                        state.lastActiveTimeMs = now;
                    } else if (state.lastCpuTicks >= 0 && totalTicks > state.lastCpuTicks) {
                        state.lastActiveTimeMs = now;
                    }
                    state.lastCpuTicks = totalTicks;
                }
                if (now - state.lastActiveTimeMs < 3000L) {
                    return TAB_STATUS_PROCESSING;
                } else {
                    return TAB_STATUS_FINISHED;
                }
            } catch (Exception ignored) {}
        }
        return TAB_STATUS_STANDBY;
    }

    private void pollLiveLocalhostPorts() {
        if (mPortScanInFlight) return;
        mPortScanInFlight = true;
        new Thread(() -> {
            try {
                final java.util.List<Integer> detected = com.termux.app.activities.LocalhostPortScanner.scanLivePorts();
                runOnUiThread(() -> {
                    mPortScanInFlight = false;
                    if (!detected.equals(mLivePorts)) {
                        mLivePorts = detected;
                        updateSessionTabs();
                    }
                });
            } catch (Exception ignored) {
                mPortScanInFlight = false;
            }
        }).start();
    }

    public void refreshTabStatusDots() {
        if (mSessionPipContainer == null) return;
        for (int i = 0; i < mSessionPipContainer.getChildCount(); i++) {
            View child = mSessionPipContainer.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof TabViewHolder) {
                TabViewHolder holder = (TabViewHolder) tag;
                if (holder.type == TAB_TYPE_TERMINAL && holder.session != null && holder.dotView != null) {
                    int status = getTerminalSessionStatus(holder.session);
                    if (holder.currentStatus != status) {
                        holder.currentStatus = status;
                        applyStatusDotColor(holder.dotView, status);
                    }
                } else if (holder.type == TAB_TYPE_BROWSER && holder.dotView != null) {
                    if (holder.currentStatus != mBrowserTabStatus) {
                        holder.currentStatus = mBrowserTabStatus;
                        applyStatusDotColor(holder.dotView, mBrowserTabStatus);
                    }
                } else if (holder.type == TAB_TYPE_FILE && holder.fileTab != null && holder.dotView != null) {
                    if (holder.currentStatus != holder.fileTab.status) {
                        holder.currentStatus = holder.fileTab.status;
                        applyStatusDotColor(holder.dotView, holder.fileTab.status);
                    }
                }
            }
        }
    }

    public void openBrowserTab(String initialUrl) {
        if (mBrowserContainer == null) {
            mBrowserContainer = findViewById(R.id.browser_container);
        }
        if (mBrowserContainer == null) return;

        String targetUrl = (initialUrl != null && !initialUrl.trim().isEmpty())
            ? initialUrl.trim()
            : "https://www.google.com";

        mHasBrowserTab = true;
        mIsBrowserTabActive = true;
        mActiveFileTabIndex = -1;
        mBrowserTabStatus = TAB_STATUS_PROCESSING;

        com.termux.app.activities.DevBrowserBridge.attachTo(
            mBrowserContainer,
            this,
            targetUrl,
            title -> runOnUiThread(() -> {
                mBrowserTabTitle = (title != null && !title.isEmpty()) ? title : "Dev Browser";
                updateSessionTabs();
            }),
            loading -> runOnUiThread(() -> {
                mBrowserTabStatus = loading ? TAB_STATUS_PROCESSING : TAB_STATUS_FINISHED;
                updateSessionTabs();
            }),
            () -> runOnUiThread(this::closeBrowserTab),
            (canBack, goBackRunnable) -> runOnUiThread(() -> {
                mBrowserCanGoBack = canBack;
                mBrowserGoBack = goBackRunnable;
            }),
            reloadRunnable -> runOnUiThread(() -> {
                mBrowserReload = reloadRunnable;
            })
        );

        DrawerLayout drawer = getDrawer();
        if (drawer != null) {
            drawer.setVisibility(View.GONE);
        }
        if (mFileTabContainer != null) {
            mFileTabContainer.setVisibility(View.GONE);
        }
        if (mBrowserContainer != null) {
            mBrowserContainer.setVisibility(View.VISIBLE);
        }
        updateSessionTabs();
    }

    public void closeBrowserTab() {
        mHasBrowserTab = false;
        mIsBrowserTabActive = false;
        mBrowserTabTitle = "Dev Browser";
        mBrowserTabStatus = TAB_STATUS_STANDBY;
        mBrowserGoBack = null;
        mBrowserCanGoBack = false;
        mBrowserReload = null;

        if (mBrowserContainer != null) {
            mBrowserContainer.setVisibility(View.GONE);
            mBrowserContainer.disposeComposition();
        }
        if (mActiveFileTabIndex >= 0 && mActiveFileTabIndex < mOpenFileTabs.size()) {
            switchToFileTab(mActiveFileTabIndex);
        } else if (!mOpenFileTabs.isEmpty()) {
            switchToFileTab(0);
        } else {
            mActiveFileTabIndex = -1;
            DrawerLayout drawer = getDrawer();
            if (drawer != null) {
                drawer.setVisibility(View.VISIBLE);
            }
            if (mTerminalView != null) {
                mTerminalView.requestFocus();
            }
        }
        updateSessionTabs();
    }

    public void switchToBrowserTab() {
        if (!mHasBrowserTab) {
            openBrowserTab("https://www.google.com");
            return;
        }
        mIsBrowserTabActive = true;
        mActiveFileTabIndex = -1;
        DrawerLayout drawer = getDrawer();
        if (drawer != null) {
            drawer.setVisibility(View.GONE);
        }
        if (mFileTabContainer != null) {
            mFileTabContainer.setVisibility(View.GONE);
        }
        if (mBrowserContainer != null) {
            mBrowserContainer.setVisibility(View.VISIBLE);
        }
        updateSessionTabs();
    }

    public void switchToTerminalTab(TerminalSession session) {
        mIsBrowserTabActive = false;
        mActiveFileTabIndex = -1;
        if (mBrowserContainer != null) {
            mBrowserContainer.setVisibility(View.GONE);
        }
        if (mFileTabContainer != null) {
            mFileTabContainer.setVisibility(View.GONE);
        }
        DrawerLayout drawer = getDrawer();
        if (drawer != null) {
            drawer.setVisibility(View.VISIBLE);
        }
        if (session != null && mTermuxTerminalSessionActivityClient != null) {
            mTermuxTerminalSessionActivityClient.setCurrentSession(session);
        }
        if (mTerminalView != null) {
            mTerminalView.requestFocus();
        }
        updateSessionTabs();
    }

    public void openFileTab(File file) {
        if (file == null || !file.exists()) return;
        if (mFileTabContainer == null) {
            mFileTabContainer = findViewById(R.id.file_tab_container);
        }
        if (mFileTabContainer == null) return;

        // Check if already open
        for (int i = 0; i < mOpenFileTabs.size(); i++) {
            if (mOpenFileTabs.get(i).path.equals(file.getAbsolutePath())) {
                switchToFileTab(i);
                return;
            }
        }

        FileTab newTab = new FileTab(file);
        mOpenFileTabs.add(newTab);
        switchToFileTab(mOpenFileTabs.size() - 1);
    }

    public void closeFileTab(int index) {
        if (index < 0 || index >= mOpenFileTabs.size()) return;
        mOpenFileTabs.remove(index);

        if (mActiveFileTabIndex == index) {
            if (!mOpenFileTabs.isEmpty()) {
                int nextIndex = Math.min(index, mOpenFileTabs.size() - 1);
                switchToFileTab(nextIndex);
            } else {
                mActiveFileTabIndex = -1;
                if (mFileTabContainer != null) {
                    mFileTabContainer.setVisibility(View.GONE);
                    mFileTabContainer.disposeComposition();
                }
                if (mHasBrowserTab) {
                    switchToBrowserTab();
                } else {
                    switchToTerminalTab(getCurrentSession());
                }
            }
        } else if (mActiveFileTabIndex > index) {
            mActiveFileTabIndex--;
            updateSessionTabs();
        } else {
            updateSessionTabs();
        }
    }

    public void switchToFileTab(int index) {
        if (index < 0 || index >= mOpenFileTabs.size()) return;
        mActiveFileTabIndex = index;
        mIsBrowserTabActive = false;

        DrawerLayout drawer = getDrawer();
        if (drawer != null) {
            drawer.setVisibility(View.GONE);
        }
        if (mBrowserContainer != null) {
            mBrowserContainer.setVisibility(View.GONE);
        }
        if (mFileTabContainer == null) {
            mFileTabContainer = findViewById(R.id.file_tab_container);
        }
        if (mFileTabContainer != null) {
            mFileTabContainer.setVisibility(View.VISIBLE);
            FileTab tab = mOpenFileTabs.get(index);
            com.termux.app.activities.FileTabBridge.attachTo(
                mFileTabContainer,
                this,
                tab.file,
                () -> runOnUiThread(() -> closeFileTab(mActiveFileTabIndex)),
                () -> runOnUiThread(() -> {
                    tab.status = TAB_STATUS_FINISHED;
                    updateSessionTabs();
                })
            );
        }
        updateSessionTabs();
    }

    private void showFileTabPopupMenu(View anchor, int index, FileTab fileTab) {
        com.newtermux.features.NtPopupMenu.showAsDropDown(this, anchor, null,
            new String[]{"Close Tab", "Close Others", "Share File", "Copy Path"}, idx -> {
                if (idx == 0) {
                    closeFileTab(index);
                } else if (idx == 1) {
                    FileTab keep = mOpenFileTabs.get(index);
                    mOpenFileTabs.clear();
                    mOpenFileTabs.add(keep);
                    switchToFileTab(0);
                } else if (idx == 2) {
                    shareFileUri(fileTab.file);
                } else if (idx == 3) {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("path", fileTab.file.getAbsolutePath()));
                        android.widget.Toast.makeText(this, "Path copied", android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            });
    }

    private void shareFileUri(File file) {
        try {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share " + file.getName()));
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Share error: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void showBrowserTabPopupMenu(View anchor) {
        com.newtermux.features.NtPopupMenu.showAsDropDown(this, anchor, null,
            new String[]{"Reload", "Close"}, idx -> {
                if (idx == 0) {
                    if (mBrowserReload != null) {
                        mBrowserReload.run();
                    }
                } else {
                    closeBrowserTab();
                }
            });
    }

    /** Update the horizontal session tabs row (1-row columned tabs for terminal, browser, and files). */
    public void updateSessionTabs() {
        if (mSessionPipContainer == null || mTermuxService == null) return;

        mSessionPipContainer.removeAllViews();
        java.util.List<com.termux.shared.termux.shell.command.runner.terminal.TermuxSession> sessions = mTermuxService.getTermuxSessions();
        TerminalSession currentSession = getCurrentSession();

        float density   = getResources().getDisplayMetrics().density;
        int tabHeightPx = (int) (30 * density);
        int marginPx    = (int) (6 * density);
        int accentColor = NewTermuxTheme.getAccentColor(this);
        View activeTabView = null;

        // 1. Terminal session tabs
        for (int i = 0; i < sessions.size(); i++) {
            com.termux.shared.termux.shell.command.runner.terminal.TermuxSession termuxSession = sessions.get(i);
            TerminalSession session = termuxSession.getTerminalSession();
            boolean isTabActive = (!mIsBrowserTabActive && mActiveFileTabIndex < 0 && session == currentSession);

            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.HORIZONTAL);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setPadding((int) (8 * density), 0, (int) (8 * density), 0);
            LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, tabHeightPx);
            tabLp.setMarginEnd(marginPx);
            tab.setLayoutParams(tabLp);

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(6 * density);
            if (isTabActive) {
                bg.setColor(0xFF21262D);
                bg.setStroke((int) (1.5f * density), accentColor);
            } else {
                bg.setColor(0xFF161B22);
                bg.setStroke((int) (1 * density), 0xFF30363D);
            }
            tab.setBackground(bg);

            // Leftmost status dot (Yellow = processing, Green = finished / standby)
            int status = getTerminalSessionStatus(session);
            View dot = new View(this);
            int dotSize = (int) (7 * density);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
            dotLp.setMarginEnd((int) (6 * density));
            dot.setLayoutParams(dotLp);
            dot.setBackground(createDotDrawable(status));
            tab.addView(dot);

            // Terminal icon (>_)
            ImageView icon = new ImageView(this);
            int iconSize = (int) (14 * density);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconLp.setMarginEnd((int) (6 * density));
            icon.setLayoutParams(iconLp);
            icon.setImageResource(R.drawable.ic_tab_terminal);
            icon.setColorFilter(isTabActive ? accentColor : 0xFF8B949E, android.graphics.PorterDuff.Mode.SRC_IN);
            tab.addView(icon);

            // Session title
            TextView title = new TextView(this);
            String displayName = (session.mSessionName != null && !session.mSessionName.isEmpty())
                    ? session.mSessionName : "#" + (i + 1);
            title.setText(displayName);
            title.setTypeface(android.graphics.Typeface.MONOSPACE, isTabActive ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            title.setTextSize(12f);
            title.setTextColor(isTabActive ? 0xFFFFFFFF : 0xFFC9D1D9);
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setMaxWidth((int) (130 * density));
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            title.setLayoutParams(titleLp);
            tab.addView(title);

            TabViewHolder holder = new TabViewHolder();
            holder.type = TAB_TYPE_TERMINAL;
            holder.session = session;
            holder.dotView = dot;
            holder.titleView = title;
            holder.iconView = icon;
            holder.currentStatus = status;
            tab.setTag(holder);

            tab.setOnClickListener(v -> switchToTerminalTab(session));
            tab.setOnLongClickListener(v -> {
                showSessionPopupMenu(v, session);
                return true;
            });

            mSessionPipContainer.addView(tab);
            if (isTabActive) activeTabView = tab;
        }

        // 2. In-tab Browser (if open)
        if (mHasBrowserTab) {
            boolean isBrowserActive = (mIsBrowserTabActive && mActiveFileTabIndex < 0);
            LinearLayout bTab = new LinearLayout(this);
            bTab.setOrientation(LinearLayout.HORIZONTAL);
            bTab.setGravity(Gravity.CENTER_VERTICAL);
            bTab.setPadding((int) (8 * density), 0, (int) (8 * density), 0);
            LinearLayout.LayoutParams bTabLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, tabHeightPx);
            bTabLp.setMarginEnd(marginPx);
            bTab.setLayoutParams(bTabLp);

            android.graphics.drawable.GradientDrawable bBg = new android.graphics.drawable.GradientDrawable();
            bBg.setCornerRadius(6 * density);
            if (isBrowserActive) {
                bBg.setColor(0xFF21262D);
                bBg.setStroke((int) (1.5f * density), accentColor);
            } else {
                bBg.setColor(0xFF161B22);
                bBg.setStroke((int) (1 * density), 0xFF30363D);
            }
            bTab.setBackground(bBg);

            // Leftmost status dot (Yellow = loading, Green = loaded / idle)
            View bDot = new View(this);
            int dotSize = (int) (7 * density);
            LinearLayout.LayoutParams bDotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
            bDotLp.setMarginEnd((int) (6 * density));
            bDot.setLayoutParams(bDotLp);
            bDot.setBackground(createDotDrawable(mBrowserTabStatus));
            bTab.addView(bDot);

            // Browser icon (Globe)
            ImageView bIcon = new ImageView(this);
            int iconSize = (int) (14 * density);
            LinearLayout.LayoutParams bIconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
            bIconLp.setMarginEnd((int) (6 * density));
            bIcon.setLayoutParams(bIconLp);
            bIcon.setImageResource(R.drawable.ic_tab_browser);
            bIcon.setColorFilter(isBrowserActive ? accentColor : 0xFF8B949E, android.graphics.PorterDuff.Mode.SRC_IN);
            bTab.addView(bIcon);

            // Browser title
            TextView bTitle = new TextView(this);
            bTitle.setText(mBrowserTabTitle != null ? mBrowserTabTitle : "Dev Browser");
            bTitle.setTypeface(android.graphics.Typeface.MONOSPACE, isBrowserActive ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            bTitle.setTextSize(12f);
            bTitle.setTextColor(isBrowserActive ? 0xFFFFFFFF : 0xFFC9D1D9);
            bTitle.setMaxLines(1);
            bTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            bTitle.setMaxWidth((int) (130 * density));
            LinearLayout.LayoutParams bTitleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bTitle.setLayoutParams(bTitleLp);
            bTab.addView(bTitle);

            TabViewHolder holder = new TabViewHolder();
            holder.type = TAB_TYPE_BROWSER;
            holder.dotView = bDot;
            holder.titleView = bTitle;
            holder.iconView = bIcon;
            holder.currentStatus = mBrowserTabStatus;
            bTab.setTag(holder);

            bTab.setOnClickListener(v -> switchToBrowserTab());
            bTab.setOnLongClickListener(v -> {
                showBrowserTabPopupMenu(v);
                return true;
            });

            mSessionPipContainer.addView(bTab);
            if (isBrowserActive) activeTabView = bTab;
        }

        // 3. Open File Tabs
        for (int i = 0; i < mOpenFileTabs.size(); i++) {
            final int fileIdx = i;
            FileTab fileTab = mOpenFileTabs.get(i);
            boolean isFileActive = (mActiveFileTabIndex == fileIdx);

            LinearLayout fTab = new LinearLayout(this);
            fTab.setOrientation(LinearLayout.HORIZONTAL);
            fTab.setGravity(Gravity.CENTER_VERTICAL);
            fTab.setPadding((int) (8 * density), 0, (int) (8 * density), 0);
            LinearLayout.LayoutParams fTabLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, tabHeightPx);
            fTabLp.setMarginEnd(marginPx);
            fTab.setLayoutParams(fTabLp);

            android.graphics.drawable.GradientDrawable fBg = new android.graphics.drawable.GradientDrawable();
            fBg.setCornerRadius(6 * density);
            if (isFileActive) {
                fBg.setColor(0xFF21262D);
                fBg.setStroke((int) (1.5f * density), accentColor);
            } else {
                fBg.setColor(0xFF161B22);
                fBg.setStroke((int) (1 * density), 0xFF30363D);
            }
            fTab.setBackground(fBg);

            // Leftmost status dot (Green = standby/saved)
            View fDot = new View(this);
            int dotSize = (int) (7 * density);
            LinearLayout.LayoutParams fDotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
            fDotLp.setMarginEnd((int) (6 * density));
            fDot.setLayoutParams(fDotLp);
            fDot.setBackground(createDotDrawable(fileTab.status));
            fTab.addView(fDot);

            // File icon
            ImageView fIcon = new ImageView(this);
            int iconSize = (int) (14 * density);
            LinearLayout.LayoutParams fIconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
            fIconLp.setMarginEnd((int) (6 * density));
            fIcon.setLayoutParams(fIconLp);
            fIcon.setImageResource(R.drawable.ic_tab_file);
            fIcon.setColorFilter(isFileActive ? accentColor : 0xFF8B949E, android.graphics.PorterDuff.Mode.SRC_IN);
            fTab.addView(fIcon);

            // File title
            TextView fTitle = new TextView(this);
            fTitle.setText(fileTab.name);
            fTitle.setTypeface(android.graphics.Typeface.MONOSPACE, isFileActive ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            fTitle.setTextSize(12f);
            fTitle.setTextColor(isFileActive ? 0xFFFFFFFF : 0xFFC9D1D9);
            fTitle.setMaxLines(1);
            fTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            fTitle.setMaxWidth((int) (130 * density));
            LinearLayout.LayoutParams fTitleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            fTitle.setLayoutParams(fTitleLp);
            fTab.addView(fTitle);

            TabViewHolder holder = new TabViewHolder();
            holder.type = TAB_TYPE_FILE;
            holder.fileTab = fileTab;
            holder.dotView = fDot;
            holder.titleView = fTitle;
            holder.iconView = fIcon;
            holder.currentStatus = fileTab.status;
            fTab.setTag(holder);

            fTab.setOnClickListener(v -> switchToFileTab(fileIdx));
            fTab.setOnLongClickListener(v -> {
                showFileTabPopupMenu(v, fileIdx, fileTab);
                return true;
            });

            mSessionPipContainer.addView(fTab);
            if (isFileActive) activeTabView = fTab;
        }

        // 4. Live Localhost Ports Pill (if any local server is running)
        if (mLivePorts != null && !mLivePorts.isEmpty()) {
            final java.util.List<Integer> portsSnapshot = new java.util.ArrayList<>(mLivePorts);
            LinearLayout liveChip = new LinearLayout(this);
            liveChip.setOrientation(LinearLayout.HORIZONTAL);
            liveChip.setGravity(Gravity.CENTER_VERTICAL);
            liveChip.setPadding((int) (8 * density), 0, (int) (8 * density), 0);
            LinearLayout.LayoutParams liveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, tabHeightPx);
            liveLp.setMarginEnd(marginPx);
            liveChip.setLayoutParams(liveLp);

            android.graphics.drawable.GradientDrawable liveBg = new android.graphics.drawable.GradientDrawable();
            liveBg.setCornerRadius(6 * density);
            liveBg.setColor(0xFF14261C);
            liveBg.setStroke((int) (1 * density), 0xFF3FB950);
            liveChip.setBackground(liveBg);

            View liveDot = new View(this);
            int dotSize = (int) (7 * density);
            LinearLayout.LayoutParams liveDotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
            liveDotLp.setMarginEnd((int) (5 * density));
            liveDot.setLayoutParams(liveDotLp);
            liveDot.setBackground(createDotDrawable(TAB_STATUS_FINISHED));
            liveChip.addView(liveDot);

            TextView liveLabel = new TextView(this);
            String labelText = portsSnapshot.size() == 1
                ? ":" + portsSnapshot.get(0)
                : ":" + portsSnapshot.get(0) + " (+" + (portsSnapshot.size() - 1) + ")";
            liveLabel.setText(labelText);
            liveLabel.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
            liveLabel.setTextSize(11.5f);
            liveLabel.setTextColor(0xFF3FB950);
            liveChip.addView(liveLabel);

            liveChip.setOnClickListener(v -> {
                if (portsSnapshot.size() == 1) {
                    openBrowserTab("http://localhost:" + portsSnapshot.get(0));
                } else {
                    String[] items = new String[portsSnapshot.size()];
                    for (int idx = 0; idx < portsSnapshot.size(); idx++) {
                        items[idx] = "http://localhost:" + portsSnapshot.get(idx);
                    }
                    com.newtermux.features.NtPopupMenu.showAsDropDown(this, v, "Live Localhost", items, sel -> {
                        if (sel >= 0 && sel < portsSnapshot.size()) {
                            openBrowserTab("http://localhost:" + portsSnapshot.get(sel));
                        }
                    });
                }
            });
            mSessionPipContainer.addView(liveChip);
        }

        // 5. Plus "+" button for new tab
        TextView addTab = new TextView(this);
        addTab.setText("+");
        addTab.setTextSize(16f);
        addTab.setTextColor(0xFF8B949E);
        addTab.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams((int) (28 * density), tabHeightPx);
        addTab.setLayoutParams(addLp);
        android.graphics.drawable.GradientDrawable addBg = new android.graphics.drawable.GradientDrawable();
        addBg.setCornerRadius(6 * density);
        addBg.setColor(0xFF161B22);
        addBg.setStroke((int) (1 * density), 0xFF30363D);
        addTab.setBackground(addBg);
        addTab.setOnClickListener(v -> {
            com.newtermux.features.NtPopupMenu.showAsDropDown(this, v, null,
                new String[]{"New Terminal Tab", "New Dev Browser Tab", "Files", "Upload / Paste Image"}, optIdx -> {
                    if (optIdx == 0) {
                        if (mTermuxTerminalSessionActivityClient != null) {
                            mTermuxTerminalSessionActivityClient.addNewSession(false, null);
                        }
                    } else if (optIdx == 1) {
                        openBrowserTab("https://www.google.com");
                    } else if (optIdx == 2) {
                        startActivity(new Intent(this, com.termux.app.activities.FileManagerActivity.class));
                    } else if (optIdx == 3) {
                        promptOrUploadImageToCli(v);
                    }
                });
        });
        mSessionPipContainer.addView(addTab);

        // Keep active tab in view
        if (activeTabView != null && mSessionPipContainer.getParent() instanceof android.widget.HorizontalScrollView) {
            final View tabToShow = activeTabView;
            final android.widget.HorizontalScrollView scroll = (android.widget.HorizontalScrollView) mSessionPipContainer.getParent();
            scroll.post(() -> scroll.smoothScrollTo(Math.max(0, tabToShow.getLeft() - (scroll.getWidth() / 3)), 0));
        }

        updateStatusLineTabs(sessions, currentSession);
        DeckBridge.notifyChanged();
    }

    /** Fill the status line with one text tab per session ("1:name"); the current one is highlighted. */
    private void updateStatusLineTabs(java.util.List<com.termux.shared.termux.shell.command.runner.terminal.TermuxSession> sessions,
                                      TerminalSession currentSession) {
        if (mStatusTabsContainer == null) return;

        mStatusTabsContainer.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int accent = NewTermuxTheme.getAccentColor(this);
        int onAccent = androidx.core.graphics.ColorUtils.calculateLuminance(accent) > 0.5 ? 0xFF1B1029 : 0xFFFFFFFF;
        View activeTab = null;

        for (int i = 0; i < sessions.size(); i++) {
            TerminalSession session = sessions.get(i).getTerminalSession();
            boolean active = session == currentSession;
            String name = (session.mSessionName != null && !session.mSessionName.isEmpty())
                    ? session.mSessionName : "#" + (i + 1);

            TextView tab = new TextView(this);
            tab.setText((i + 1) + ":" + name);
            tab.setTypeface(android.graphics.Typeface.MONOSPACE);
            tab.setTextSize(13f);
            tab.setMaxLines(1);
            tab.setMaxWidth((int) (180 * density));
            tab.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding((int) (10 * density), 0, (int) (10 * density), 0);
            tab.setTextColor(active ? onAccent : 0xFFCFC6DD);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(6 * density);
            bg.setColor(active ? accent : 0x00000000);
            tab.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, (int) (30 * density));
            lp.setMarginEnd((int) (4 * density));
            tab.setLayoutParams(lp);

            tab.setOnClickListener(v -> {
                if (mTermuxTerminalSessionActivityClient != null) {
                    mTermuxTerminalSessionActivityClient.setCurrentSession(session);
                    updateSessionTabs();
                }
            });
            tab.setOnLongClickListener(v -> {
                showSessionPopupMenu(v, session);
                return true;
            });

            mStatusTabsContainer.addView(tab);
            if (active) activeTab = tab;
        }

        // Keep the current tab in view when there are more tabs than fit
        if (activeTab != null && mStatusTabsContainer.getParent() instanceof android.widget.HorizontalScrollView) {
            final View tabToShow = activeTab;
            final android.widget.HorizontalScrollView scroll = (android.widget.HorizontalScrollView) mStatusTabsContainer.getParent();
            scroll.post(() -> scroll.smoothScrollTo(Math.max(0, tabToShow.getLeft() - tabToShow.getWidth()), 0));
        }
    }

    private void toggleDrawer(int gravity) {
        DrawerLayout drawer = getDrawer();
        if (drawer.isDrawerOpen(gravity)) drawer.closeDrawer(gravity);
        else drawer.openDrawer(gravity);
    }

    // -----------------------------------------------------------------------------------------
    // Screen layout per orientation (Settings → Appearance → Layout)
    // -----------------------------------------------------------------------------------------

    private static final int[] TOOLBAR_BUTTON_IDS = {
        R.id.btn_autocorrect_toggle, R.id.btn_files, R.id.btn_packages_menu, R.id.btn_clear_terminal,
        R.id.btn_stt, R.id.btn_settings,
        R.id.btn_extra_keys, R.id.btn_new_session, R.id.btn_more_menu
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // configChanges includes orientation, so the activity is not recreated on rotation
        if (mIsInvalidState) return;
        applyLayoutMode(newConfig.orientation);
    }

    private void applyLayoutMode(int orientation) {
        boolean landscape = orientation == Configuration.ORIENTATION_LANDSCAPE;
        String layout = NewTermuxSettings.getLayout(this, landscape);
        boolean dual = NewTermuxSettings.LAYOUT_DUAL_SCREEN.equals(layout) && DeckBridge.hasSecondaryDisplay(this);
        // Dual screen: only the terminal here, the controls on the second display. When the deck
        // is closed or can't start, the status line takes over so the controls stay reachable.
        // While Settings is open on the second screen the deck stays away (it would cover it) but
        // this screen keeps the terminal-only layout.
        boolean secondScreenBusy = dual && DeckBridge.isSecondScreenBusy();
        boolean deck = dual && !DeckBridge.getUserHidden() && (secondScreenBusy || DeckBridge.showDeck(this));
        if (!deck || secondScreenBusy) DeckBridge.hideDeck();
        setStatusLineMode(dual || NewTermuxSettings.LAYOUT_STATUS_LINE.equals(layout));
        View statusLine = findViewById(R.id.newtermux_status_line);
        if (statusLine != null) statusLine.setVisibility(mStatusLineMode && !deck ? View.VISIBLE : View.GONE);
        // Dual screen with the deck closed: offer a button to put it back on the second screen
        setVisible(R.id.status_btn_deck, dual && !deck);
        setSystemBarsHidden(deck);
        updateOverlayOffset();
    }

    private void setSystemBarsHidden(boolean hidden) {
        if (hidden == mSystemBarsHidden) return;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (hidden) {
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }
        mSystemBarsHidden = hidden;
    }

    /** Keep the update pill just below the header, or at the top when the header is hidden. */
    private void updateOverlayOffset() {
        View header = findViewById(R.id.newtermux_toolbar_container);
        if (header == null) return;
        header.post(() -> UpdateUi.setTopOffset(header.getVisibility() == View.VISIBLE ? header.getHeight() : 0));
    }

    /** In dual screen with the deck up, Settings opens on the second screen so the terminal stays readable here. */
    private void openSettings() {
        if (DeckBridge.isDeckShowing() && DeckBridge.openOnSecondScreen(this, new Intent(this, SettingsActivity.class))) return;
        ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
    }

    /** The deck on the second display closed (user or system), or the second screen is free again: re-apply the layout. */
    public void onDeckClosed() {
        runOnUiThread(() -> {
            if (!mIsInvalidState && !isFinishing()) applyLayoutMode(getResources().getConfiguration().orientation);
        });
    }

    // Used by the dual-screen deck (com.newtermux.deck.ControlDeckPresentation)

    /** Runs a toolbar button's action even while the button itself is hidden. */
    public void performToolbarAction(int viewId) {
        View v = findViewById(viewId);
        if (v != null) v.performClick();
    }

    public boolean isToolbarButtonShown(int viewId) {
        View v = findViewById(viewId);
        return v != null && v.getVisibility() == View.VISIBLE;
    }

    public void switchToSession(TerminalSession session) {
        if (mTermuxTerminalSessionActivityClient == null || session == null) return;
        mTermuxTerminalSessionActivityClient.setCurrentSession(session);
        updateSessionTabs();
    }

    /** Same as Rename in the session tab menu. */
    public void renameSession(TerminalSession session) {
        if (mTermuxTerminalSessionActivityClient != null && session != null)
            mTermuxTerminalSessionActivityClient.renameSession(session);
    }

    /** Same as Close in the session tab menu. */
    public void closeSession(TerminalSession session) {
        if (session == null) return;
        confirmCloseTerminalSession(session);
    }

    /** Labels of the left drawer's buttons, in drawer order. */
    public List<String> getDrawerCommandLabels() {
        List<String> labels = new ArrayList<>();
        for (View b : drawerButtons()) labels.add(((TextView) b).getText().toString());
        return labels;
    }

    public void clickDrawerCommand(int index) {
        List<View> buttons = drawerButtons();
        if (index >= 0 && index < buttons.size()) buttons.get(index).performClick();
    }

    public void longClickDrawerCommand(int index) {
        List<View> buttons = drawerButtons();
        if (index >= 0 && index < buttons.size()) buttons.get(index).performLongClick();
    }

    private List<View> drawerButtons() {
        List<View> out = new ArrayList<>();
        ViewGroup container = findViewById(R.id.drawer_cmd_container);
        if (container == null) return out;
        for (int i = 0; i < container.getChildCount(); i++) {
            View c = container.getChildAt(i);
            if (c instanceof android.widget.Button && c.getVisibility() == View.VISIBLE) out.add(c);
        }
        return out;
    }

    /**
     * Switch between the standard header (toolbar + session previews on top) and the status line
     * at the bottom. The toolbar buttons are moved, not copied, so their click handlers and state
     * colors (root, speech, AC) keep working in both places.
     */
    private void setStatusLineMode(boolean on) {
        if (on == mStatusLineMode) return;
        View header = findViewById(R.id.newtermux_toolbar_container);
        View statusLine = findViewById(R.id.newtermux_status_line);
        ViewGroup toolbarRow = findViewById(R.id.newtermux_toolbar_row);
        ViewGroup statusButtons = findViewById(R.id.status_buttons);
        if (header == null || statusLine == null || toolbarRow == null || statusButtons == null) return;

        float density = getResources().getDisplayMetrics().density;
        for (int id : TOOLBAR_BUTTON_IDS) {
            View button = findViewById(id);
            if (button == null || !(button.getParent() instanceof ViewGroup)) continue;
            ViewGroup.LayoutParams current = button.getLayoutParams();
            ((ViewGroup) button.getParent()).removeView(button);
            if (on) {
                mToolbarButtonParams.put(id, current);
                boolean icon = button instanceof ImageButton;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        icon ? (int) (40 * density) : current.width,
                        (int) ((icon ? 40 : 30) * density));
                if (current instanceof ViewGroup.MarginLayoutParams)
                    lp.setMarginEnd(((ViewGroup.MarginLayoutParams) current).getMarginEnd());
                statusButtons.addView(button, lp);
            } else {
                ViewGroup.LayoutParams original = mToolbarButtonParams.get(id);
                toolbarRow.addView(button, original != null ? original : current);
            }
        }

        header.setVisibility(on ? View.GONE : View.VISIBLE);
        statusLine.setVisibility(on ? View.VISIBLE : View.GONE);
        mStatusLineMode = on;
        updateSessionTabs();
    }

    private void showSessionPopupMenu(View anchor, TerminalSession session) {
        // Bannerlator-style pop-out menu: rounded + outlined card with a thin gray divider
        // between options (see NtPopupMenu).
        com.newtermux.features.NtPopupMenu.showAsDropDown(this, anchor, null,
            new String[]{"Rename", "Close"}, idx -> {
                if (idx == 0) {
                    if (mTermuxTerminalSessionActivityClient != null)
                        mTermuxTerminalSessionActivityClient.renameSession(session);
                } else {
                    confirmCloseTerminalSession(session);
                }
            });
    }

    private void confirmCloseTerminalSession(TerminalSession session) {
        if (session == null) return;
        if (session.isRunning()) {
            new AlertDialog.Builder(this)
                .setTitle("Close Session")
                .setMessage("A process is still running in this session. Are you sure you want to terminate it?")
                .setPositiveButton("Close", (dialog, which) -> {
                    session.finishIfRunning();
                    if (mTermuxTerminalSessionActivityClient != null) {
                        mTermuxTerminalSessionActivityClient.removeFinishedSession(session);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        } else {
            session.finishIfRunning();
            if (mTermuxTerminalSessionActivityClient != null) {
                mTermuxTerminalSessionActivityClient.removeFinishedSession(session);
            }
        }
    }

    private void showToolbarMoreMenu(View anchor) {
        String[] items = new String[]{
            "Upload / Paste Image",
            "Clear Terminal",
            "Package Manager",
            "Speech to Text",
            "Toggle Autocorrect",
            "Tips & Shortcuts",
            "Settings"
        };
        com.newtermux.features.NtPopupMenu.showAsDropDown(this, anchor, null, items, idx -> {
            switch (idx) {
                case 0: { // Upload / Paste Image
                    promptOrUploadImageToCli(anchor);
                    break;
                }
                case 1: { // Clear Terminal
                    TerminalSession session = getCurrentSession();
                    if (session != null) {
                        session.write("clear\n");
                    }
                    break;
                }
                case 2: { // Package Manager
                    if (mPackageManagerMenu != null) {
                        mPackageManagerMenu.show(anchor);
                    }
                    break;
                }
                case 3: { // Speech to Text
                    onSTTButtonClicked();
                    break;
                }
                case 4: { // Toggle Autocorrect
                    if (mTerminalView != null) {
                        boolean nowEnabled = !mTerminalView.isKeyboardSuggestionsEnabled();
                        mTerminalView.setKeyboardSuggestionsEnabled(nowEnabled);
                        if (mAutoCorrectHandler != null) mAutoCorrectHandler.setEnabled(nowEnabled);
                        NewTermuxSettings.setKeyboardSuggestions(this, nowEnabled);
                        TextView btnAC = findViewById(R.id.btn_autocorrect_toggle);
                        if (btnAC != null) {
                            int color = getResources().getColor(
                                nowEnabled ? R.color.nt_primary : R.color.nt_on_surface, getTheme());
                            btnAC.setTextColor(color);
                        }
                        showToast("Autocorrect: " + (nowEnabled ? "ON" : "OFF"), false);
                    }
                    break;
                }
                case 5: { // Tips & Shortcuts
                    showTipsAndShortcutsDialog();
                    break;
                }
                case 6: { // Settings
                    openSettings();
                    break;
                }
            }
        });
    }

    public Uri getClipboardImageUri() {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return null;
            android.content.ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) {
                    String mime = getContentResolver().getType(uri);
                    if (mime != null && mime.startsWith("image/")) {
                        return uri;
                    }
                    if (clip.getDescription() != null && clip.getDescription().hasMimeType("image/*")) {
                        return uri;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public boolean pasteClipboardImageIfPresent() {
        Uri imgUri = getClipboardImageUri();
        if (imgUri == null) return false;
        saveAndInsertImageUriToCli(imgUri, "clipboard");
        return true;
    }

    public void promptOrUploadImageToCli(View anchor) {
        Uri clipUri = getClipboardImageUri();
        if (clipUri != null && anchor != null) {
            com.newtermux.features.NtPopupMenu.showAsDropDown(
                this,
                anchor,
                "Insert Image to CLI",
                new String[]{"Paste Image from Clipboard", "Pick Image from Phone"},
                idx -> {
                    if (idx == 0) {
                        saveAndInsertImageUriToCli(clipUri, "clipboard");
                    } else if (idx == 1 && mImageUploadLauncher != null) {
                        mImageUploadLauncher.launch("image/*");
                    }
                }
            );
        } else if (mImageUploadLauncher != null) {
            mImageUploadLauncher.launch("image/*");
        }
    }

    private void saveAndInsertImageUriToCli(Uri uri, String prefix) {
        new Thread(() -> {
            try {
                String mime = getContentResolver().getType(uri);
                String ext = "png";
                if (mime != null) {
                    if (mime.contains("jpeg") || mime.contains("jpg")) ext = "jpg";
                    else if (mime.contains("webp")) ext = "webp";
                    else if (mime.contains("gif")) ext = "gif";
                }
                File dir = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".cache/clipboard");
                if (!dir.exists()) dir.mkdirs();
                File outFile = new File(dir, prefix + "_" + System.currentTimeMillis() + "." + ext);
                try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                     java.io.FileOutputStream out = new java.io.FileOutputStream(outFile)) {
                    if (in == null) throw new java.io.IOException("Cannot open image stream");
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                }
                final String insertedPath = outFile.getAbsolutePath() + " ";
                runOnUiThread(() -> {
                    TerminalSession session = getCurrentSession();
                    if (mIsBrowserTabActive || mActiveFileTabIndex >= 0) {
                        switchToTerminalTab(session);
                    }
                    if (session != null && session.getEmulator() != null) {
                        session.getEmulator().paste(insertedPath);
                        showToast("Inserted image: " + outFile.getName(), false);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Image paste failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showTipsAndShortcutsDialog() {
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        int pad = Math.round(18 * getResources().getDisplayMetrics().density);
        scrollView.setPadding(pad, pad, pad, pad);

        TextView tv = new TextView(this);
        tv.setTextSize(14f);
        tv.setTextColor(0xFFE6EDF3);
        tv.setLineSpacing(0, 1.25f);

        String html = "<h3><b>⚡ BeTermux Tips &amp; Shortcuts</b></h3>"
            + "<p><b>🌐 Clickable Links &amp; Paths</b><br/>"
            + "Tap any URL or file path directly in the terminal to view it in the Dev Browser or File Viewer. Multi-line wrapped links are supported.</p>"
            + "<p><b>🚀 Dev Browser &amp; DevTools</b><br/>"
            + "Live localhost servers appear automatically in the top bar and browser port bar. Tap the <b>⋮</b> browser menu to zoom in/out, view history, or configure settings.</p>"
            + "<p><b>🖼️ Paste / Upload Images to CLI</b><br/>"
            + "Copy any screenshot to your clipboard and paste directly into the terminal, or use <b>Upload / Paste Image</b> in the <b>+</b> or <b>⋮</b> menu.</p>"
            + "<p><b>📁 File Manager &amp; Search</b><br/>"
            + "Tap <b>Files</b> in the toolbar. Toggle hidden files with the eye icon, or use the search bar to filter files instantly.</p>"
            + "<p><b>🛡️ Tab Safety &amp; Management</b><br/>"
            + "Tap <b>+</b> to create terminal or browser tabs. Long-press any tab to rename or close it.</p>"
            + "<p><b>⌨️ Extra Keys &amp; Menu</b><br/>"
            + "Toggle the keyboard shortcuts bar anytime. Tap <b>⋮ More</b> for Clear Terminal, Package Manager, Voice Input, and Settings.</p>"
            + "<hr/><p><small style='color:#8B949E;'>BeTermux v1.0.0 • Forked by Kurixutian • Based on Termux Open Source Project</small></p>";

        tv.setText(androidx.core.text.HtmlCompat.fromHtml(html, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT));
        scrollView.addView(tv);

        new AlertDialog.Builder(this)
            .setView(scrollView)
            .setPositiveButton("Got It", null)
            .show();
    }

    /**
     * Notify the tab for a specific session to update status dot (called from onTextChanged).
     * Updates in-place without rebuilding the whole row.
     */
    public void notifyPipUpdate(TerminalSession session) {
        markSessionActive(session);
        DeckBridge.notifyPipUpdate(session);
        if (mSessionPipContainer == null) return;
        runOnUiThread(() -> {
            for (int i = 0; i < mSessionPipContainer.getChildCount(); i++) {
                android.view.View child = mSessionPipContainer.getChildAt(i);
                Object tag = child.getTag();
                if (tag instanceof TabViewHolder) {
                    TabViewHolder holder = (TabViewHolder) tag;
                    if (holder.session == session && holder.dotView != null) {
                        int newStatus = getTerminalSessionStatus(session);
                        if (holder.currentStatus != newStatus) {
                            holder.currentStatus = newStatus;
                            applyStatusDotColor(holder.dotView, newStatus);
                        }
                        return;
                    }
                }
            }
        });
    }

    public void onSTTButtonClicked() {
        if (mSpeechInputManager == null) return;
        if (mSpeechInputManager.isListening()) {
            mSpeechInputManager.stopListening();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        } else {
            mSpeechInputManager.startListening();
        }
    }

    public void checkForAutocorrect(String text) {
        if (mAutoCorrectHandler == null) return;
        if (text == null || text.trim().isEmpty()) {
            hideAutocorrectBar();
            return;
        }
        String corrected = mAutoCorrectHandler.getCommandCorrection(text);
        if (corrected != null && !corrected.equals(text)) {
            mPendingCorrection = corrected;
            mPendingOriginal = text;
            runOnUiThread(() -> {
                if (mAutocorrectBar != null && mAutocorrectText != null) {
                    mAutocorrectText.setText("Did you mean: " + corrected + "?");
                    mAutocorrectBar.setVisibility(View.VISIBLE);
                }
            });
        } else {
            // If it's a small word or we are done typing, maybe keep it.
            // For now, only hide if no correction found
            if (text.length() > 3) hideAutocorrectBar();
        }
    }

    private void applyAutocorrect() {
        if (mPendingCorrection == null || mPendingOriginal == null) return;
        TerminalSession session = getCurrentSession();
        if (session != null) {
            // Send backspaces to delete the original word and the trailing space
            StringBuilder backspaces = new StringBuilder();
            for (int i = 0; i < mPendingOriginal.length() + 1; i++) {
                backspaces.append('\177'); // DEL character for backspace
            }
            byte[] bsBytes = backspaces.toString().getBytes();
            session.write(bsBytes, 0, bsBytes.length);

            // Send the corrected command
            byte[] bytes = mPendingCorrection.getBytes();
            session.write(bytes, 0, bytes.length);
        }
        hideAutocorrectBar();
    }

    private void hideAutocorrectBar() {
        mPendingCorrection = null;
        runOnUiThread(() -> {
            if (mAutocorrectBar != null) mAutocorrectBar.setVisibility(View.GONE);
        });
    }

    private void onRootToggleClicked() {
        if (mRootToggleManager == null) return;
        boolean enable = !mRootToggleManager.isRootEnabled();
        mRootToggleManager.toggleRoot(enable, new RootToggleManager.RootCallback() {
            @Override public void onRootGranted() {
                runOnUiThread(() -> {
                    updateRootButtonState(true);
                    showToast("Root access enabled", false);
                });
            }
            @Override public void onRootDenied(String reason) {
                runOnUiThread(() -> {
                    updateRootButtonState(false);
                    showToast(reason, true);
                });
            }
            @Override public void onRootStateChanged(boolean isRoot) {
                runOnUiThread(() -> {
                    updateRootButtonState(isRoot);
                    showToast(isRoot ? "Root enabled" : "Root disabled", false);
                });
            }
        });
    }

    private void updateSTTButtonState(boolean isListening) {
        if (mBtnSTT == null) return;
        mBtnSTT.setImageResource(isListening ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        int color = getResources().getColor(
            isListening ? R.color.nt_stt_listening : R.color.nt_stt_idle, getTheme());
        mBtnSTT.setColorFilter(color);
    }

    private void updateRootButtonState(boolean isRoot) {
        if (mBtnRootToggle == null) return;
        int color = getResources().getColor(
            isRoot ? R.color.nt_root_active : R.color.nt_root_inactive, getTheme());
        mBtnRootToggle.setColorFilter(color);
    }

    private void insertSpeechTextToTerminal(String text) {
        if (text == null || text.isEmpty()) return;
        // Check autocorrect for known command typos
        String corrected = mAutoCorrectHandler != null
            ? mAutoCorrectHandler.getCommandCorrection(text) : null;
        String toInsert = corrected != null ? corrected : text;
        // Write to the active terminal session
        TerminalSession session = getCurrentSession();
        if (session != null) {
            byte[] bytes = toInsert.getBytes();
            session.write(bytes, 0, bytes.length);
        }
    }

    // -----------------------------------------------------------------------------------------

    private static final String DRAWER_PREFS = "newtermux_drawer_buttons";
    private static final String[] DRAWER_BTN_DEFAULT_NAMES = {"Gemini Yolo", "Claude", "", "", ""};
    private static final String[] DRAWER_BTN_DEFAULT_CMDS  = {
        "gemini --yolo",
        "claude --dangerously-skip-permissions",
        "", "", ""
    };

    private void setupDrawerCommandButtons() {
        DeckBridge.notifyChanged(); // the deck recomposes after this rebuild (same frame)
        SharedPreferences prefs = getSharedPreferences(DRAWER_PREFS, MODE_PRIVATE);
        int count = prefs.getInt("btn_count", DRAWER_BTN_DEFAULT_NAMES.length);

        LinearLayout container = findViewById(R.id.drawer_cmd_container);
        if (container == null) return;
        container.removeAllViews();

        int accentColor = NewTermuxTheme.getAccentColor(this);
        android.content.res.ColorStateList accentCsl =
            android.content.res.ColorStateList.valueOf(accentColor);

        int marginBtm = Math.round(6 * getResources().getDisplayMetrics().density);

        // --- Utility buttons (always at top, unless toggled off in Settings) ---
        boolean showExportScript = NewTermuxSettings.isShowDrawerExportScript(this);
        boolean showPkgUpdate    = NewTermuxSettings.isShowDrawerPkgUpdate(this);
        boolean anyUtility = showExportScript || showPkgUpdate;

        if (showExportScript) {
            MaterialButton exportBtn = new MaterialButton(this,
                null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = marginBtm;
            exportBtn.setLayoutParams(lp);
            exportBtn.setStrokeColor(accentCsl);
            exportBtn.setTextColor(accentColor);
            exportBtn.setText("Export Screen");
            exportBtn.setOnClickListener(v -> {
                getDrawer().closeDrawers();
                mScreenExportSaver.launch("screen.txt");
            });
            container.addView(exportBtn);

            MaterialButton scriptBtn = new MaterialButton(this,
                null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp2.bottomMargin = marginBtm;
            scriptBtn.setLayoutParams(lp2);
            scriptBtn.setStrokeColor(accentCsl);
            scriptBtn.setTextColor(accentColor);
            scriptBtn.setText("Make Script");
            scriptBtn.setOnClickListener(v -> {
                getDrawer().closeDrawers();
                showMakeScriptDialog();
            });
            container.addView(scriptBtn);
        }

        if (showPkgUpdate) {
            MaterialButton pkgBtn = new MaterialButton(this,
                null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = marginBtm;
            pkgBtn.setLayoutParams(lp);
            pkgBtn.setStrokeColor(accentCsl);
            pkgBtn.setTextColor(accentColor);
            pkgBtn.setText("Pkg Update");
            pkgBtn.setOnClickListener(v -> {
                getDrawer().closeDrawers();
                TerminalSession s = getCurrentSession();
                if (s != null) {
                    byte[] cmdBytes = "pkg update -y\n".getBytes();
                    s.write(cmdBytes, 0, cmdBytes.length);
                }
            });
            container.addView(pkgBtn);
        }

        // Tips & Shortcuts button in drawer
        MaterialButton tipsBtn = new MaterialButton(this,
            null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        LinearLayout.LayoutParams lpTips = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpTips.bottomMargin = marginBtm;
        tipsBtn.setLayoutParams(lpTips);
        tipsBtn.setStrokeColor(accentCsl);
        tipsBtn.setTextColor(accentColor);
        tipsBtn.setText("Tips & Shortcuts");
        tipsBtn.setOnClickListener(v -> {
            getDrawer().closeDrawers();
            showTipsAndShortcutsDialog();
        });
        container.addView(tipsBtn);
        anyUtility = true;

        // Divider between utility and custom buttons
        if (anyUtility) {
            android.view.View divider = new android.view.View(this);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.round(1 * getResources().getDisplayMetrics().density));
            dlp.topMargin = Math.round(2 * getResources().getDisplayMetrics().density);
            dlp.bottomMargin = marginBtm;
            divider.setLayoutParams(dlp);
            divider.setBackgroundColor((accentColor & 0x00FFFFFF) | 0x55000000); // 33% alpha accent
            container.addView(divider);
        }

        // --- Custom command buttons ---
        if (!NewTermuxSettings.isShowDrawerCmdButtons(this)) return;
        for (int i = 0; i < count; i++) {
            final int idx = i;
            String defName = idx < DRAWER_BTN_DEFAULT_NAMES.length ? DRAWER_BTN_DEFAULT_NAMES[idx] : "";
            String defCmd  = idx < DRAWER_BTN_DEFAULT_CMDS.length  ? DRAWER_BTN_DEFAULT_CMDS[idx]  : "";
            String name = prefs.getString("btn_" + (i + 1) + "_name", defName);
            String cmd  = prefs.getString("btn_" + (i + 1) + "_cmd",  defCmd);

            MaterialButton btn = new MaterialButton(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = marginBtm;
            btn.setLayoutParams(lp);
            btn.setBackgroundTintList(accentCsl);

            boolean isUnset = name.isEmpty() && cmd.isEmpty();
            btn.setText(isUnset ? "long press to set" : (name.isEmpty() ? "Button " + (i + 1) : name));
            if (isUnset) btn.setAlpha(0.5f);

            btn.setOnClickListener(v -> {
                String c = prefs.getString("btn_" + (idx + 1) + "_cmd",
                    idx < DRAWER_BTN_DEFAULT_CMDS.length ? DRAWER_BTN_DEFAULT_CMDS[idx] : "");
                String n = prefs.getString("btn_" + (idx + 1) + "_name",
                    idx < DRAWER_BTN_DEFAULT_NAMES.length ? DRAWER_BTN_DEFAULT_NAMES[idx] : "");
                String label = n.isEmpty() ? "Button " + (idx + 1) : n;
                if (mTermuxTerminalSessionActivityClient != null) {
                    mTermuxTerminalSessionActivityClient.addNewSession(false, label);
                    if (!c.isEmpty()) {
                        final String finalCmd = c + "\n";
                        mTerminalView.post(() -> {
                            TerminalSession s = getCurrentSession();
                            if (s != null) {
                                byte[] finalBytes = finalCmd.getBytes();
                                s.write(finalBytes, 0, finalBytes.length);
                            }
                        });
                    }
                }
            });
            btn.setOnLongClickListener(v -> {
                showEditDrawerButtonDialog(idx);
                return true;
            });
            container.addView(btn);
        }

        // +/- controls — apply accent stroke/text color
        MaterialButton addBtn    = findViewById(R.id.drawer_btn_add);
        MaterialButton removeBtn = findViewById(R.id.drawer_btn_remove);
        if (addBtn != null) {
            addBtn.setStrokeColor(accentCsl);
            addBtn.setTextColor(accentColor);
            addBtn.setOnClickListener(v -> {
                int cur = prefs.getInt("btn_count", DRAWER_BTN_DEFAULT_NAMES.length);
                if (cur < 10) {
                    prefs.edit().putInt("btn_count", cur + 1).apply();
                    setupDrawerCommandButtons();
                }
            });
        }
        if (removeBtn != null) {
            removeBtn.setStrokeColor(accentCsl);
            removeBtn.setTextColor(accentColor);
            removeBtn.setOnClickListener(v -> {
                int cur = prefs.getInt("btn_count", DRAWER_BTN_DEFAULT_NAMES.length);
                if (cur > 1) {
                    // Clear saved values for the slot being removed so it starts fresh if re-added
                    prefs.edit()
                        .remove("btn_" + cur + "_name")
                        .remove("btn_" + cur + "_cmd")
                        .putInt("btn_count", cur - 1)
                        .apply();
                    setupDrawerCommandButtons();
                }
            });
        }
    }

    private void showEditDrawerButtonDialog(int idx) {
        SharedPreferences prefs = getSharedPreferences(DRAWER_PREFS, MODE_PRIVATE);
        String defName = idx < DRAWER_BTN_DEFAULT_NAMES.length ? DRAWER_BTN_DEFAULT_NAMES[idx] : "";
        String defCmd  = idx < DRAWER_BTN_DEFAULT_CMDS.length  ? DRAWER_BTN_DEFAULT_CMDS[idx]  : "";
        String currentName = prefs.getString("btn_" + (idx + 1) + "_name", defName);
        String currentCmd  = prefs.getString("btn_" + (idx + 1) + "_cmd",  defCmd);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);

        EditText nameField = new EditText(this);
        nameField.setHint("Button label");
        nameField.setText(currentName);
        layout.addView(nameField);

        EditText cmdField = new EditText(this);
        cmdField.setHint("Command");
        cmdField.setText(currentCmd);
        layout.addView(cmdField);

        new AlertDialog.Builder(this)
            .setTitle("Edit Button " + (idx + 1))
            .setView(layout)
            .setPositiveButton("Save", (d, w) -> {
                String newName = nameField.getText().toString().trim();
                String newCmd  = cmdField.getText().toString().trim();
                prefs.edit()
                    .putString("btn_" + (idx + 1) + "_name", newName)
                    .putString("btn_" + (idx + 1) + "_cmd",  newCmd)
                    .apply();
                setupDrawerCommandButtons();
            })
            .setNeutralButton("Reset", (d, w) -> {
                prefs.edit()
                    .remove("btn_" + (idx + 1) + "_name")
                    .remove("btn_" + (idx + 1) + "_cmd")
                    .apply();
                setupDrawerCommandButtons();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }





    private void showMakeScriptDialog() {
        new Thread(() -> {
            // Try zsh_history first, fall back to bash_history
            File histFile = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".zsh_history");
            if (!histFile.exists()) histFile = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".bash_history");
            if (!histFile.exists()) {
                runOnUiThread(() -> Toast.makeText(this, "No history file found", Toast.LENGTH_SHORT).show());
                return;
            }

            // Parse history: deduplicate, newest-first, cap at 100
            LinkedHashMap<String, String> seen = new LinkedHashMap<>();
            try (BufferedReader br = new BufferedReader(new FileReader(histFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    // Strip zsh extended format: ": timestamp:elapsed;command"
                    if (line.startsWith(": ") && line.contains(";")) {
                        line = line.substring(line.indexOf(';') + 1);
                    }
                    line = line.trim();
                    if (!line.isEmpty()) seen.put(line, line);
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Failed to read history", Toast.LENGTH_SHORT).show());
                return;
            }

            // Reverse so newest entries appear first, cap at 100
            List<String> entries = new ArrayList<>(seen.values());
            java.util.Collections.reverse(entries);
            if (entries.size() > 100) entries = entries.subList(0, 100);

            final List<String> finalEntries = entries;
            final boolean[] checked = new boolean[finalEntries.size()];

            runOnUiThread(() -> {
                CharSequence[] items = finalEntries.toArray(new CharSequence[0]);
                new AlertDialog.Builder(this)
                    .setTitle("Make Script — select commands")
                    .setMultiChoiceItems(items, checked, (d, which, isChecked) -> checked[which] = isChecked)
                    .setPositiveButton("Save Script", (d, w) -> {
                        // Collect in chronological order (reverse of display order)
                        List<String> selected = new ArrayList<>();
                        for (int i = checked.length - 1; i >= 0; i--) {
                            if (checked[i]) selected.add(finalEntries.get(i));
                        }
                        if (selected.isEmpty()) {
                            Toast.makeText(this, "No commands selected", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        mPendingScriptLines.clear();
                        mPendingScriptLines.addAll(selected);
                        mScriptSaver.launch("script.sh");
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        }).start();
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onBackPressed() {
        DrawerLayout drawer = getDrawer();
        if (drawer != null && (drawer.isDrawerOpen(Gravity.LEFT) || drawer.isDrawerOpen(Gravity.RIGHT))) {
            drawer.closeDrawers();
        } else if (mActiveFileTabIndex >= 0) {
            closeFileTab(mActiveFileTabIndex);
        } else if (mIsBrowserTabActive) {
            if (mBrowserCanGoBack && mBrowserGoBack != null) {
                mBrowserGoBack.run();
            } else {
                switchToTerminalTab(getCurrentSession());
            }
        } else {
            finishActivityIfNotFinishing();
        }
    }

    public void finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!TermuxActivity.this.isFinishing()) {
            finish();
        }
    }

    /** Show a toast and dismiss the last one if still visible. */
    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(TermuxActivity.this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);
        // Bannerlator-style outlined banner for the top-of-screen session-activity notice.
        float density = getResources().getDisplayMetrics().density;
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText(text);
        tv.setTextColor(androidx.core.content.ContextCompat.getColor(this, com.termux.R.color.nt_on_surface));
        tv.setTextSize(14f);
        int ph = (int) (20 * density), pv = (int) (12 * density);
        tv.setPadding(ph, pv, ph, pv);
        tv.setBackgroundResource(com.termux.R.drawable.bg_popup_menu);
        mLastToast.setView(tv);
        mLastToast.show();
    }



    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return;

        boolean autoFillEnabled = mTerminalView.isAutoFillEnabled();

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);
        if (!DataUtils.isNullOrEmpty(mTerminalView.getStoredSelectedText()))
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_USERNAME, Menu.NONE, R.string.action_autofill_username);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_PASSWORD, Menu.NONE, R.string.action_autofill_password);
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE, getResources().getString(R.string.action_kill_process, getCurrentSession().getPid())).setEnabled(currentSession.isRunning());
        menu.add(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on).setCheckable(true).setChecked(mPreferences.shouldKeepScreenOn());
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help);
        menu.add(Menu.NONE, CONTEXT_MENU_SETTINGS_ID, Menu.NONE, R.string.action_open_settings);
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_report_issue);
    }

    /** Hook system menu to show context menu instead. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mTerminalView.showContextMenu();
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        TerminalSession session = getCurrentSession();

        switch (item.getItemId()) {
            case CONTEXT_MENU_SELECT_URL_ID:
                mTermuxTerminalViewClient.showUrlSelection();
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID:
                mTermuxTerminalViewClient.shareSessionTranscript();
                return true;
            case CONTEXT_MENU_SHARE_SELECTED_TEXT:
                mTermuxTerminalViewClient.shareSelectedText();
                return true;
            case CONTEXT_MENU_AUTOFILL_USERNAME:
                mTerminalView.requestAutoFillUsername();
                return true;
            case CONTEXT_MENU_AUTOFILL_PASSWORD:
                mTerminalView.requestAutoFillPassword();
                return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID:
                onResetTerminalSession(session);
                return true;
            case CONTEXT_MENU_KILL_PROCESS_ID:
                showKillSessionDialog(session);
                return true;
            case CONTEXT_MENU_STYLING_ID:
                showStylingDialog();
                return true;
            case CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON:
                toggleKeepScreenOn();
                return true;
            case CONTEXT_MENU_HELP_ID:
                ActivityUtils.startActivity(this, new Intent(this, HelpActivity.class));
                return true;
            case CONTEXT_MENU_SETTINGS_ID:
                openSettings();
                return true;
            case CONTEXT_MENU_REPORT_ID:
                mTermuxTerminalViewClient.reportIssueFromTranscript();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        super.onContextMenuClosed(menu);
        // onContextMenuClosed() is triggered twice if back button is pressed to dismiss instead of tap for some reason
        mTerminalView.onContextMenuClosed(menu);
    }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;

        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            session.finishIfRunning();
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void onResetTerminalSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getResources().getString(R.string.msg_terminal_reset), true);

            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onResetTerminalSession();
        }
    }

    private void showStylingDialog() {
        Intent stylingIntent = new Intent();
        stylingIntent.setClassName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME, TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            startActivity(stylingIntent);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            // The startActivity() call is not documented to throw IllegalArgumentException.
            // However, crash reporting shows that it sometimes does, so catch it here.
            new AlertDialog.Builder(this).setMessage(getString(R.string.error_styling_not_installed))
                .setPositiveButton(R.string.action_styling_install,
                    (dialog, which) -> ActivityUtils.startActivity(this, new Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL))))
                .setNegativeButton(android.R.string.cancel, null).show();
        }
    }
    private void toggleKeepScreenOn() {
        if (mTerminalView.getKeepScreenOn()) {
            mTerminalView.setKeepScreenOn(false);
            mPreferences.setKeepScreenOn(false);
        } else {
            mTerminalView.setKeepScreenOn(true);
            mPreferences.setKeepScreenOn(true);
        }
    }



    /**
     * For processes to access primary external storage (/sdcard, /storage/emulated/0, ~/storage/shared),
     * termux needs to be granted legacy WRITE_EXTERNAL_STORAGE or MANAGE_EXTERNAL_STORAGE permissions
     * if targeting targetSdkVersion 30 (android 11) and running on sdk 30 (android 11) and higher.
     */
    public void requestStoragePermission(boolean isPermissionCallback) {
        new Thread() {
            @Override
            public void run() {
                // Do not ask for permission again
                int requestCode = isPermissionCallback ? -1 : PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;

                // If permission is granted, then also setup storage symlinks.
                if(PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                    TermuxActivity.this, requestCode, !isPermissionCallback)) {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_granted_on_request));

                    TermuxInstaller.setupStorageSymlinks(TermuxActivity.this);
                } else {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_not_granted_on_request));
                }
            }
        }.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: "  + resultCode + ", data: "  + IntentUtils.getIntentString(data));
        if (!BuildConfig.IS_DEMO && requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Logger.logVerbose(LOG_TAG, "onRequestPermissionsResult: requestCode: " + requestCode + ", permissions: "  + Arrays.toString(permissions) + ", grantResults: "  + Arrays.toString(grantResults));
        if (!BuildConfig.IS_DEMO && requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        } else if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mSpeechInputManager != null) mSpeechInputManager.startListening();
            } else {
                showToast("Microphone permission required for speech input", false);
            }
        }
    }



    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    public TermuxActivityRootView getTermuxActivityRootView() {
        return mTermuxActivityRootView;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    public ExtraKeysView getExtraKeysView() {
        // While the dual-screen deck is up, its keys hold the CTRL/ALT state the terminal reads.
        ExtraKeysView deckKeys = DeckBridge.getDeckKeys();
        return deckKeys != null ? deckKeys : mExtraKeysView;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() {
        return mTermuxTerminalExtraKeys;
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView) {
        mExtraKeysView = extraKeysView;
    }

    public DrawerLayout getDrawer() {
        return (DrawerLayout) findViewById(R.id.drawer_layout);
    }


    public ViewPager getTerminalToolbarViewPager() {
        return (ViewPager) findViewById(R.id.terminal_toolbar_view_pager);
    }

    public float getTerminalToolbarDefaultHeight() {
        return mTerminalToolbarDefaultHeight;
    }

    public boolean isTerminalViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 0;
    }

    public boolean isTerminalToolbarTextInputViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 1;
    }


    public void termuxSessionListNotifyUpdated() {
        if (mTermuxSessionListViewController != null)
            mTermuxSessionListViewController.notifyDataSetChanged();
        updateSessionTabs();
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public boolean isOnResumeAfterOnCreate() {
        return mIsOnResumeAfterOnCreate;
    }

    public boolean isActivityRecreated() {
        return mIsActivityRecreated;
    }



    public TermuxService getTermuxService() {
        return mTermuxService;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public TermuxTerminalViewClient getTermuxTerminalViewClient() {
        return mTermuxTerminalViewClient;
    }

    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionActivityClient;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        if (mTerminalView != null)
            return mTerminalView.getCurrentSession();
        else
            return null;
    }

    public TermuxAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxAppSharedProperties getProperties() {
        return mProperties;
    }




    public static void updateTermuxActivityStyling(Context context, boolean recreateActivity) {
        // Make sure that terminal styling is always applied.
        Intent stylingIntent = new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        stylingIntent.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, recreateActivity);
        context.sendBroadcast(stylingIntent);
    }

    private void registerTermuxActivityBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);

        registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter);
    }

    private void unregisterTermuxActivityBroadcastReceiver() {
        unregisterReceiver(mTermuxActivityBroadcastReceiver);
    }

    private void fixTermuxActivityBroadcastReceiverIntent(Intent intent) {
        if (intent == null) return;

        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            if (mIsVisible) {
                fixTermuxActivityBroadcastReceiverIntent(intent);

                switch (intent.getAction()) {
                    case TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH:
                        Logger.logDebug(LOG_TAG, "Received intent to notify app crash");
                        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(context, LOG_TAG);
                        return;
                    case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:
                        Logger.logDebug(LOG_TAG, "Received intent to reload styling");
                        reloadActivityStyling(intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true));
                        return;
                    case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS:
                        Logger.logDebug(LOG_TAG, "Received intent to request storage permissions");
                        requestStoragePermission(false);
                        return;
                    default:
                }
            }
        }
    }

    private void reloadActivityStyling(boolean recreateActivity) {
        if (mProperties != null) {
            reloadProperties();

            if (mExtraKeysView != null) {
                mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
                mExtraKeysView.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
            }

            // Update NightMode.APP_NIGHT_MODE
            TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        }

        setMargins();
        setTerminalToolbarHeight();

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onReloadActivityStyling();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadActivityStyling();

        // To change the activity and drawer theme, activity needs to be recreated.
        // It will destroy the activity, including all stored variables and views, and onCreate()
        // will be called again. Extra keys input text, terminal sessions and transcripts will be preserved.
        if (recreateActivity) {
            Logger.logDebug(LOG_TAG, "Recreating activity");
            TermuxActivity.this.recreate();
        }
    }



    public static void startTermuxActivity(@NonNull final Context context) {
        ActivityUtils.startActivity(context, newInstance(context));
    }

    public static Intent newInstance(@NonNull final Context context) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

}
