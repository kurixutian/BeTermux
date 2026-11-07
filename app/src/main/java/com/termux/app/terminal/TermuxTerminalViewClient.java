package com.termux.app.terminal;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.media.AudioManager;
import android.os.Environment;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.file.FileUtils;
import com.termux.shared.interact.MessageDialogUtils;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.shell.ShellUtils;
import com.termux.shared.termux.TermuxBootstrap;
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.shared.termux.extrakeys.SpecialButton;
import com.termux.shared.android.AndroidUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.models.ReportInfo;
import com.termux.app.models.UserAction;
import com.termux.app.terminal.io.KeyboardShortcut;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.data.DataUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.markdown.MarkdownUtils;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.data.TermuxUrlUtils;
import com.termux.shared.view.KeyboardUtils;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.KeyHandler;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import androidx.drawerlayout.widget.DrawerLayout;

public class TermuxTerminalViewClient extends TermuxTerminalViewClientBase {

    final TermuxActivity mActivity;

    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    /** Keeping track of the special keys acting as Ctrl and Fn for the soft keyboard and other hardware keys. */
    boolean mVirtualControlKeyDown, mVirtualFnKeyDown;

    private Runnable mShowSoftKeyboardRunnable;

    private boolean mShowSoftKeyboardIgnoreOnce;
    private boolean mShowSoftKeyboardWithDelayOnce;

    private boolean mTerminalCursorBlinkerStateAlreadySet;

    private List<KeyboardShortcut> mSessionShortcuts;

    private final StringBuilder mCurrentCommand = new StringBuilder();
    private final StringBuilder mExpansionBuffer = new StringBuilder();

    private static final String LOG_TAG = "TermuxTerminalViewClient";

    public TermuxTerminalViewClient(TermuxActivity activity, TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        this.mActivity = activity;
        this.mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;
    }

    public TermuxActivity getActivity() {
        return mActivity;
    }

    /**
     * Should be called when mActivity.onCreate() is called
     */
    public void onCreate() {
        onReloadProperties();

        mActivity.getTerminalView().setTextSize(mActivity.getPreferences().getFontSize());
        mActivity.getTerminalView().setKeepScreenOn(mActivity.getPreferences().shouldKeepScreenOn());
    }

    /**
     * Should be called when mActivity.onStart() is called
     */
    public void onStart() {
        // Set {@link TerminalView#TERMINAL_VIEW_KEY_LOGGING_ENABLED} value
        // Also required if user changed the preference from {@link TermuxSettings} activity and returns
        boolean isTerminalViewKeyLoggingEnabled = mActivity.getPreferences().isTerminalViewKeyLoggingEnabled();
        mActivity.getTerminalView().setIsTerminalViewKeyLoggingEnabled(isTerminalViewKeyLoggingEnabled);

        // Piggyback on the terminal view key logging toggle for now, should add a separate toggle in future
        mActivity.getTermuxActivityRootView().setIsRootViewLoggingEnabled(isTerminalViewKeyLoggingEnabled);
        ViewUtils.setIsViewUtilsLoggingEnabled(isTerminalViewKeyLoggingEnabled);
    }

    /**
     * Should be called when mActivity.onResume() is called
     */
    public void onResume() {
        // Show the soft keyboard if required
        setSoftKeyboardState(true, mActivity.isActivityRecreated());

        mTerminalCursorBlinkerStateAlreadySet = false;

        if (mActivity.getTerminalView().mEmulator != null) {
            // Start terminal cursor blinking if enabled
            // If emulator is already set, then start blinker now, otherwise wait for onEmulatorSet()
            // event to start it. This is needed since onEmulatorSet() may not be called after
            // TermuxActivity is started after device display timeout with double tap and not power button.
            setTerminalCursorBlinkerState(true);
            mTerminalCursorBlinkerStateAlreadySet = true;
        }
    }

    /**
     * Should be called when mActivity.onStop() is called
     */
    public void onStop() {
        // Stop terminal cursor blinking if enabled
        setTerminalCursorBlinkerState(false);
    }

    /**
     * Should be called when mActivity.reloadProperties() is called
     */
    public void onReloadProperties() {
        setSessionShortcuts();
    }

    /**
     * Should be called when mActivity.reloadActivityStyling() is called
     */
    public void onReloadActivityStyling() {
        // Show the soft keyboard if required
        setSoftKeyboardState(false, true);

        // Start terminal cursor blinking if enabled
        setTerminalCursorBlinkerState(true);
    }

    /**
     * Should be called when {@link com.termux.view.TerminalView#mEmulator} is set
     */
    @Override
    public void onEmulatorSet() {
        if (!mTerminalCursorBlinkerStateAlreadySet) {
            // Start terminal cursor blinking if enabled
            // We need to wait for the first session to be attached that's set in
            // TermuxActivity.onServiceConnected() and then the multiple calls to TerminalView.updateSize()
            // where the final one eventually sets the mEmulator when width/height is not 0. Otherwise
            // blinker will not start again if TermuxActivity is started again after exiting it with
            // double back press. Check TerminalView.setTerminalCursorBlinkerState().
            setTerminalCursorBlinkerState(true);
            mTerminalCursorBlinkerStateAlreadySet = true;
        }
    }



    @Override
    public float onScale(float scale) {
        if (scale < 0.9f || scale > 1.1f) {
            boolean increase = scale > 1.f;
            changeFontSize(increase);
            return 1.0f;
        }
        return scale;
    }



