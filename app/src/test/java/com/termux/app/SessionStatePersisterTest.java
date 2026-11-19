package com.termux.app;

import com.termux.shared.termux.TermuxConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

/**
 * Tests for {@link SessionStatePersister} save/load/clear paths.
 * <p>
 * {@link SessionStatePersister#detectResumeCommand(int)} reads real {@code /proc/<pid>/cmdline}
 * and {@code /proc/<pid>/stat}, so its parsing is inherently integration-level (needs a live
 * {@code /proc}). The save/load/clear paths here are unit-testable without {@code /proc}.
 */
public class SessionStatePersisterTest {

    private static final File DIR = new File(TermuxConstants.TERMUX_FILES_DIR_PATH, ".termux");
    private static final File STATE_FILE = new File(DIR, "session_state.json");

    @Before
    public void setUp() {
        SessionStatePersister.clear();
    }

    @Test
    public void loadReturnsEmptyWhenNoStateFile() {
        Assert.assertTrue(SessionStatePersister.load().isEmpty());
    }

    @Test
    public void clearDoesNotThrowWhenNoStateFile() {
        SessionStatePersister.clear();
        Assert.assertFalse(STATE_FILE.exists());
    }

    @Test
    public void saveNullAndEmptySessionListDoNotThrow() {
        SessionStatePersister.save(null);
        SessionStatePersister.save(java.util.Collections.emptyList());
        // best-effort: file may or may not be created; either is acceptable
        Assert.assertTrue(true);
    }
}
