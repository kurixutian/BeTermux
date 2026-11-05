package com.termux.app;

import android.content.Context;

import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists the live session list (name + cwd + failsafe flag) to disk so that
 * if the com.termux process is torn down in the background (e.g. while a
 * fullscreen game is in the foreground), the next launch can recreate the same
 * set of tabs.
 *
 * IMPORTANT — this restores the VIEW, not the compute:
 *   What IS persisted (per session): shellName, workingDirectory, isFailsafe.
 *   What is NOT persisted: the PTY, scrollback, and any in-flight child
 *   processes. Those die with the PID and cannot be revived. A `claude` CLI
 *   that was running is gone — reopen the restored tab and `claude --continue`
 *   to resume conversation state from ~/.claude/.
 *
 * All I/O here is best-effort: a persistence failure must never crash the app.
 */
public final class SessionStatePersister {

    private static final String DIR_NAME  = ".termux";
    private static final String FILE_NAME = "session_state.json";

    public static final class Snapshot {
        public String  name;
        public String  cwd;
        public boolean failsafe;
        public String  resumeCommand;
    }

    private static File stateFile() {
        File dir = new File(TermuxConstants.TERMUX_FILES_DIR_PATH, DIR_NAME);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, FILE_NAME);
    }

    /** Best-effort snapshot of the current session list. Never throws. Atomic rename on success. */
    public static void save(Context ctx, List<TermuxSession> sessions) {
        if (sessions == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (TermuxSession s : sessions) {
                if (s == null) continue;
                ExecutionCommand ec = s.getExecutionCommand();
                if (ec == null) continue;
                JSONObject o = new JSONObject();
                o.put("name",     ec.shellName        == null ? "" : ec.shellName);

                int pid = s.getTerminalSession() != null ? s.getTerminalSession().getPid() : -1;
                String cwd = ec.workingDirectory == null ? "" : ec.workingDirectory;
                String resumeCmd = "";

                if (pid > 0) {
                    try {
                        File cwdLink = new File("/proc/" + pid + "/cwd");
                        if (cwdLink.exists()) {
                            cwd = cwdLink.getCanonicalPath();
                        }
                    } catch (Exception ignored) { }

                    try {
                        resumeCmd = detectResumeCommand(pid);
                    } catch (Exception ignored) { }
                }

                o.put("cwd",            cwd);
                o.put("failsafe",       ec.isFailsafe);
                o.put("resume_command", resumeCmd);
                arr.put(o);
            }
            File f   = stateFile();
            File tmp = new File(f.getParentFile(), FILE_NAME + ".tmp");
            try (FileWriter w = new FileWriter(tmp)) {
                w.write(arr.toString());
            }
            // POSIX rename = atomic on same filesystem.
            if (!tmp.renameTo(f)) {
                tmp.delete();
            }
        } catch (Exception ignored) { }
    }

    private static String detectResumeCommand(int pid) {
        int tpgid = -1;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader("/proc/" + pid + "/stat"), 256)) {
            String line = reader.readLine();
            if (line != null) {
                int closeParen = line.lastIndexOf(')');
                if (closeParen != -1 && closeParen + 2 < line.length()) {
                    String rest = line.substring(closeParen + 2).trim();
                    String[] tokens = rest.split("\\s+");
                    if (tokens.length > 5) {
                        int pgrp = Integer.parseInt(tokens[2]);
                        int fg = Integer.parseInt(tokens[5]);
                        if (fg > 0 && fg != pgrp && fg != pid) {
                            tpgid = fg;
                        }
                    }
                }
            }
        } catch (Exception ignored) { }

        if (tpgid <= 0) return "";

        try {
            byte[] bytes = Files.readAllBytes(Paths.get("/proc/" + tpgid + "/cmdline"));
            if (bytes.length == 0) return "";
            String cmdline = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).replace('\0', ' ').trim();
            String firstArg = cmdline.split("\\s+")[0];
            String binaryName = new File(firstArg).getName();

            // Check if node/python wrapper was used
            if ((binaryName.equals("node") || binaryName.equals("python") || binaryName.equals("python3") || binaryName.equals("sh") || binaryName.equals("bash"))
                    && cmdline.split("\\s+").length > 1) {
                String secondArg = cmdline.split("\\s+")[1];
                binaryName = new File(secondArg).getName();
            }

            if (binaryName.equals("agy")) {
                String convId = findAgyConversationId(tpgid);
                if (convId != null && !convId.isEmpty()) {
                    return "agy --conversation " + convId;
                }
                return "agy -c";
            } else if (binaryName.equals("opencode") || binaryName.startsWith("opencode")) {
                String[] parts = cmdline.split("\\s+");
                for (int i = 0; i < parts.length - 1; i++) {
                    if ("-s".equals(parts[i]) || "--session".equals(parts[i])) {
                        return "opencode --session " + parts[i + 1];
                    }
                }
                return "opencode -c";
            } else if (binaryName.equals("gemini")) {
                String[] parts = cmdline.split("\\s+");
                for (int i = 0; i < parts.length - 1; i++) {
                    if ("-r".equals(parts[i]) || "--resume".equals(parts[i])) {
                        return "gemini -r " + parts[i + 1];
                    }
                }
                return "gemini -r latest";
            }
        } catch (Exception ignored) { }

        return "";
    }

    private static String findAgyConversationId(int pid) {
        try {
            File fdDir = new File("/proc/" + pid + "/fd");
            File[] fds = fdDir.listFiles();
            if (fds != null) {
                for (File f : fds) {
                    try {
                        String target = f.getCanonicalPath();
                        if (target.contains("presence/") && target.endsWith(".lock")) {
                            int slash = target.lastIndexOf('/');
                            if (slash != -1) {
                                return target.substring(slash + 1, target.length() - 5);
                            }
                        } else if (target.contains("conversations/") && target.endsWith(".db")) {
                            int slash = target.lastIndexOf('/');
                            if (slash != -1) {
                                return target.substring(slash + 1, target.length() - 3);
                            }
                        }
                    } catch (Exception ignored) { }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Best-effort load of the last snapshot. Never throws; returns an empty list on any failure. */
    public static List<Snapshot> load() {
        List<Snapshot> out = new ArrayList<>();
        File f = stateFile();
        if (!f.exists()) return out;
        try {
            String json = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath())));
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Snapshot s = new Snapshot();
                s.name          = o.optString("name", "");
                s.cwd           = o.optString("cwd",  "");
                s.failsafe      = o.optBoolean("failsafe", false);
                s.resumeCommand = o.optString("resume_command", "");
                out.add(s);
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Drop the snapshot so a subsequent launch starts fresh (used on explicit stop). */
    public static void clear() {
        try {
            File f = stateFile();
            if (f.exists()) f.delete();
        } catch (Exception ignored) { }
    }

    private SessionStatePersister() { }
}