    @Override
    public void onSingleTapUp(MotionEvent e) {
        if (handleTapOnText(e)) {
            return;
        }

        TerminalEmulator term = mActivity.getCurrentSession().getEmulator();
        if (!term.isMouseTrackingActive() && !e.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (!KeyboardUtils.areDisableSoftKeyboardFlagsSet(mActivity))
                KeyboardUtils.showSoftKeyboard(mActivity, mActivity.getTerminalView());
            else
                Logger.logVerbose(LOG_TAG, "Not showing soft keyboard onSingleTapUp since its disabled");
        }
    }

    @Override
    public boolean shouldBackButtonBeMappedToEscape() {
        return mActivity.getProperties().isBackKeyTheEscapeKey();
    }

    @Override
    public boolean shouldEnforceCharBasedInput() {
        return mActivity.getProperties().isEnforcingCharBasedInput();
    }

    @Override
    public boolean shouldUseCtrlSpaceWorkaround() {
        return mActivity.getProperties().isUsingCtrlSpaceWorkaround();
    }

    @Override
    public boolean isTerminalViewSelected() {
        return mActivity.getTerminalToolbarViewPager() == null || mActivity.isTerminalViewSelected() || mActivity.getTerminalView().hasFocus();
    }



    @Override
    public void copyModeChanged(boolean copyMode) {
        // Disable drawer while copying.
        mActivity.getDrawer().setDrawerLockMode(copyMode ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED : DrawerLayout.LOCK_MODE_UNLOCKED);
    }



