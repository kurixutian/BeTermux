package com.termux.terminal;

import junit.framework.TestCase;

import java.nio.charset.StandardCharsets;

public class TerminalSessionInputGateTest extends TestCase {

    private static final class NoOpClient implements TerminalSessionClient {
        @Override public void onTextChanged(TerminalSession changedSession) {}
        @Override public void onTitleChanged(TerminalSession changedSession) {}
        @Override public void onSessionFinished(TerminalSession finishedSession) {}
        @Override public void onCopyTextToClipboard(TerminalSession session, String text) {}
        @Override public void onPasteTextFromClipboard(TerminalSession session) {}
        @Override public void onBell(TerminalSession session) {}
        @Override public void onColorsChanged(TerminalSession session) {}
        @Override public void onTerminalCursorStateChange(boolean state) {}
        @Override public void setTerminalShellPid(TerminalSession session, int pid) {}
        @Override public Integer getTerminalCursorStyle() { return 0; }
        @Override public void logError(String tag, String message) {}
        @Override public void logWarn(String tag, String message) {}
        @Override public void logInfo(String tag, String message) {}
        @Override public void logDebug(String tag, String message) {}
        @Override public void logVerbose(String tag, String message) {}
        @Override public void logStackTraceWithMessage(String tag, String message, Exception e) {}
        @Override public void logStackTrace(String tag, Exception e) {}
    }

    private TerminalSession createSessionWithPid(int pid) {
        TerminalSession session = new TerminalSession("/bin/sh", "/", new String[0], new String[0], null, new NoOpClient());
        session.mShellPid = pid;
        return session;
    }

    private static String drainQueue(TerminalSession session) {
        byte[] buf = new byte[4096];
        int read = session.mTerminalToProcessIOQueue.read(buf, false);
        if (read <= 0) return "";
        return new String(buf, 0, read, StandardCharsets.UTF_8);
    }

    public void testInputGateDropsAllWritesAndResumesWithoutQueuedBurst() {
        TerminalSession session = createSessionWithPid(4242);
        assertFalse(session.isInputGated());

        // Ungated writes reach the PTY output queue.
        session.write("hello");
        assertEquals("hello", drainQueue(session));

        // Gate enabled: write(String), write(byte[], int, int), and writeCodePoint(...) are all dropped.
        session.setInputGated(true);
        assertTrue(session.isInputGated());

        session.write("dropped-string");
        byte[] raw = "dropped-bytes".getBytes(StandardCharsets.UTF_8);
        session.write(raw, 0, raw.length);
        session.writeCodePoint(false, 'x');
        session.writeCodePoint(true, 'y');
        session.writeCodePoint(false, 0x1F600); // 4-byte UTF-8

        assertEquals("", drainQueue(session));

        // Gate disabled: no queued burst from gated period, and new writes work immediately.
        session.setInputGated(false);
        assertFalse(session.isInputGated());
        assertEquals("", drainQueue(session));

        session.write("after");
        session.writeCodePoint(false, '!');
        assertEquals("after!", drainQueue(session));
    }
}
