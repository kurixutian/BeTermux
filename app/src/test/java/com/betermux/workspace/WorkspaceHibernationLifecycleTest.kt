package com.betermux.workspace

import android.content.Context
import android.system.OsConstants
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.betermux.compose.BeTermuxComposeTheme
import com.betermux.features.BeTermuxSettings
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h915dp")
class WorkspaceHibernationLifecycleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    private data class FakeProcess(
        val pid: Int,
        val comm: String,
        var state: Char,
        val ppid: Int,
        val pgid: Int,
        val sid: Int,
        val uid: Int,
        val startTimeTicks: Long,
        var ignoresSigTerm: Boolean = false
    ) {
        fun toStatLine(): String {
            return "$pid ($comm) $state $ppid $pgid $sid 0 -1 4194304 0 0 0 0 0 0 0 0 20 0 1 0 $startTimeTicks 1024 10"
        }
    }

    private class FakeProcessOps(
        initialProcesses: List<FakeProcess> = emptyList()
    ) : ProcessOps {
        val processes = linkedMapOf<Int, FakeProcess>().apply {
            initialProcesses.forEach { put(it.pid, it) }
        }
        val recordedSignals = mutableListOf<Pair<Int, Int>>()
        var virtualClockMs: Long = 1_000L

        override fun listProcPids(): List<Int> = processes.keys.toList()

        override fun readProcStat(pid: Int): String? = processes[pid]?.toStatLine()

        override fun readProcUid(pid: Int): Int? = processes[pid]?.uid

        override fun sendSignal(pid: Int, signal: Int): SignalStatus {
            recordedSignals.add(pid to signal)
            val proc = processes[pid] ?: return SignalStatus.ESRCH
            when (signal) {
                OsConstants.SIGSTOP -> proc.state = 'T'
                OsConstants.SIGCONT -> if (proc.state == 'T') proc.state = 'S'
                OsConstants.SIGTERM -> if (!proc.ignoresSigTerm) processes.remove(pid)
                OsConstants.SIGKILL -> processes.remove(pid)
            }
            return SignalStatus.OK
        }

        override fun sleepMs(millis: Long) {
            virtualClockMs += millis
        }

        override fun nowMs(): Long = virtualClockMs
    }

    private class StubSessionClient : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {}
        override fun onTitleChanged(changedSession: TerminalSession) {}
        override fun onSessionFinished(finishedSession: TerminalSession) {}
        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {}
        override fun onPasteTextFromClipboard(session: TerminalSession?) {}
        override fun onBell(session: TerminalSession) {}
        override fun onColorsChanged(session: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
        override fun getTerminalCursorStyle(): Int = 0
        override fun logError(tag: String?, message: String?) {}
        override fun logWarn(tag: String?, message: String?) {}
        override fun logInfo(tag: String?, message: String?) {}
        override fun logDebug(tag: String?, message: String?) {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
        override fun logStackTrace(tag: String?, e: Exception?) {}
    }

    private fun newTestSession(pid: Int): TerminalSession {
        val session = TerminalSession("/bin/sh", "/", emptyArray(), emptyArray(), null, StubSessionClient())
        val field = TerminalSession::class.java.getDeclaredField("mShellPid")
        field.isAccessible = true
        field.setInt(session, pid)
        return session
    }

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("betermux_workspaces", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("betermux_hibernation_sids", Context.MODE_PRIVATE).edit().clear().commit()
        BeTermuxSettings.setWorkspacesEnabled(context, true)
        BeTermuxSettings.setWorkspaceHibernationEnabled(context, true)
        WorkspaceManager.resetForTesting(context, "/data/data/com.termux/files/home")
    }

    @Test
    fun coldStartReconciliationClearsStaleSleepStateAndCleansLeakedSidWhileReconnectPreservesSleepState() = runBlocking<Unit> {
        val ws = WorkspaceManager.createWorkspace("Backend", "/tmp/backend")
        val session = newTestSession(500)
        WorkspaceManager.associateSession(session.mHandle, ws.id)

        val ops = FakeProcessOps(
            listOf(
                FakeProcess(pid = 500, comm = "bash", state = 'S', ppid = 1, pgid = 500, sid = 500, uid = 10912, startTimeTicks = 4200L),
                FakeProcess(pid = 501, comm = "node", state = 'S', ppid = 500, pgid = 501, sid = 500, uid = 10912, startTimeTicks = 4210L),
                // Unrelated UID & unrelated SID must be spared
                FakeProcess(pid = 600, comm = "other_uid", state = 'T', ppid = 1, pgid = 600, sid = 500, uid = 9999, startTimeTicks = 4300L),
                FakeProcess(pid = 601, comm = "other_sid", state = 'T', ppid = 1, pgid = 601, sid = 601, uid = 10912, startTimeTicks = 4400L)
            )
        )
        val controller = WorkspaceHibernationController(context, ops, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        assertEquals(WorkspaceSleepState.SLEEPING, controller.sleepWorkspace(ws.id, listOf(session)))
        assertEquals('T', ops.processes[500]?.state)
        assertEquals('T', ops.processes[501]?.state)

        // Activity recreation while service stays alive preserves SLEEPING / ERROR states
        val errWs = WorkspaceManager.createWorkspace("ErrWs", "/tmp/err")
        WorkspaceManager.setSleepState(errWs.id, WorkspaceSleepState.ERROR)
        WorkspaceManager.syncLiveSessionsPreservingSleepState(listOf(session.mHandle))
        assertEquals(WorkspaceSleepState.SLEEPING, WorkspaceManager.getSleepState(ws.id))
        assertEquals(WorkspaceSleepState.ERROR, WorkspaceManager.getSleepState(errWs.id))

        // Simulated cold-start process resurrection: WorkspaceManager resets sleep states to AWAKE,
        // and launchColdStartCleanup sends D2 (CONT -> TERM -> KILL) to previously managed 'T' SID 500 only.
        WorkspaceManager.resetForTesting(context, "/data/data/com.termux/files/home")
        WorkspaceManager.reconcileStartupSessions(emptyList())
        assertEquals(WorkspaceSleepState.AWAKE, WorkspaceManager.getSleepState(ws.id))

        val cleanupJob = WorkspaceHibernationCoordinator.launchColdStartCleanup(
            context = context,
            ownerUid = 10912,
            controller = controller,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
        cleanupJob.join()

        assertFalse("Leaked shell 500 should be terminated", ops.processes.containsKey(500))
        assertFalse("Leaked child 501 should be terminated", ops.processes.containsKey(501))
        assertTrue("Unrelated UID 600 must be spared", ops.processes.containsKey(600))
        assertTrue("Unrelated SID 601 must be spared", ops.processes.containsKey(601))
        assertEquals(WorkspaceSleepState.AWAKE, WorkspaceManager.getSleepState(ws.id))
    }

    @Test
    fun closingTabInSleepingWorkspaceRoutesThroughTerminateSingleSessionAndGuardsDoubleClick() = runBlocking<Unit> {
        val ws = WorkspaceManager.createWorkspace("SleepingTab", "/tmp/sleeping-tab")
        val s1 = newTestSession(700)
        val s2 = newTestSession(701)
        WorkspaceManager.associateSession(s1.mHandle, ws.id)
        WorkspaceManager.associateSession(s2.mHandle, ws.id)

        val ops = FakeProcessOps(
            listOf(
                FakeProcess(pid = 700, comm = "bash", state = 'S', ppid = 1, pgid = 700, sid = 700, uid = 10912, startTimeTicks = 8000L, ignoresSigTerm = true),
                FakeProcess(pid = 702, comm = "python", state = 'S', ppid = 700, pgid = 702, sid = 700, uid = 10912, startTimeTicks = 8010L),
                FakeProcess(pid = 701, comm = "bash", state = 'S', ppid = 1, pgid = 701, sid = 701, uid = 10912, startTimeTicks = 8020L)
            )
        )
        val controller = WorkspaceHibernationController(context, ops, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        assertEquals(WorkspaceSleepState.SLEEPING, controller.sleepWorkspace(ws.id, listOf(s1, s2)))

        ops.recordedSignals.clear()
        val coordinator = WorkspaceHibernationCoordinator(
            context = context,
            controller = controller,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )

        var completedCount = 0
        val job = coordinator.closeSession(ws.id, s1) {
            completedCount += 1
        }
        job?.join()

        assertEquals(1, completedCount)
        assertFalse(coordinator.isSessionTerminating(s1.mHandle))
        assertFalse(ops.processes.containsKey(700))
        assertFalse(ops.processes.containsKey(702))
        assertTrue("Sibling session s2 must remain intact", ops.processes.containsKey(701))

        val s1Signals = ops.recordedSignals.filter { it.first == 700 }.map { it.second }
        assertEquals(listOf(OsConstants.SIGCONT, OsConstants.SIGTERM, OsConstants.SIGKILL), s1Signals)

        // Double-click guard: while a close is in-flight, a duplicate closeSession call is ignored.
        ops.processes[701]?.ignoresSigTerm = true
        val sleepEntered = CompletableDeferred<Unit>()
        val blocker = CompletableDeferred<Unit>()
        val slowOps = object : ProcessOps by ops {
            override fun sleepMs(millis: Long) {
                sleepEntered.complete(Unit)
                runBlocking { blocker.await() }
                ops.sleepMs(millis)
            }
        }
        val slowController = WorkspaceHibernationController(context, slowOps, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        val asyncCoordinator = WorkspaceHibernationCoordinator(
            context = context,
            controller = slowController,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
        var secondCompleted = 0
        val firstJob = asyncCoordinator.closeSession(ws.id, s2) { secondCompleted += 1 }
        assertTrue(firstJob != null)
        sleepEntered.await()
        assertTrue(asyncCoordinator.isSessionTerminating(s2.mHandle))
        val duplicateJob = asyncCoordinator.closeSession(ws.id, s2) { secondCompleted += 1 }
        assertEquals(null, duplicateJob)
        blocker.complete(Unit)
        firstJob!!.join()
        assertEquals(1, secondCompleted)
        assertFalse(asyncCoordinator.isSessionTerminating(s2.mHandle))

        asyncCoordinator.destroy()
        coordinator.destroy()
    }

    @Test
    fun crossWorkspaceTapOnSleepingWorkspaceWakesItUngatesSessionAndUpdatesBanner() = runBlocking<Unit> {
        val wsDefault = DEFAULT_WORKSPACE_ID
        val wsApi = WorkspaceManager.createWorkspace("API", "/tmp/api")
        val sDefault = newTestSession(800)
        val sApi = newTestSession(801)
        WorkspaceManager.associateSession(sDefault.mHandle, wsDefault)
        WorkspaceManager.associateSession(sApi.mHandle, wsApi.id)

        val ops = FakeProcessOps(
            listOf(
                FakeProcess(pid = 800, comm = "bash", state = 'S', ppid = 1, pgid = 800, sid = 800, uid = 10912, startTimeTicks = 9000L),
                FakeProcess(pid = 801, comm = "bash", state = 'S', ppid = 1, pgid = 801, sid = 801, uid = 10912, startTimeTicks = 9010L)
            )
        )
        val controller = WorkspaceHibernationController(context, ops, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        assertEquals(WorkspaceSleepState.SLEEPING, controller.sleepWorkspace(wsApi.id, listOf(sApi)))
        assertTrue(sApi.isInputGated)
        assertEquals(WorkspaceSleepState.SLEEPING, WorkspaceManager.getSleepState(wsApi.id))

        val coordinator = WorkspaceHibernationCoordinator(
            context = context,
            controller = controller,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )

        // Simulate cross-workspace switch + wake request
        assertTrue(WorkspaceManager.setActiveWorkspace(wsApi.id))
        var refreshCalls = 0
        val wakeJob = coordinator.requestWorkspaceWakeIfNeeded(
            workspaceId = wsApi.id,
            sessionsProvider = { listOf(sApi) },
            onComplete = { refreshCalls += 1 }
        )
        wakeJob?.join()

        assertEquals(wsApi.id, WorkspaceManager.activeWorkspace().id)
        assertEquals(WorkspaceSleepState.AWAKE, WorkspaceManager.getSleepState(wsApi.id))
        assertFalse("TerminalSession must be un-gated after wake", sApi.isInputGated)
        assertEquals('S', ops.processes[801]?.state)
        assertEquals(1, refreshCalls)
        coordinator.destroy()
    }

    @Test
    fun honestDrawerBadgesSshNoticeAndStatusBannerReflectAllSleepStates() {
        var sleepState by mutableStateOf(WorkspaceSleepState.SLEEPING)
        var wakeClicks = 0

        val workspaces = listOf(
            WorkspaceDrawerItem(
                id = DEFAULT_WORKSPACE_ID,
                name = "Default",
                rootPath = "/home",
                sessionCount = 1,
                sleepState = WorkspaceSleepState.AWAKE,
                isActive = true,
                isDefault = true
            ),
            WorkspaceDrawerItem(
                id = "ws-sleep",
                name = "API Server",
                rootPath = "/home/api",
                sessionCount = 1,
                sleepState = WorkspaceSleepState.SLEEPING,
                isActive = false,
                isDefault = false
            ),
            WorkspaceDrawerItem(
                id = "ws-err",
                name = "Broken",
                rootPath = "/home/broken",
                sessionCount = 1,
                sleepState = WorkspaceSleepState.ERROR,
                isActive = false,
                isDefault = false
            )
        )

        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                CollapsibleTabDrawer(
                    visible = true,
                    sessions = listOf(DrawerSessionItem("h1", "Shell", null, isActive = true, isRunning = true)),
                    workspaces = workspaces,
                    hibernationEnabled = true,
                    onDismiss = {},
                    onSelectSession = {},
                    onCloseSession = {},
                    onNewSession = {},
                    onOpenDevBrowser = {},
                    onOpenFileManager = {},
                    onOpenLocalhost = {},
                    onOpenQuickCommands = {},
                    onOpenSettings = {}
                )
                WorkspaceHibernationBanner(
                    sleepState = sleepState,
                    onWakeClick = { wakeClicks += 1 }
                )
            }
        }

        // Honest zzz only on SLEEPING, Error badge on ERROR, SSH honesty notice shown for sleep
        assertEquals(1, composeRule.onAllNodesWithText("zzz").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Error").assertIsDisplayed()

        // Banner in SLEEPING state shows honest copy + Wake button
        composeRule.onNodeWithTag("workspace_hibernation_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace_wake_banner_button").assertIsDisplayed().performClick()
        assertEquals(1, wakeClicks)

        // Banner in SUSPENDING / RESUMING / ERROR / AWAKE
        sleepState = WorkspaceSleepState.SUSPENDING
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Suspending workspace…").assertIsDisplayed()

        sleepState = WorkspaceSleepState.RESUMING
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Waking workspace…").assertIsDisplayed()

        sleepState = WorkspaceSleepState.ERROR
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Retry Wake").assertIsDisplayed().performClick()
        assertEquals(2, wakeClicks)

        sleepState = WorkspaceSleepState.AWAKE
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("workspace_hibernation_banner").assertDoesNotExist()

        // Overflow menu on awake workspace displays Sleep + SSH honesty notice (in addition to sleeping row subtitle)
        composeRule.onNodeWithTag("workspace_overflow_$DEFAULT_WORKSPACE_ID").performClick()
        composeRule.onNodeWithText("Sleep").assertIsDisplayed()
        composeRule.onAllNodesWithText("Remote SSH sessions may drop during long sleep", substring = true)
            .assertCountEquals(2)
        composeRule.onAllNodesWithText("Remote SSH sessions may drop during long sleep", substring = true)
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun hibernationDisabledHidesAllSleepBadgesEvenIfWorkspaceStateIsNonAwake() {
        val workspaces = listOf(
            WorkspaceDrawerItem(
                id = DEFAULT_WORKSPACE_ID,
                name = "Default",
                rootPath = "/home",
                sessionCount = 1,
                sleepState = WorkspaceSleepState.SLEEPING,
                isActive = true,
                isDefault = true
            ),
            WorkspaceDrawerItem(
                id = "ws-err",
                name = "Err",
                rootPath = "/home/err",
                sessionCount = 1,
                sleepState = WorkspaceSleepState.ERROR,
                isActive = false,
                isDefault = false
            ),
            WorkspaceDrawerItem(
                id = "ws-susp",
                name = "Susp",
                rootPath = "/home/susp",
                sessionCount = 1,
                sleepState = WorkspaceSleepState.SUSPENDING,
                isActive = false,
                isDefault = false
            ),
            WorkspaceDrawerItem(
                id = "ws-res",
                name = "Res",
                rootPath = "/home/res",
                sessionCount = 1,
                sleepState = WorkspaceSleepState.RESUMING,
                isActive = false,
                isDefault = false
            )
        )

        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                CollapsibleTabDrawer(
                    visible = true,
                    sessions = listOf(DrawerSessionItem("h1", "Shell", null, isActive = true, isRunning = true)),
                    workspaces = workspaces,
                    hibernationEnabled = false,
                    onDismiss = {},
                    onSelectSession = {},
                    onCloseSession = {},
                    onNewSession = {},
                    onOpenDevBrowser = {},
                    onOpenFileManager = {},
                    onOpenLocalhost = {},
                    onOpenQuickCommands = {},
                    onOpenSettings = {}
                )
            }
        }

        composeRule.onAllNodesWithText("zzz").assertCountEquals(0)
        composeRule.onAllNodesWithText("Error").assertCountEquals(0)
        composeRule.onAllNodesWithText("Suspending…").assertCountEquals(0)
        composeRule.onAllNodesWithText("Waking…").assertCountEquals(0)
        composeRule.onAllNodesWithText("Remote SSH sessions may drop during long sleep", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("workspace_sleep_badge_$DEFAULT_WORKSPACE_ID").assertDoesNotExist()
        composeRule.onNodeWithTag("workspace_sleep_badge_ws-err").assertDoesNotExist()

        val reportsAwake = SemanticsMatcher("contentDescription contains Awake") { node ->
            val desc = node.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }
            desc?.any { it.contains("Awake") } == true
        }
        composeRule.onNodeWithTag("workspace_row_$DEFAULT_WORKSPACE_ID").assert(reportsAwake)
        composeRule.onNodeWithTag("workspace_row_ws-err").assert(reportsAwake)
    }

    @Test
    fun requestWorkspaceWakeIfNeededRunsOnReadyAfterUngatingAndImmediatelyWhenAwake() = runBlocking<Unit> {
        val ws = WorkspaceManager.createWorkspace("OpenHere", "/tmp/open-here")
        val session = newTestSession(910)
        WorkspaceManager.associateSession(session.mHandle, ws.id)

        val ops = FakeProcessOps(
            listOf(
                FakeProcess(pid = 910, comm = "bash", state = 'S', ppid = 1, pgid = 910, sid = 910, uid = 10912, startTimeTicks = 11000L)
            )
        )
        val controller = WorkspaceHibernationController(context, ops, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        val coordinator = WorkspaceHibernationCoordinator(
            context = context,
            controller = controller,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )

        // 1. Already AWAKE -> onReady runs immediately
        var awakeReadyCalls = 0
        val immediateJob = coordinator.requestWorkspaceWakeIfNeeded(ws.id, { listOf(session) }) {
            awakeReadyCalls += 1
        }
        assertEquals(null, immediateJob)
        assertEquals(1, awakeReadyCalls)

        // 2. SLEEPING -> onReady runs only AFTER SIGCONT and un-gating complete
        assertEquals(WorkspaceSleepState.SLEEPING, controller.sleepWorkspace(ws.id, listOf(session)))
        assertTrue(session.isInputGated)

        var readySawUngated = false
        var readySawAwakeState = false
        var readySawRunningProcess = false
        val wakeJob = coordinator.requestWorkspaceWakeIfNeeded(ws.id, { listOf(session) }) {
            readySawUngated = !session.isInputGated
            readySawAwakeState = WorkspaceManager.getSleepState(ws.id) == WorkspaceSleepState.AWAKE
            readySawRunningProcess = ops.processes[910]?.state == 'S'
        }
        wakeJob?.join()

        assertTrue("onReady must run only after TerminalSession is un-gated", readySawUngated)
        assertTrue("onReady must observe AWAKE sleep state", readySawAwakeState)
        assertTrue("onReady must observe resumed process", readySawRunningProcess)
        coordinator.destroy()
    }

    @Test
    fun closeSessionsSequentiallyCompletesAllRemovalsBeforeOnAllClosed() = runBlocking<Unit> {
        val ws = WorkspaceManager.createWorkspace("BatchClose", "/tmp/batch-close")
        val s1 = newTestSession(920)
        val s2 = newTestSession(921)
        WorkspaceManager.associateSession(s1.mHandle, ws.id)
        WorkspaceManager.associateSession(s2.mHandle, ws.id)

        val ops = FakeProcessOps(
            listOf(
                FakeProcess(pid = 920, comm = "bash", state = 'S', ppid = 1, pgid = 920, sid = 920, uid = 10912, startTimeTicks = 12000L),
                FakeProcess(pid = 921, comm = "bash", state = 'S', ppid = 1, pgid = 921, sid = 921, uid = 10912, startTimeTicks = 12010L)
            )
        )
        val controller = WorkspaceHibernationController(context, ops, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        assertEquals(WorkspaceSleepState.SLEEPING, controller.sleepWorkspace(ws.id, listOf(s1, s2)))

        val coordinator = WorkspaceHibernationCoordinator(
            context = context,
            controller = controller,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )

        val events = mutableListOf<String>()
        val job = coordinator.closeSessionsSequentially(
            workspaceId = ws.id,
            sessions = listOf(s1, s2),
            onEachClosed = { closed ->
                assertFalse("Process for ${closed.mHandle} must be dead before removal", ops.processes.containsKey(closed.pid))
                events.add("removed:${closed.mHandle}")
            },
            onAllClosed = {
                events.add("all_closed")
            }
        )
        job?.join()

        assertEquals(
            listOf("removed:${s1.mHandle}", "removed:${s2.mHandle}", "all_closed"),
            events
        )
        assertFalse(coordinator.isSessionTerminating(s1.mHandle))
        assertFalse(coordinator.isSessionTerminating(s2.mHandle))
        coordinator.destroy()
    }

    @Test
    fun destroyingCoordinatorMidWakeStillCompletesWakeAndUngatesWithoutCallingUiCallback() = runBlocking<Unit> {
        val ws = WorkspaceManager.createWorkspace("RotateWake", "/tmp/rotate-wake")
        val session = newTestSession(930)
        WorkspaceManager.associateSession(session.mHandle, ws.id)

        val sigContEntered = CompletableDeferred<Unit>()
        val releaseSigCont = CompletableDeferred<Unit>()
        val backingOps = FakeProcessOps(
            listOf(
                FakeProcess(pid = 930, comm = "bash", state = 'S', ppid = 1, pgid = 930, sid = 930, uid = 10912, startTimeTicks = 13000L)
            )
        )
        var gateSignals = false
        val ops = object : ProcessOps by backingOps {
            override fun sendSignal(pid: Int, signal: Int): SignalStatus {
                if (gateSignals && signal == OsConstants.SIGCONT) {
                    sigContEntered.complete(Unit)
                    runBlocking { releaseSigCont.await() }
                }
                return backingOps.sendSignal(pid, signal)
            }
        }
        val controller = WorkspaceHibernationController(context, ops, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        assertEquals(WorkspaceSleepState.SLEEPING, controller.sleepWorkspace(ws.id, listOf(session)))
        assertTrue(session.isInputGated)

        gateSignals = true
        val coordinator = WorkspaceHibernationCoordinator(
            context = context,
            controller = controller,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
        var uiCallbackInvoked = false
        val wakeJob = coordinator.requestWorkspaceWakeIfNeeded(ws.id, { listOf(session) }) {
            uiCallbackInvoked = true
        }
        assertTrue(wakeJob != null)
        sigContEntered.await()

        // Simulate Activity.onDestroy() cancelling the coordinator mid-wake
        coordinator.destroy()
        releaseSigCont.complete(Unit)
        wakeJob!!.join()

        assertEquals(WorkspaceSleepState.AWAKE, WorkspaceManager.getSleepState(ws.id))
        assertFalse("TerminalSession must still be un-gated even if Activity was destroyed mid-wake", session.isInputGated)
        assertEquals('S', backingOps.processes[930]?.state)
        assertFalse("Destroyed activity UI callback must not be invoked", uiCallbackInvoked)
    }

    @Test
    fun wakeWorkspaceBeforeRemovalResumesAndUngatesSessionsBeforeMigratingToDefault() = runBlocking<Unit> {
        val ws = WorkspaceManager.createWorkspace("MigrateSleeping", "/tmp/migrate-sleeping")
        val session = newTestSession(940)
        WorkspaceManager.associateSession(session.mHandle, ws.id)

        val ops = FakeProcessOps(
            listOf(
                FakeProcess(pid = 940, comm = "bash", state = 'S', ppid = 1, pgid = 940, sid = 940, uid = 10912, startTimeTicks = 14000L),
                FakeProcess(pid = 941, comm = "sleep", state = 'S', ppid = 940, pgid = 941, sid = 940, uid = 10912, startTimeTicks = 14010L)
            )
        )
        val controller = WorkspaceHibernationController(context, ops, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        assertEquals(WorkspaceSleepState.SLEEPING, controller.sleepWorkspace(ws.id, listOf(session)))
        assertTrue(session.isInputGated)
        assertEquals('T', ops.processes[940]?.state)
        assertEquals('T', ops.processes[941]?.state)

        val coordinator = WorkspaceHibernationCoordinator(
            context = context,
            controller = controller,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )

        var migrated = emptyList<String>()
        val job = coordinator.wakeWorkspaceBeforeRemoval(ws.id, { listOf(session) }) {
            assertFalse("Session must be un-gated before migration", session.isInputGated)
            assertEquals('S', ops.processes[940]?.state)
            assertEquals('S', ops.processes[941]?.state)
            assertFalse("Persisted SID records must be cleared before migration", controller.hasRecordsForWorkspace(ws.id))
            migrated = WorkspaceManager.removeWorkspace(ws.id)
        }
        job?.join()

        assertEquals(listOf(session.mHandle), migrated)
        assertTrue(
            WorkspaceManager.state.value.workspaces.first { it.id == DEFAULT_WORKSPACE_ID }.sessionHandles.contains(session.mHandle)
        )
        assertEquals(WorkspaceSleepState.AWAKE, WorkspaceManager.getSleepState(DEFAULT_WORKSPACE_ID))
        coordinator.destroy()
    }

    @Test
    fun rollbackFlagsUngateSessionsAndResetSleepStatesAcrossBothFlags() = runBlocking<Unit> {
        val ws = WorkspaceManager.createWorkspace("RollbackWs", "/tmp/rollback")
        val s1 = newTestSession(950)
        WorkspaceManager.associateSession(s1.mHandle, ws.id)

        val ops = FakeProcessOps(
            listOf(
                FakeProcess(pid = 950, comm = "bash", state = 'S', ppid = 1, pgid = 950, sid = 950, uid = 10912, startTimeTicks = 15000L)
            )
        )
        val controller = WorkspaceHibernationController(context, ops, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        assertEquals(WorkspaceSleepState.SLEEPING, controller.sleepWorkspace(ws.id, listOf(s1)))
        assertTrue(s1.isInputGated)

        val coordinator = WorkspaceHibernationCoordinator(
            context = context,
            controller = controller,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )

        // Case A: workspaces_enabled flipped OFF while hibernation stays ON -> wakes & un-gates & resets state.
        BeTermuxSettings.setWorkspacesEnabled(context, false)
        var callbackCount = 0
        coordinator.reconcileRollbackFlagsIfDisabled({ listOf(s1) }) { callbackCount++ }?.join()
        assertFalse(s1.isInputGated)
        assertEquals('S', ops.processes[950]?.state)
        assertEquals(WorkspaceSleepState.AWAKE, WorkspaceManager.getSleepState(ws.id))
        assertEquals(1, callbackCount)

        // Case B: hibernation flipped OFF -> un-gates & resets states with ZERO signals.
        BeTermuxSettings.setWorkspacesEnabled(context, true)
        assertEquals(WorkspaceSleepState.SLEEPING, controller.sleepWorkspace(ws.id, listOf(s1)))
        assertTrue(s1.isInputGated)
        ops.recordedSignals.clear()

        BeTermuxSettings.setWorkspaceHibernationEnabled(context, false)
        coordinator.reconcileRollbackFlagsIfDisabled({ listOf(s1) }) { callbackCount++ }?.join()
        assertFalse(s1.isInputGated)
        assertEquals(WorkspaceSleepState.AWAKE, WorkspaceManager.getSleepState(ws.id))
        assertTrue("Zero signals expected when hibernation is disabled", ops.recordedSignals.isEmpty())
        assertFalse(controller.hasAnyRecords())
        assertEquals(2, callbackCount)
        coordinator.destroy()
    }

    @Test
    fun closeSessionsSequentiallyCleansUpDeadShellWithPersistedChildProcesses() = runBlocking<Unit> {
        val ws = WorkspaceManager.createWorkspace("BatchDeadShell", "/tmp/batch-dead")
        val s1 = newTestSession(960)
        WorkspaceManager.associateSession(s1.mHandle, ws.id)

        val ops = FakeProcessOps(
            listOf(
                FakeProcess(pid = 960, comm = "bash", state = 'S', ppid = 1, pgid = 960, sid = 960, uid = 10912, startTimeTicks = 16000L),
                FakeProcess(pid = 961, comm = "python3", state = 'S', ppid = 960, pgid = 961, sid = 960, uid = 10912, startTimeTicks = 16005L)
            )
        )
        val controller = WorkspaceHibernationController(context, ops, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        assertEquals(WorkspaceSleepState.SLEEPING, controller.sleepWorkspace(ws.id, listOf(s1)))

        // Shell died leaving pid <= 0, while child 961 remains in SID 960.
        ops.processes.remove(960)
        val pidField = TerminalSession::class.java.getDeclaredField("mShellPid")
        pidField.isAccessible = true
        pidField.setInt(s1, 0)

        val coordinator = WorkspaceHibernationCoordinator(
            context = context,
            controller = controller,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
        val events = mutableListOf<String>()
        coordinator.closeSessionsSequentially(
            workspaceId = ws.id,
            sessions = listOf(s1),
            onEachClosed = { events.add("closed:${it.mHandle}") },
            onAllClosed = { events.add("done") }
        )?.join()

        assertEquals(listOf("closed:${s1.mHandle}", "done"), events)
        assertFalse("Persisted child 961 must be killed even when shell pid <= 0", ops.processes.containsKey(961))
        assertFalse(controller.hasRecordsForWorkspace(ws.id))
        coordinator.destroy()
    }

    @Test
    fun switchToEmptyNonAwakeWorkspaceWakesBeforeCreatingSessionWithoutRollback() = runBlocking<Unit> {
        val ws = WorkspaceManager.createWorkspace("EmptyNonAwake", "/tmp/empty-non-awake")
        WorkspaceManager.setSleepState(ws.id, WorkspaceSleepState.SLEEPING)

        val ops = FakeProcessOps(emptyList())
        val controller = WorkspaceHibernationController(context, ops, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        val coordinator = WorkspaceHibernationCoordinator(
            context = context,
            controller = controller,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )

        assertTrue(WorkspaceSessionPolicy.shouldWakeBeforeCreatingSession(WorkspaceSleepState.SLEEPING, hasSessions = false))
        assertTrue(WorkspaceSessionPolicy.shouldWakeBeforeCreatingSession(WorkspaceSleepState.RESUMING, hasSessions = false))
        assertTrue(WorkspaceSessionPolicy.shouldWakeBeforeCreatingSession(WorkspaceSleepState.ERROR, hasSessions = true))
        assertFalse(WorkspaceSessionPolicy.shouldWakeBeforeCreatingSession(WorkspaceSleepState.ERROR, hasSessions = false))
        assertFalse(WorkspaceSessionPolicy.shouldWakeBeforeCreatingSession(WorkspaceSleepState.AWAKE, hasSessions = true))

        assertTrue(WorkspaceManager.setActiveWorkspace(ws.id))
        var createdInWorkspaceId: String? = null
        coordinator.requestWorkspaceWakeIfNeeded(ws.id, { emptyList() }) {
            // Simulates selectOrCreateWorkspaceSession running inside requestWorkspaceWakeIfNeeded callback:
            val stateAfterWake = WorkspaceManager.getSleepState(ws.id)
            assertFalse(
                "addNewSession must not defer again after wake completes",
                WorkspaceSessionPolicy.shouldWakeBeforeCreatingSession(stateAfterWake, hasSessions = false)
            )
            createdInWorkspaceId = WorkspaceManager.activeWorkspace().id
        }?.join()

        assertEquals(ws.id, createdInWorkspaceId)
        assertEquals(WorkspaceSleepState.AWAKE, WorkspaceManager.getSleepState(ws.id))
        coordinator.destroy()
    }
}