    @SuppressLint("RtlHardcoded")
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession currentSession) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            mCurrentCommand.setLength(0);
            mActivity.checkForAutocorrect(null);
            TermuxActivity.markSessionActive(currentSession);
        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
            if (mExpansionBuffer.length() > 0)
                mExpansionBuffer.deleteCharAt(mExpansionBuffer.length() - 1);
            if (mCurrentCommand.length() > 0) {
                mCurrentCommand.deleteCharAt(mCurrentCommand.length() - 1);
                mActivity.checkForAutocorrect(mCurrentCommand.toString());
            }
        }
        if (handleVirtualKeys(keyCode, e, true)) return true;

        if (keyCode == KeyEvent.KEYCODE_ENTER && !currentSession.isRunning()) {
            mTermuxTerminalSessionActivityClient.removeFinishedSession(currentSession);
            return true;
        } else if (!mActivity.getProperties().areHardwareKeyboardShortcutsDisabled() &&
            e.isCtrlPressed() && e.isAltPressed()) {
            // Get the unmodified code point:
            int unicodeChar = e.getUnicodeChar(0);

            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || unicodeChar == 'n'/* next */) {
                mTermuxTerminalSessionActivityClient.switchToSession(true);
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP || unicodeChar == 'p' /* previous */) {
                mTermuxTerminalSessionActivityClient.switchToSession(false);
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                mActivity.getDrawer().openDrawer(Gravity.LEFT);
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                mActivity.getDrawer().closeDrawers();
            } else if (unicodeChar == 'k'/* keyboard */) {
                onToggleSoftKeyboardRequest();
            } else if (unicodeChar == 'm'/* menu */) {
                mActivity.getTerminalView().showContextMenu();
            } else if (unicodeChar == 'r'/* rename */) {
                mTermuxTerminalSessionActivityClient.renameSession(currentSession);
            } else if (unicodeChar == 'c'/* create */) {
                mTermuxTerminalSessionActivityClient.addNewSession(false, null);
            } else if (unicodeChar == 's' /* speech */) {
                mActivity.onSTTButtonClicked();
            } else if (unicodeChar == 'u' /* urls */) {
                showUrlSelection();
            } else if (unicodeChar == 'v') {
                doPaste();
            } else if (unicodeChar == '+' || e.getUnicodeChar(KeyEvent.META_SHIFT_ON) == '+') {
                // We also check for the shifted char here since shift may be required to produce '+',
                // see https://github.com/termux/termux-api/issues/2
                changeFontSize(true);
            } else if (unicodeChar == '-') {
                changeFontSize(false);
            } else if (unicodeChar >= '1' && unicodeChar <= '9') {
                int index = unicodeChar - '1';
                mTermuxTerminalSessionActivityClient.switchToSession(index);
            }
            return true;
        }

        return false;

    }



    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        // If emulator is not set, like if bootstrap installation failed and user dismissed the error
        // dialog, then just exit the activity, otherwise they will be stuck in a broken state.
        if (keyCode == KeyEvent.KEYCODE_BACK && mActivity.getTerminalView().mEmulator == null) {
            mActivity.finishActivityIfNotFinishing();
            return true;
        }

        return handleVirtualKeys(keyCode, e, false);
    }

    /** Handle dedicated volume buttons as virtual keys if applicable. */
    private boolean handleVirtualKeys(int keyCode, KeyEvent event, boolean down) {
        InputDevice inputDevice = event.getDevice();
        if (mActivity.getProperties().areVirtualVolumeKeysDisabled()) {
            return false;
        } else if (inputDevice != null && inputDevice.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC) {
            // Do not steal dedicated buttons from a full external keyboard.
            return false;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            mVirtualControlKeyDown = down;
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            mVirtualFnKeyDown = down;
            return true;
        }
        return false;
    }



    @Override
    public boolean readControlKey() {
        return readExtraKeysSpecialButton(SpecialButton.CTRL) || mVirtualControlKeyDown;
    }

    @Override
    public boolean readAltKey() {
        return readExtraKeysSpecialButton(SpecialButton.ALT);
    }

    @Override
    public boolean readShiftKey() {
        return readExtraKeysSpecialButton(SpecialButton.SHIFT);
    }

    @Override
    public boolean readFnKey() {
        return readExtraKeysSpecialButton(SpecialButton.FN);
    }

    public boolean readExtraKeysSpecialButton(SpecialButton specialButton) {
        if (mActivity.getExtraKeysView() == null) return false;
        Boolean state = mActivity.getExtraKeysView().readSpecialButton(specialButton, true);
        if (state == null) {
            Logger.logError(LOG_TAG,"Failed to read an unregistered " + specialButton + " special button value from extra keys.");
            return false;
        }
        return state;
    }

    @Override
    public boolean onLongPress(MotionEvent event) {
        if (handleTapOnText(event)) {
            return true;
        }
        return false;
    }

    private static final Pattern LINE_COL_PATTERN = Pattern.compile("^(.+?):(\\d+)(?::(\\d+))?$");

    private boolean handleTapOnText(MotionEvent e) {
        if (mActivity == null || mActivity.getCurrentSession() == null) return false;
        TerminalSession session = mActivity.getCurrentSession();
        TerminalEmulator term = session.getEmulator();
        if (term == null || term.getScreen() == null) return false;

        try {
            int[] columnAndRow = mActivity.getTerminalView().getColumnAndRow(e, true);
            int col = columnAndRow[0];
            int row = columnAndRow[1];

            long style = term.getScreen().getStyleAt(row, col);
            boolean isUnderlined = (com.termux.terminal.TextStyle.decodeEffect(style) & com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0;

            String candidate = null;

            if (isUnderlined) {
                int maxCols = term.mColumns;
                int startRow = row;
                int startCol = col;
                while (startCol > 0) {
                    try {
                        long prevStyle = term.getScreen().getStyleAt(startRow, startCol - 1);
                        if ((com.termux.terminal.TextStyle.decodeEffect(prevStyle) & com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0) {
                            startCol--;
                        } else {
                            break;
                        }
                    } catch (Exception ignored) {
                        break;
                    }
                }
                if (startCol == 0 && startRow > -term.getScreen().getActiveTranscriptRows()) {
                    try {
                        long prevRowEndStyle = term.getScreen().getStyleAt(startRow - 1, maxCols - 1);
                        if ((com.termux.terminal.TextStyle.decodeEffect(prevRowEndStyle) & com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0) {
                            startRow--;
                            startCol = maxCols - 1;
                            while (startCol > 0) {
                                long prevStyle = term.getScreen().getStyleAt(startRow, startCol - 1);
                                if ((com.termux.terminal.TextStyle.decodeEffect(prevStyle) & com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0) {
                                    startCol--;
                                } else {
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                int endRow = row;
                int endCol = col;
                while (endCol + 1 < maxCols) {
                    try {
                        long nextStyle = term.getScreen().getStyleAt(endRow, endCol + 1);
                        if ((com.termux.terminal.TextStyle.decodeEffect(nextStyle) & com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0) {
                            endCol++;
                        } else {
                            break;
                        }
                    } catch (Exception ignored) {
                        break;
                    }
                }
                if (endCol + 1 >= maxCols && endRow + 1 < term.mRows) {
                    try {
                        long nextRowStartStyle = term.getScreen().getStyleAt(endRow + 1, 0);
                        if ((com.termux.terminal.TextStyle.decodeEffect(nextRowStartStyle) & com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0) {
                            endRow++;
                            endCol = 0;
                            while (endCol + 1 < maxCols) {
                                long nextStyle = term.getScreen().getStyleAt(endRow, endCol + 1);
                                if ((com.termux.terminal.TextStyle.decodeEffect(nextStyle) & com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0) {
                                    endCol++;
                                } else {
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                candidate = term.getScreen().getSelectedText(startCol, startRow, endCol, endRow);
                if (candidate != null) candidate = candidate.trim();
            }

            if (candidate == null || candidate.isEmpty()) {
                candidate = term.getScreen().getWordAtLocation(col, row);
                if (candidate != null) candidate = candidate.trim();
            }

            if (candidate == null || candidate.isEmpty()) {
                return false;
            }

            String clean = stripEnclosingPunctuation(candidate);
            if (clean.isEmpty()) return false;

            String matchedUrl = extractUrl(clean);
            if (matchedUrl != null) {
                showUrlChooser(matchedUrl, (int) e.getRawX(), (int) e.getRawY());
                return true;
            }

            File resolvedFile = resolveFile(clean, session);
            if (resolvedFile != null) {
                showFileChooser(resolvedFile, clean, (int) e.getRawX(), (int) e.getRawY());
                return true;
            }

            if (isUnderlined) {
                showUnderlinedTextChooser(clean, session, (int) e.getRawX(), (int) e.getRawY());
                return true;
            }
        } catch (Exception ex) {
            Logger.logError(LOG_TAG, "Error in handleTapOnText: " + ex.getMessage());
        }

        return false;
    }

    private static String stripEnclosingPunctuation(String s) {
        if (s == null) return "";
        int start = 0;
        int end = s.length();
        while (start < end) {
            char c = s.charAt(start);
            if (c == '"' || c == '\'' || c == '`' || c == '(' || c == '[' || c == '{' || c == '<' || Character.isWhitespace(c)) {
                start++;
            } else {
                break;
            }
        }
        while (end > start) {
            char c = s.charAt(end - 1);
            if (c == '"' || c == '\'' || c == '`' || c == ')' || c == ']' || c == '}' || c == '>' || c == ',' || c == ';' || Character.isWhitespace(c)) {
                end--;
            } else {
                break;
            }
        }
        return s.substring(start, end).trim();
    }

    private String extractUrl(String text) {
        if (text == null || text.isEmpty()) return null;
        if (text.startsWith("file://")) return null;
        if (text.startsWith("http://") || text.startsWith("https://") || text.startsWith("ftp://")) {
            return text;
        }
        if (text.matches("^(?:localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0):\\d+.*")) {
            return "http://" + text;
        }
        LinkedHashSet<CharSequence> urlSet = TermuxUrlUtils.extractUrls(text);
        if (!urlSet.isEmpty()) {
            return (String) urlSet.iterator().next();
        }
        return null;
    }

    private File resolveFile(String text, TerminalSession session) {
        if (text == null || text.isEmpty()) return null;
        String raw = text;
        if (raw.startsWith("file://")) {
            raw = raw.substring(7);
        }

        Matcher m = LINE_COL_PATTERN.matcher(raw);
        if (m.matches()) {
            raw = m.group(1);
        } else if (raw.contains("#L")) {
            raw = raw.substring(0, raw.indexOf("#L"));
        }

        raw = stripEnclosingPunctuation(raw);
        if (raw.isEmpty()) return null;

        String homePath = TermuxConstants.TERMUX_HOME_DIR_PATH;
        String cwd = session != null ? session.getCwd() : null;

        if (raw.equals("~")) {
            return new File(homePath);
        } else if (raw.startsWith("~/")) {
            File f = new File(homePath, raw.substring(2));
            return f;
        }

        if (raw.startsWith("/")) {
            File f = new File(raw);
            if (f.exists()) return f;
            if (raw.startsWith("/sdcard/") || raw.startsWith("/storage/") || raw.startsWith("/data/data/")) {
                return f;
            }
        }

        if (raw.startsWith("./") || raw.startsWith("../")) {
            if (cwd != null) {
                File f = new File(cwd, raw);
                if (f.exists()) return f;
            }
            File fHome = new File(homePath, raw);
            if (fHome.exists()) return fHome;
            if (cwd != null) return new File(cwd, raw);
        }

        if (cwd != null) {
            File f = new File(cwd, raw);
            if (f.exists()) return f;
        }

        File fHome = new File(homePath, raw);
        if (fHome.exists()) return fHome;

        int dotIdx = raw.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx < raw.length() - 1 && !raw.contains(" ")) {
            String ext = raw.substring(dotIdx + 1).toLowerCase();
            if (isKnownCodeOrDocExtension(ext)) {
                if (cwd != null) {
                    return new File(cwd, raw);
                }
                return new File(homePath, raw);
            }
        }

        return null;
    }

    private static boolean isKnownCodeOrDocExtension(String ext) {
        switch (ext) {
            case "js": case "ts": case "jsx": case "tsx":
            case "py": case "java": case "kt": case "rs": case "go":
            case "c": case "cpp": case "h": case "hpp":
            case "html": case "css": case "json": case "xml": case "yaml": case "yml": case "toml":
            case "sh": case "bash": case "zsh":
            case "md": case "txt": case "log": case "sql":
            case "pdf": case "png": case "jpg": case "jpeg": case "svg":
                return true;
            default:
                return false;
        }
    }

    private void showUrlChooser(String url, int x, int y) {
        String title = url.length() > 38 ? url.substring(0, 35) + "..." : url;
        String[] options = new String[]{
            "Open in Dev Browser",
            "Open in External Browser",
            "Copy Link"
        };
        com.newtermux.features.NtPopupMenu.showAtLocation(mActivity,
            mActivity.getTerminalView(), x, y, title, options, idx -> {
                if (idx == 0) {
                    mActivity.openBrowserTab(url);
                } else if (idx == 1) {
                    ShareUtils.openUrl(mActivity, url);
                } else if (idx == 2) {
                    ShareUtils.copyTextToClipboard(mActivity, url,
                        mActivity.getString(R.string.msg_select_url_copied_to_clipboard));
                }
            });
    }

    private void showFileChooser(File file, String originalText, int x, int y) {
        String title = file.getName();
        if (title.isEmpty()) title = file.getAbsolutePath();
        if (title.length() > 38) title = "..." + title.substring(title.length() - 35);

        String[] options = new String[]{
            "Open in Editor Tab",
            "Open with External App",
            "Copy File Path",
            "Share File"
        };
        com.newtermux.features.NtPopupMenu.showAtLocation(mActivity,
            mActivity.getTerminalView(), x, y, title, options, idx -> {
                if (idx == 0) {
                    mActivity.openFileTab(file);
                } else if (idx == 1) {
                    openFileWithSystem(mActivity, file);
                } else if (idx == 2) {
                    ShareUtils.copyTextToClipboard(mActivity, file.getAbsolutePath(), "Path copied to clipboard");
                } else if (idx == 3) {
                    shareFileWithSystem(mActivity, file);
                }
            });
    }

    private void showUnderlinedTextChooser(String text, TerminalSession session, int x, int y) {
        String title = text.length() > 38 ? text.substring(0, 35) + "..." : text;
        String[] options = new String[]{
            "Open in Editor Tab",
            "Search Web",
            "Copy Text"
        };
        com.newtermux.features.NtPopupMenu.showAtLocation(mActivity,
            mActivity.getTerminalView(), x, y, title, options, idx -> {
                if (idx == 0) {
                    String cwd = session != null ? session.getCwd() : TermuxConstants.TERMUX_HOME_DIR_PATH;
                    File f = new File(cwd != null ? cwd : TermuxConstants.TERMUX_HOME_DIR_PATH, text);
                    mActivity.openFileTab(f);
                } else if (idx == 1) {
                    String searchUrl = "https://www.google.com/search?q=" + Uri.encode(text);
                    mActivity.openBrowserTab(searchUrl);
                } else if (idx == 2) {
                    ShareUtils.copyTextToClipboard(mActivity, text, "Copied to clipboard");
                }
            });
    }

    private static void openFileWithSystem(Context context, File file) {
        try {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                context, context.getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent, "Open " + file.getName()));
        } catch (Exception e) {
            Toast.makeText(context, "Cannot open: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void shareFileWithSystem(Context context, File file) {
        try {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                context, context.getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent, "Share " + file.getName()));
        } catch (Exception e) {
            Toast.makeText(context, "Cannot share: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }



    @Override
    public boolean onCodePoint(final int codePoint, boolean ctrlDown, TerminalSession session) {
        if (!ctrlDown && !mVirtualControlKeyDown && !mVirtualFnKeyDown) {
            if (Character.isWhitespace(codePoint)) {
                // Text expansion: fire on Space or Tab only (not Enter/newline)
                if ((codePoint == ' ' || codePoint == '\t')
                        && mExpansionBuffer.length() > 0
                        && com.newtermux.features.NewTermuxSettings.isTextExpansionEnabled(mActivity)) {
                    String trigger = mExpansionBuffer.toString();
                    String expansion = com.newtermux.features.TextExpansionStore.findExpansion(mActivity, trigger);
                    if (expansion != null) {
                        mExpansionBuffer.setLength(0);
                        mCurrentCommand.setLength(0);
                        StringBuilder out = new StringBuilder();
                        for (int i = 0; i < trigger.length(); i++) out.append('\177'); // DEL
                        out.append(expansion).append((char) codePoint);
                        byte[] bytes = out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        session.write(bytes, 0, bytes.length);
                        return true; // consume — don't send codePoint again
                    }
                }
                mExpansionBuffer.setLength(0);
                if (mCurrentCommand.length() > 0) {
                    mActivity.checkForAutocorrect(mCurrentCommand.toString());
                }
                mCurrentCommand.setLength(0);
            } else if (codePoint >= 32) { // printable character
                mExpansionBuffer.append((char) codePoint);
                if (Character.isLetterOrDigit(codePoint) || codePoint == '-' || codePoint == '_') {
                    mCurrentCommand.append((char) codePoint);
                }
            }
        }
        if (mVirtualFnKeyDown) {
            int resultingKeyCode = -1;
            int resultingCodePoint = -1;
            boolean altDown = false;
            int lowerCase = Character.toLowerCase(codePoint);
            switch (lowerCase) {
                // Arrow keys.
                case 'w':
                    resultingKeyCode = KeyEvent.KEYCODE_DPAD_UP;
                    break;
                case 'a':
                    resultingKeyCode = KeyEvent.KEYCODE_DPAD_LEFT;
                    break;
                case 's':
                    resultingKeyCode = KeyEvent.KEYCODE_DPAD_DOWN;
                    break;
                case 'd':
                    resultingKeyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
                    break;

                // Page up and down.
                case 'p':
                    resultingKeyCode = KeyEvent.KEYCODE_PAGE_UP;
                    break;
                case 'n':
                    resultingKeyCode = KeyEvent.KEYCODE_PAGE_DOWN;
                    break;

                // Some special keys:
                case 't':
                    resultingKeyCode = KeyEvent.KEYCODE_TAB;
                    break;
                case 'i':
                    resultingKeyCode = KeyEvent.KEYCODE_INSERT;
                    break;
                case 'h':
                    resultingCodePoint = '~';
                    break;

                // Special characters to input.
                case 'u':
                    resultingCodePoint = '_';
                    break;
                case 'l':
                    resultingCodePoint = '|';
                    break;

                // Function keys.
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    resultingKeyCode = (codePoint - '1') + KeyEvent.KEYCODE_F1;
                    break;
                case '0':
                    resultingKeyCode = KeyEvent.KEYCODE_F10;
                    break;

                // Other special keys.
                case 'e':
                    resultingCodePoint = /*Escape*/ 27;
                    break;
                case '.':
                    resultingCodePoint = /*^.*/ 28;
                    break;

                case 'b': // alt+b, jumping backward in readline.
                case 'f': // alf+f, jumping forward in readline.
                case 'x': // alt+x, common in emacs.
                    resultingCodePoint = lowerCase;
                    altDown = true;
                    break;

                // Volume control.
                case 'v':
                    resultingCodePoint = -1;
                    AudioManager audio = (AudioManager) mActivity.getSystemService(Context.AUDIO_SERVICE);
                    audio.adjustSuggestedStreamVolume(AudioManager.ADJUST_SAME, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
                    break;

                // Writing mode:
                case 'q':
                case 'k':
                    mActivity.toggleTerminalToolbar();
                    mVirtualFnKeyDown=false; // force disable fn key down to restore keyboard input into terminal view, fixes termux/termux-app#1420
                    break;
            }

            if (resultingKeyCode != -1) {
                TerminalEmulator term = session.getEmulator();
                session.write(KeyHandler.getCode(resultingKeyCode, 0, term.isCursorKeysApplicationMode(), term.isKeypadApplicationMode()));
            } else if (resultingCodePoint != -1) {
                session.writeCodePoint(altDown, resultingCodePoint);
            }
            return true;
        } else if (ctrlDown) {
            if (codePoint == 106 /* Ctrl+j or \n */ && !session.isRunning()) {
                mTermuxTerminalSessionActivityClient.removeFinishedSession(session);
                return true;
            }

            List<KeyboardShortcut> shortcuts = mSessionShortcuts;
            if (shortcuts != null && !shortcuts.isEmpty()) {
                int codePointLowerCase = Character.toLowerCase(codePoint);
                for (int i = shortcuts.size() - 1; i >= 0; i--) {
                    KeyboardShortcut shortcut = shortcuts.get(i);
                    if (codePointLowerCase == shortcut.codePoint) {
                        switch (shortcut.shortcutAction) {
                            case TermuxPropertyConstants.ACTION_SHORTCUT_CREATE_SESSION:
                                mTermuxTerminalSessionActivityClient.addNewSession(false, null);
                                return true;
                            case TermuxPropertyConstants.ACTION_SHORTCUT_NEXT_SESSION:
                                mTermuxTerminalSessionActivityClient.switchToSession(true);
                                return true;
                            case TermuxPropertyConstants.ACTION_SHORTCUT_PREVIOUS_SESSION:
                                mTermuxTerminalSessionActivityClient.switchToSession(false);
                                return true;
                            case TermuxPropertyConstants.ACTION_SHORTCUT_RENAME_SESSION:
                                mTermuxTerminalSessionActivityClient.renameSession(mActivity.getCurrentSession());
                                return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Set the terminal sessions shortcuts.
     */
    private void setSessionShortcuts() {
        mSessionShortcuts = new ArrayList<>();

        // The {@link TermuxPropertyConstants#MAP_SESSION_SHORTCUTS} stores the session shortcut key and action pair
        for (Map.Entry<String, Integer> entry : TermuxPropertyConstants.MAP_SESSION_SHORTCUTS.entrySet()) {
            // The mMap stores the code points for the session shortcuts while loading properties
            Integer codePoint = (Integer) mActivity.getProperties().getInternalPropertyValue(entry.getKey(), true);
            // If codePoint is null, then session shortcut did not exist in properties or was invalid
            // as parsed by {@link #getCodePointForSessionShortcuts(String,String)}
            // If codePoint is not null, then get the action for the MAP_SESSION_SHORTCUTS key and
            // add the code point to sessionShortcuts
            if (codePoint != null)
                mSessionShortcuts.add(new KeyboardShortcut(codePoint, entry.getValue()));
        }
    }





    public void changeFontSize(boolean increase) {
        mActivity.getPreferences().changeFontSize(increase);
        mActivity.getTerminalView().setTextSize(mActivity.getPreferences().getFontSize());
    }



    /**
     * Called when user requests the soft keyboard to be toggled via "KEYBOARD" toggle button in
     * drawer or extra keys, or with ctrl+alt+k hardware keyboard shortcut.
     */
    public void onToggleSoftKeyboardRequest() {
        // If soft keyboard toggle behaviour is enable/disabled
        if (mActivity.getProperties().shouldEnableDisableSoftKeyboardOnToggle()) {
            // If soft keyboard is visible
            if (!KeyboardUtils.areDisableSoftKeyboardFlagsSet(mActivity)) {
                Logger.logVerbose(LOG_TAG, "Disabling soft keyboard on toggle");
                mActivity.getPreferences().setSoftKeyboardEnabled(false);
                KeyboardUtils.disableSoftKeyboard(mActivity, mActivity.getTerminalView());
            } else {
                // Show with a delay, otherwise pressing keyboard toggle won't show the keyboard after
                // switching back from another app if keyboard was previously disabled by user.
                // Also request focus, since it wouldn't have been requested at startup by
                // setSoftKeyboardState if keyboard was disabled. #2112
                Logger.logVerbose(LOG_TAG, "Enabling soft keyboard on toggle");
                mActivity.getPreferences().setSoftKeyboardEnabled(true);
                KeyboardUtils.clearDisableSoftKeyboardFlags(mActivity);
                if(mShowSoftKeyboardWithDelayOnce) {
                    mShowSoftKeyboardWithDelayOnce = false;
                    mActivity.getTerminalView().postDelayed(getShowSoftKeyboardRunnable(), 500);
                    mActivity.getTerminalView().requestFocus();
                } else
                    KeyboardUtils.showSoftKeyboard(mActivity, mActivity.getTerminalView());
            }
        }
        // If soft keyboard toggle behaviour is show/hide
        else {
            // If soft keyboard is disabled by user for Termux
            if (!mActivity.getPreferences().isSoftKeyboardEnabled()) {
                Logger.logVerbose(LOG_TAG, "Maintaining disabled soft keyboard on toggle");
                KeyboardUtils.disableSoftKeyboard(mActivity, mActivity.getTerminalView());
            } else {
                Logger.logVerbose(LOG_TAG, "Showing/Hiding soft keyboard on toggle");
                KeyboardUtils.clearDisableSoftKeyboardFlags(mActivity);
                KeyboardUtils.toggleSoftKeyboard(mActivity);
            }
        }
    }

    public void setSoftKeyboardState(boolean isStartup, boolean isReloadTermuxProperties) {
        boolean noShowKeyboard = false;

        // Requesting terminal view focus is necessary regardless of if soft keyboard is to be
        // disabled or hidden at startup, otherwise if hardware keyboard is attached and user
        // starts typing on hardware keyboard without tapping on the terminal first, then a colour
        // tint will be added to the terminal as highlight for the focussed view. Test with a light
        // theme. For android 8.+, the "defaultFocusHighlightEnabled" attribute is also set to false
        // in TerminalView layout to fix the issue.

        // If soft keyboard is disabled by user for Termux (check function docs for Termux behaviour info)
        if (KeyboardUtils.shouldSoftKeyboardBeDisabled(mActivity,
            mActivity.getPreferences().isSoftKeyboardEnabled(),
            mActivity.getPreferences().isSoftKeyboardEnabledOnlyIfNoHardware())) {
            Logger.logVerbose(LOG_TAG, "Maintaining disabled soft keyboard");
            KeyboardUtils.disableSoftKeyboard(mActivity, mActivity.getTerminalView());
            mActivity.getTerminalView().requestFocus();
            noShowKeyboard = true;
            // Delay is only required if onCreate() is called like when Termux app is exited with
            // double back press, not when Termux app is switched back from another app and keyboard
            // toggle is pressed to enable keyboard
            if (isStartup && mActivity.isOnResumeAfterOnCreate())
                mShowSoftKeyboardWithDelayOnce = true;
        } else {
            // Set flag to automatically push up TerminalView when keyboard is opened instead of showing over it
            KeyboardUtils.setSoftInputModeAdjustResize(mActivity);

            // Clear any previous flags to disable soft keyboard in case setting updated
            KeyboardUtils.clearDisableSoftKeyboardFlags(mActivity);

            // If soft keyboard is to be hidden on startup
            if (isStartup && mActivity.getProperties().shouldSoftKeyboardBeHiddenOnStartup()) {
                Logger.logVerbose(LOG_TAG, "Hiding soft keyboard on startup");
                // Required to keep keyboard hidden when Termux app is switched back from another app
                KeyboardUtils.setSoftKeyboardAlwaysHiddenFlags(mActivity);

                KeyboardUtils.hideSoftKeyboard(mActivity, mActivity.getTerminalView());
                mActivity.getTerminalView().requestFocus();
                noShowKeyboard = true;
                // Required to keep keyboard hidden on app startup
                mShowSoftKeyboardIgnoreOnce = true;
            }
        }

        mActivity.getTerminalView().setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                // Force show soft keyboard if TerminalView or toolbar text input view has
                // focus and close it if they don't
                boolean textInputViewHasFocus = false;
                final EditText textInputView =  mActivity.findViewById(R.id.terminal_toolbar_text_input);
                if (textInputView != null) textInputViewHasFocus = textInputView.hasFocus();

                if (hasFocus || textInputViewHasFocus) {
                    if (mShowSoftKeyboardIgnoreOnce) {
                        mShowSoftKeyboardIgnoreOnce = false; return;
                    }
                    Logger.logVerbose(LOG_TAG, "Showing soft keyboard on focus change");
                } else {
                    Logger.logVerbose(LOG_TAG, "Hiding soft keyboard on focus change");
                }

                KeyboardUtils.setSoftKeyboardVisibility(getShowSoftKeyboardRunnable(), mActivity, mActivity.getTerminalView(), hasFocus || textInputViewHasFocus);
            }
        });

        // Do not force show soft keyboard if termux-reload-settings command was run with hardware keyboard
        // or soft keyboard is to be hidden or is disabled
        if (!isReloadTermuxProperties && !noShowKeyboard) {
            // Request focus for TerminalView
            // Also show the keyboard, since onFocusChange will not be called if TerminalView already
            // had focus on startup to show the keyboard, like when opening url with context menu
            // "Select URL" long press and returning to Termux app with back button. This
            // will also show keyboard even if it was closed before opening url. #2111
            Logger.logVerbose(LOG_TAG, "Requesting TerminalView focus and showing soft keyboard");
            mActivity.getTerminalView().requestFocus();
            mActivity.getTerminalView().postDelayed(getShowSoftKeyboardRunnable(), 300);
        }
    }

    private Runnable getShowSoftKeyboardRunnable() {
        if (mShowSoftKeyboardRunnable == null) {
            mShowSoftKeyboardRunnable = () -> {
                KeyboardUtils.showSoftKeyboard(mActivity, mActivity.getTerminalView());
            };
        }
        return mShowSoftKeyboardRunnable;
    }



    public void setTerminalCursorBlinkerState(boolean start) {
        if (start) {
            // If set/update the cursor blinking rate is successful, then enable cursor blinker
            if (mActivity.getTerminalView().setTerminalCursorBlinkerRate(mActivity.getProperties().getTerminalCursorBlinkRate()))
                mActivity.getTerminalView().setTerminalCursorBlinkerState(true, true);
            else
                Logger.logError(LOG_TAG,"Failed to start cursor blinker");
        } else {
            // Disable cursor blinker
            mActivity.getTerminalView().setTerminalCursorBlinkerState(false, true);
        }
    }



    public void shareSessionTranscript() {
        TerminalSession session = mActivity.getCurrentSession();
        if (session == null) return;

        String transcriptText = ShellUtils.getTerminalSessionTranscriptText(session, false, true);
        if (transcriptText == null) return;

        // See https://github.com/termux/termux-app/issues/1166.
        transcriptText = DataUtils.getTruncatedCommandOutput(transcriptText, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES, false, true, false).trim();
        ShareUtils.shareText(mActivity, mActivity.getString(R.string.title_share_transcript),
            transcriptText, mActivity.getString(R.string.title_share_transcript_with));
    }

    public void shareSelectedText() {
        String selectedText = mActivity.getTerminalView().getStoredSelectedText();
        if (DataUtils.isNullOrEmpty(selectedText)) return;
        ShareUtils.shareText(mActivity, mActivity.getString(R.string.title_share_selected_text),
            selectedText, mActivity.getString(R.string.title_share_selected_text_with));
    }

    public void showUrlSelection() {
        TerminalSession session = mActivity.getCurrentSession();
        if (session == null) return;

        String text = ShellUtils.getTerminalSessionTranscriptText(session, true, true);

        LinkedHashSet<CharSequence> urlSet = TermuxUrlUtils.extractUrls(text);
        if (urlSet.isEmpty()) {
            new AlertDialog.Builder(mActivity).setMessage(R.string.title_select_url_none_found).show();
            return;
        }

        final CharSequence[] urls = urlSet.toArray(new CharSequence[0]);
        Collections.reverse(Arrays.asList(urls)); // Latest first.

        // Click to copy url to clipboard:
        final AlertDialog dialog = new AlertDialog.Builder(mActivity).setItems(urls, (di, which) -> {
            String url = (String) urls[which];
            ShareUtils.copyTextToClipboard(mActivity, url, mActivity.getString(R.string.msg_select_url_copied_to_clipboard));
        }).setTitle(R.string.title_select_url_dialog).create();

        // Long press to open URL:
        dialog.setOnShowListener(di -> {
            ListView lv = dialog.getListView(); // this is a ListView with your "buds" in it
            lv.setOnItemLongClickListener((parent, view, position, id) -> {
                dialog.dismiss();
                String url = (String) urls[position];
                ShareUtils.openUrl(mActivity, url);
                return true;
            });
        });

        dialog.show();
    }

    public void reportIssueFromTranscript() {
        TerminalSession session = mActivity.getCurrentSession();
        if (session == null) return;

        final String transcriptText = ShellUtils.getTerminalSessionTranscriptText(session, false, true);
        if (transcriptText == null) return;

        MessageDialogUtils.showMessage(mActivity, TermuxConstants.TERMUX_APP_NAME + " Report Issue",
            mActivity.getString(R.string.msg_add_termux_debug_info),
            mActivity.getString(com.termux.shared.R.string.action_yes), (dialog, which) -> reportIssueFromTranscript(transcriptText, true),
            mActivity.getString(com.termux.shared.R.string.action_no), (dialog, which) -> reportIssueFromTranscript(transcriptText, false),
            null);
    }

    private void reportIssueFromTranscript(String transcriptText, boolean addTermuxDebugInfo) {
        Logger.showToast(mActivity, mActivity.getString(R.string.msg_generating_report), true);

        new Thread() {
            @Override
            public void run() {
                StringBuilder reportString = new StringBuilder();

                String title = TermuxConstants.TERMUX_APP_NAME + " Report Issue";

                reportString.append("## Transcript\n");
                reportString.append("\n").append(MarkdownUtils.getMarkdownCodeForString(transcriptText, true));
                reportString.append("\n##\n");

                if (addTermuxDebugInfo) {
                    reportString.append("\n\n").append(TermuxUtils.getAppInfoMarkdownString(mActivity, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES));
                } else {
                    reportString.append("\n\n").append(TermuxUtils.getAppInfoMarkdownString(mActivity, TermuxUtils.AppInfoMode.TERMUX_PACKAGE));
                }

                reportString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(mActivity, true));

                if (TermuxBootstrap.isAppPackageManagerAPT()) {
                    String termuxAptInfo = TermuxUtils.geAPTInfoMarkdownString(mActivity);
                    if (termuxAptInfo != null)
                        reportString.append("\n\n").append(termuxAptInfo);
                }

                if (addTermuxDebugInfo) {
                    String termuxDebugInfo = TermuxUtils.getTermuxDebugMarkdownString(mActivity);
                    if (termuxDebugInfo != null)
                        reportString.append("\n\n").append(termuxDebugInfo);
                }

                String userActionName = UserAction.REPORT_ISSUE_FROM_TRANSCRIPT.getName();

                ReportInfo reportInfo = new ReportInfo(userActionName,
                    TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY_NAME, title);
                reportInfo.setReportString(reportString.toString());
                reportInfo.setReportStringSuffix("\n\n" + TermuxUtils.getReportIssueMarkdownString(mActivity));
                reportInfo.setReportSaveFileLabelAndPath(userActionName,
                    Environment.getExternalStorageDirectory() + "/" +
                        FileUtils.sanitizeFileName(TermuxConstants.TERMUX_APP_NAME + "-" + userActionName + ".log", true, true));

                ReportActivity.startReportActivity(mActivity, reportInfo);
            }
        }.start();
    }

    public void doPaste() {
        TerminalSession session = mActivity.getCurrentSession();
        if (session == null) return;
        if (!session.isRunning()) return;

        if (mActivity.pasteClipboardImageIfPresent()) return;

        String text = ShareUtils.getTextStringFromClipboardIfSet(mActivity, true);
        if (text != null)
            session.getEmulator().paste(text);
    }

}
