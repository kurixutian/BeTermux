package com.termux.app;

import android.content.Context;

import com.betermux.workspace.ProjectWorkspace;
import com.betermux.workspace.WorkspaceManager;
import com.termux.shared.termux.TermuxConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.List;

import static com.betermux.workspace.WorkspaceModelsKt.DEFAULT_WORKSPACE_ID;

/**
 * Tests for {@link SessionStatePersister} save/load/clear paths.
 * <p>
 * {@link SessionStatePersister#detectResumeCommand(int)} reads real {@code /proc/<pid>/cmdline}
 * and {@code /proc/<pid>/stat}, so its parsing is inherently integration-level (needs a live
 * {@code /proc}). The save/load/clear paths here are unit-testable without {@code /proc}.
 */
@RunWith(RobolectricTestRunner.class)
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
        Context context = RuntimeEnvironment.getApplication();
        SessionStatePersister.save(context, null);
        SessionStatePersister.save(context, java.util.Collections.emptyList());
        // best-effort: file may or may not be created; either is acceptable
        Assert.assertTrue(true);
    }

    @Test
    public void loadKeepsWorkspaceAndCwdAndTreatsMissingLegacyWorkspaceAsEmpty() throws Exception {
        DIR.mkdirs();
        try (FileWriter writer = new FileWriter(STATE_FILE)) {
            writer.write("["
                + "{\"name\":\"backend\",\"cwd\":\"/srv/backend\",\"failsafe\":true,"
                + "\"resume_command\":\"claude --continue\",\"workspace\":\"backend-workspace\"},"
                + "{\"name\":\"legacy\",\"cwd\":\"/home\",\"failsafe\":false,\"resume_command\":\"\"}"
                + "]");
        }

        List<SessionStatePersister.Snapshot> snapshots = SessionStatePersister.load();

        Assert.assertEquals(2, snapshots.size());
        Assert.assertEquals("/srv/backend", snapshots.get(0).cwd);
        Assert.assertEquals("backend-workspace", snapshots.get(0).workspace);
        Assert.assertEquals("/home", snapshots.get(1).cwd);
        Assert.assertEquals("", snapshots.get(1).workspace);
    }

    @Test
    public void reconcileAfterRestoreKeepsDistinctWorkspaceMappings() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("betermux_workspaces", Context.MODE_PRIVATE).edit().clear().commit();
        WorkspaceManager.resetForTesting(context, "/home");
        ProjectWorkspace backend = WorkspaceManager.createWorkspace("Backend", "/srv/backend");
        WorkspaceManager.associateSession("restored-backend", backend.getId());
        WorkspaceManager.associateSession("restored-default", DEFAULT_WORKSPACE_ID);

        TermuxService.reconcileAfterRestore(Arrays.asList("restored-backend", "restored-default"));

        Assert.assertEquals(backend.getId(), WorkspaceManager.workspaceForSession("restored-backend").getId());
        Assert.assertEquals(DEFAULT_WORKSPACE_ID, WorkspaceManager.workspaceForSession("restored-default").getId());
    }
}
