package com.betermux.workspace

import android.content.Context
import android.system.OsConstants
import com.betermux.features.BeTermuxSettings
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkspaceHibernationTest {

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
        var ignoresSigTerm: Boolean = false,
        var refusesSigStop: Boolean = false,
        var exitsOnTermAfterTicks: Int = 0
    ) {
        fun toStatLine(): String {
            // Fields: pid (comm) state ppid pgrp session tty tpgid flags minflt cminflt majflt cmajflt
            // utime stime cutime cstime priority nice num_threads itrealvalue starttime vsize rss
            return "$pid ($comm) $state $ppid $pgid $sid 0 -1 4194304 0 0 0 0 0 0 0 0 20 0 1 0 $startTimeTicks 1024 10"
        }
    }

    private class FakeProcessOps(
        initialProcesses: List<FakeProcess> = emptyList(),
        var procBlocked: Boolean = false,
        var onSignalSent: ((pid: Int, signal: Int) -> Unit)? = null
    ) : ProcessOps {
        val processes = linkedMapOf<Int, FakeProcess>().apply {
            initialProcesses.forEach { put(it.pid, it) }
        }
        val recordedSignals = mutableListOf<Pair<Int, Int>>()
        var virtualClockMs: Long = 1_000L
        var sleepPollCount: Int = 0
        var failNextSignalWithEperm: Boolean = false

        override fun listProcPids(): List<Int>? {
            if (procBlocked) return null
            return processes.keys.toList()
        }

        override fun readProcStat(pid: Int): String? {
            if (procBlocked) return null
            return processes[pid]?.toStatLine()
        }

        override fun readProcUid(pid: Int): Int? {
            if (procBlocked) return null
            return processes[pid]?.uid
        }

        override fun sendSignal(pid: Int, signal: Int): SignalStatus {
            recordedSignals.add(pid to signal)
            if (failNextSignalWithEperm) {
                failNextSignalWithEperm = false
                return SignalStatus.EPERM
            }
            val proc = processes[pid] ?: return SignalStatus.ESRCH
            when (signal) {
                OsConstants.SIGSTOP -> {
                    if (!proc.refusesSigStop) {
                        proc.state = 'T'
                    }
                }
                OsConstants.SIGCONT -> {
                    if (proc.state == 'T') {
                        proc.state = 'S'
                    }
                }
                OsConstants.SIGTERM -> {
                    if (!proc.ignoresSigTerm && proc.exitsOnTermAfterTicks == 0) {
                        processes.remove(pid)
                    }
                }
                OsConstants.SIGKILL -> {
                    processes.remove(pid)
                }
            }
            return SignalStatus.OK.also {
                onSignalSent?.invoke(pid, signal)
            }
        }

        override fun sleepMs(millis: Long) {
            virtualClockMs += millis
            sleepPollCount += 1
            val toRemove = mutableListOf<Int>()
            for (proc in processes.values) {
                if (proc.exitsOnTermAfterTicks > 0) {
                    proc.exitsOnTermAfterTicks -= 1
                    if (proc.exitsOnTermAfterTicks == 0) {
                        toRemove.add(proc.pid)
                    }
                }
            }
            toRemove.forEach { processes.remove(it) }
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
        BeTermuxSettings.setWorkspaceHibernationEnabled(context, true)
        WorkspaceManager.resetForTesting(context, "/data/data/com.termux/files/home")
    }

    @Test
    fun parseProcStatHandlesSpacesAndNestedParenthesesInComm() {
        val raw = "123 (foo (bar) baz) T 1 200 100 0 -1 4194304 10 0 0 0 1 2 0 0 20 0 1 0 987654321 4096 12"
        val parsed = ProcSessionController.parseProcStat(123, raw, 10912)
        assertNotNull(parsed)
        parsed!!
        assertEquals(123, parsed.pid)
        assertEquals("foo (bar) baz", parsed.comm)
        assertEquals('T', parsed.state)
        assertEquals(1, parsed.ppid)
        assertEquals(200, parsed.pgid)
        assertEquals(100, parsed.sid)
        assertEquals(10912, parsed.uid)
        assertEquals(987654321L, parsed.startTimeTicks)
    }

    @Test
    fun multiPgidSessionOnlySignalsMatchingSidAndUidNeverTouchesPid1OrOtherUid() {
        val ops = FakeProcessOps(
            listOf(
                FakeProcess(pid = 1, comm = "init", state = 'S', ppid = 0, pgid = 1, sid = 100, uid = 10912, startTimeTicks = 1L),
                FakeProcess(pid = 100, comm = "bash", state = 'S', ppid = 1, pgid = 100, sid = 100, uid = 10912, startTimeTicks = 1000L),
                FakeProcess(pid = 101, comm = "vim", state = 'S', ppid = 100, pgid = 101, sid = 100, uid = 10912, startTimeTicks = 1005L),
                FakeProcess(pid = 102, comm = "sleep", state = 'S', ppid = 100, pgid = 102, sid = 100, uid = 10912, startTimeTicks = 1010L),
                FakeProcess(pid = 200, comm = "other_uid", state = 'S', ppid = 1, pgid = 200, sid = 100, uid = 9999, startTimeTicks = 1020L),
                FakeProcess(pid = 201, comm = "other_sid", state = 'S', ppid = 1, pgid = 201, sid = 201, uid = 10912, startTimeTicks = 1030L),
                FakeProcess(pid = 202, comm = "bad_pgid", state = 'S', ppid = 1, pgid = 0, sid = 100, uid = 10912, startTimeTicks = 1040L)
            )
        )

        val enumRes = ProcSessionController.findSessionProcesses(100, 10912, ops)
        assertTrue(enumRes is ProcEnumerationResult.Success)
        val foundPids = (enumRes as ProcEnumerationResult.Success).processes.map { it.pid }.toSet()
        assertEquals(setOf(100, 101, 102), foundPids)

        val ws = WorkspaceManager.createWorkspace("API", "/tmp/api")
        val session = newTestSession(100)
        val controller = WorkspaceHibernationController(context, ops, ownerUidProvider = { 10912 })

        val stateAfterSleep = runBlocking { controller.sleepWorkspace(ws.id, listOf(session)) }
        assertEquals(WorkspaceSleepState.SLEEPING, stateAfterSleep)
        assertEquals(WorkspaceSleepState.SLEEPING, WorkspaceManager.getSleepState(ws.id))
        assertTrue(session.isInputGated)

        val sigstopTargets = ops.recordedSignals.filter { it.second == OsConstants.SIGSTOP }.map { it.first }.toSet()
        assertEquals(setOf(100, 101, 102), sigstopTargets)
        assertFalse(ops.recordedSignals.any { it.first == 1 || it.first == 200 || it.first == 201 || it.first == 202 })
        assertEquals('T', ops.processes[100]?.state)
        assertEquals('T', ops.processes[101]?.state)
        assertEquals('T', ops.processes[102]?.state)
        assertEquals('S', ops.processes[200]?.state)
        assertEquals('S', ops.processes[201]?.state)

        // Wake resumes all stopped PIDs and un-gates input.
        val stateAfterWake = runBlocking { controller.wakeWorkspace(ws.id, listOf(session)) }
        assertEquals(WorkspaceSleepState.AWAKE, stateAfterWake)
        assertFalse(session.isInputGated)
        assertEquals('S', ops.processes[100]?.state)
        assertEquals('S', ops.processes[101]?.state)
        assertEquals('S', ops.processes[102]?.state)
    }

    @Test
    fun partialFailedStopVerificationFailsClosedToErrorRollsBackSigcontAndUngates() {
        val ops = FakeProcessOps(
            listOf(
                FakeProcess(pid = 100, comm = "bash", state = 'S', ppid = 1, pgid = 100, sid = 100, uid = 10912, startTimeTicks = 1000L),
                FakeProcess(pid = 101, comm = "stubborn", state = 'R', ppid = 100, pgid = 101, sid = 100, uid = 10912, startTimeTicks = 1005L, refusesSigStop = true)
            )
        )
        val ws = WorkspaceManager.createWorkspace("Worker", "/tmp/worker")
        val session = newTestSession(100)
        val controller = WorkspaceHibernationController(context, ops, ownerUidProvider = { 10912 })

        val resultState = runBlocking { controller.sleepWorkspace(ws.id, listOf(session)) }
        assertEquals(WorkspaceSleepState.ERROR, resultState)
        assertEquals(WorkspaceSleepState.ERROR, WorkspaceManager.getSleepState(ws.id))
        assertFalse("Input gate must be cleared on fail-closed rollback", session.isInputGated)
        // bash (100) was stopped then rolled back with SIGCONT so user isn't left frozen
        assertEquals('S', ops.processes[100]?.state)
        assertTrue(ops.recordedSignals.any { it == (100 to OsConstants.SIGCONT) })
    }

    @Test
    fun blockedProcEnumerationFailsClosedToError() {
        val ops = FakeProcessOps(procBlocked = true)
        val ws = WorkspaceManager.createWorkspace("Blocked", "/tmp/blocked")
        val session = newTestSession(100)
        val controller = WorkspaceHibernationController(context, ops, ownerUidProvider = { 10912 })

        val resultState = runBlocking { controller.sleepWorkspace(ws.id, listOf(session)) }
        assertEquals(WorkspaceSleepState.ERROR, resultState)
        assertEquals(WorkspaceSleepState.ERROR, WorkspaceManager.getSleepState(ws.id))
        assertFalse(session.isInputGated)
        assertTrue("No signals should be sent when /proc is blocked", ops.recordedSignals.isEmpty())
    }

    @Test
    fun terminationSendsSigcontSigtermPollsAndSigkillsSurvivors() {
        val ops = FakeProcessOps(
            listOf(
                // Interactive bash ignores SIGTERM
                FakeProcess(pid = 300, comm = "bash", state = 'T', ppid = 1, pgid = 300, sid = 300, uid = 10912, startTimeTicks = 5000L, ignoresSigTerm = true),
                // Child exits cleanly on SIGTERM
                FakeProcess(pid = 301, comm = "sleep", state = 'T', ppid = 300, pgid = 301, sid = 300, uid = 10912, startTimeTicks = 5005L)
            )
        )
        val ws = WorkspaceManager.createWorkspace("Term", "/tmp/term")
        val session = newTestSession(300)
        val controller = WorkspaceHibernationController(
            context = context,
            ops = ops,
            ownerUidProvider = { 10912 },
            terminationTimeoutMs = 200L
        )

        val ok = runBlocking { controller.terminateWorkspaceSessions(ws.id, listOf(session)) }
        assertTrue(ok)
        assertTrue(ops.processes.isEmpty())

        // Verify order: SIGCONT before SIGTERM before SIGKILL
        val bashSignals = ops.recordedSignals.filter { it.first == 300 }.map { it.second }
        assertEquals(listOf(OsConstants.SIGCONT, OsConstants.SIGTERM, OsConstants.SIGKILL), bashSignals)

        val sleepSignals = ops.recordedSignals.filter { it.first == 301 }.map { it.second }
        assertEquals(listOf(OsConstants.SIGCONT, OsConstants.SIGTERM), sleepSignals)
    }

    @Test
    fun hibernationFlagDisabledMakesSleepNoOpWithZeroSignals() {
        BeTermuxSettings.setWorkspaceHibernationEnabled(context, false)
        val ops = FakeProcessOps(
            listOf(
                FakeProcess(pid = 400, comm = "bash", state = 'S', ppid = 1, pgid = 400, sid = 400, uid = 10912, startTimeTicks = 7000L)
            )
        )
        val ws = WorkspaceManager.createWorkspace("NoSleep", "/tmp/nosleep")
        val session = newTestSession(400)
        val controller = WorkspaceHibernationController(context, ops, ownerUidProvider = { 10912 })

        val state = runBlocking { controller.sleepWorkspace(ws.id, listOf(session)) }
        assertEquals(WorkspaceSleepState.AWAKE, state)
        assertEquals(WorkspaceSleepState.AWAKE, WorkspaceManager.getSleepState(ws.id))
        assertFalse(session.isInputGated)
        assertTrue(ops.recordedSignals.isEmpty())
        assertEquals('S', ops.processes[400]?.state)
    }

    @Test
    fun coldStartReconciliationVerifiesUidSidStartTimeTicksAndSkipsReusedPid() {
        val ops1 = FakeProcessOps(
            listOf(
                FakeProcess(pid = 500, comm = "bash", state = 'S', ppid = 1, pgid = 500, sid = 500, uid = 10912, startTimeTicks = 8888L, ignoresSigTerm = true),
                FakeProcess(pid = 501, comm = "python3", state = 'S', ppid = 500, pgid = 501, sid = 500, uid = 10912, startTimeTicks = 8890L)
            )
        )
        val ws = WorkspaceManager.createWorkspace("Leaked", "/tmp/leaked")
        val session = newTestSession(500)
        val controller1 = WorkspaceHibernationController(context, ops1, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        assertEquals(WorkspaceSleepState.SLEEPING, runBlocking { controller1.sleepWorkspace(ws.id, listOf(session)) })

        // Simulate app kill + cold start with matching leaked 'T' processes -> cleaned up.
        val opsColdMatch = FakeProcessOps(
            listOf(
                FakeProcess(pid = 500, comm = "bash", state = 'T', ppid = 1, pgid = 500, sid = 500, uid = 10912, startTimeTicks = 8888L, ignoresSigTerm = true),
                FakeProcess(pid = 501, comm = "python3", state = 'T', ppid = 500, pgid = 501, sid = 500, uid = 10912, startTimeTicks = 8890L)
            )
        )
        val controller2 = WorkspaceHibernationController(context, opsColdMatch, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        runBlocking { controller2.reconcileColdStartLeakedProcesses(10912) }
        assertTrue("Leaked stopped processes should be terminated on cold start", opsColdMatch.processes.isEmpty())

        // Now test PID/SID reuse protection: persist another record, then simulate PID 600 reused with different startTimeTicks.
        val ws2 = WorkspaceManager.createWorkspace("Reuse", "/tmp/reuse")
        val session2 = newTestSession(600)
        val ops2 = FakeProcessOps(
            listOf(
                FakeProcess(pid = 600, comm = "bash", state = 'S', ppid = 1, pgid = 600, sid = 600, uid = 10912, startTimeTicks = 9999L)
            )
        )
        val controller3 = WorkspaceHibernationController(context, ops2, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        assertEquals(WorkspaceSleepState.SLEEPING, runBlocking { controller3.sleepWorkspace(ws2.id, listOf(session2)) })

        val opsReused = FakeProcessOps(
            listOf(
                FakeProcess(pid = 600, comm = "unrelated", state = 'T', ppid = 1, pgid = 600, sid = 600, uid = 10912, startTimeTicks = 12345L)
            )
        )
        val controller4 = WorkspaceHibernationController(context, opsReused, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        runBlocking { controller4.reconcileColdStartLeakedProcesses(10912) }
        assertTrue("Reused PID with different startTimeTicks must never be signaled", opsReused.recordedSignals.isEmpty())
        assertEquals(1, opsReused.processes.size)
    }

    @Test
    fun coldStartReconciliationRetainsRecordAndSetsErrorWhenTerminationFails() {
        val ops1 = FakeProcessOps(
            listOf(
                FakeProcess(pid = 700, comm = "bash", state = 'S', ppid = 1, pgid = 700, sid = 700, uid = 10912, startTimeTicks = 4444L)
            )
        )
        val ws = WorkspaceManager.createWorkspace("ColdFail", "/tmp/coldfail")
        val session = newTestSession(700)
        val controller1 = WorkspaceHibernationController(context, ops1, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        assertEquals(WorkspaceSleepState.SLEEPING, runBlocking { controller1.sleepWorkspace(ws.id, listOf(session)) })

        val opsColdFail = FakeProcessOps(
            listOf(
                FakeProcess(pid = 700, comm = "bash", state = 'T', ppid = 1, pgid = 700, sid = 700, uid = 10912, startTimeTicks = 4444L)
            )
        )
        opsColdFail.failNextSignalWithEperm = true
        val controller2 = WorkspaceHibernationController(context, opsColdFail, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        runBlocking { controller2.reconcileColdStartLeakedProcesses(10912) }

        assertEquals(WorkspaceSleepState.ERROR, WorkspaceManager.getSleepState(ws.id))
        val rawPrefs = context.getSharedPreferences("betermux_hibernation_sids", Context.MODE_PRIVATE)
            .getString("managed_sids_json", null)
        assertNotNull("Persisted SID record must be retained on failed cold-start termination", rawPrefs)
        assertTrue(rawPrefs!!.contains("\"sid\":700"))
    }

    @Test
    fun terminateSingleSessionSuccessAndFailurePaths() {
        // Success path on a sleeping workspace with 2 sessions: removes only the closed session's record.
        val ops = FakeProcessOps(
            listOf(
                FakeProcess(pid = 800, comm = "bash", state = 'S', ppid = 1, pgid = 800, sid = 800, uid = 10912, startTimeTicks = 1000L),
                FakeProcess(pid = 810, comm = "bash", state = 'S', ppid = 1, pgid = 810, sid = 810, uid = 10912, startTimeTicks = 1001L)
            )
        )
        val ws = WorkspaceManager.createWorkspace("SingleTerm", "/tmp/singleterm")
        val s1 = newTestSession(800)
        val s2 = newTestSession(810)
        val controller = WorkspaceHibernationController(context, ops, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        assertEquals(WorkspaceSleepState.SLEEPING, runBlocking { controller.sleepWorkspace(ws.id, listOf(s1, s2)) })

        val ok = runBlocking { controller.terminateSingleSession(ws.id, s1) }
        assertTrue(ok)
        assertFalse(ops.processes.containsKey(800))
        assertTrue(ops.processes.containsKey(810))
        val rawPrefs = context.getSharedPreferences("betermux_hibernation_sids", Context.MODE_PRIVATE)
            .getString("managed_sids_json", null)
        assertNotNull(rawPrefs)
        assertFalse(rawPrefs!!.contains("\"sid\":800"))
        assertTrue(rawPrefs.contains("\"sid\":810"))

        // Failure path on AWAKE workspace: sets ERROR and un-gates session.
        val wsFail = WorkspaceManager.createWorkspace("SingleFail", "/tmp/singlefail")
        val opsFail = FakeProcessOps(
            listOf(
                FakeProcess(pid = 820, comm = "bash", state = 'S', ppid = 1, pgid = 820, sid = 820, uid = 10912, startTimeTicks = 1002L)
            )
        )
        opsFail.failNextSignalWithEperm = true
        val sFail = newTestSession(820)
        val failController = WorkspaceHibernationController(context, opsFail, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        val failedResult = runBlocking { failController.terminateSingleSession(wsFail.id, sFail) }
        assertFalse(failedResult)
        assertEquals(WorkspaceSleepState.ERROR, WorkspaceManager.getSleepState(wsFail.id))
        assertFalse("Input gate must be cleared when single-session termination fails on non-sleeping workspace", sFail.isInputGated)
    }

    @Test
    fun hibernationFlagDisabledSuppressesAllSignalPathsAndResetsStaleState() {
        val ws = WorkspaceManager.createWorkspace("RollbackAll", "/tmp/rollback")
        // Seed sleeping state + persisted record while enabled.
        val opsSeed = FakeProcessOps(
            listOf(
                FakeProcess(pid = 900, comm = "bash", state = 'S', ppid = 1, pgid = 900, sid = 900, uid = 10912, startTimeTicks = 2000L)
            )
        )
        val sSeed = newTestSession(900)
        val seedController = WorkspaceHibernationController(context, opsSeed, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)
        assertEquals(WorkspaceSleepState.SLEEPING, runBlocking { seedController.sleepWorkspace(ws.id, listOf(sSeed)) })
        assertTrue(sSeed.isInputGated)

        // Flip rollback flag OFF and verify ALL paths emit zero ProcSessionController signals.
        BeTermuxSettings.setWorkspaceHibernationEnabled(context, false)
        val opsFlagOff = FakeProcessOps(
            listOf(
                FakeProcess(pid = 900, comm = "bash", state = 'T', ppid = 1, pgid = 900, sid = 900, uid = 10912, startTimeTicks = 2000L)
            )
        )
        // Use a PID > Linux PID_MAX so legacy finishIfRunning() is harmless in unit tests.
        val sessionFlagOff = newTestSession(999_999_999).apply { setInputGated(true) }
        val controller = WorkspaceHibernationController(context, opsFlagOff, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)

        // sleepWorkspace resets stale SLEEPING/ERROR -> AWAKE and un-gates.
        assertEquals(WorkspaceSleepState.AWAKE, runBlocking { controller.sleepWorkspace(ws.id, listOf(sessionFlagOff)) })
        assertEquals(WorkspaceSleepState.AWAKE, WorkspaceManager.getSleepState(ws.id))
        assertFalse(sessionFlagOff.isInputGated)

        // wakeWorkspace un-gates and sets AWAKE with zero SIGCONT.
        WorkspaceManager.setSleepState(ws.id, WorkspaceSleepState.ERROR)
        sessionFlagOff.setInputGated(true)
        assertEquals(WorkspaceSleepState.AWAKE, runBlocking { controller.wakeWorkspace(ws.id, listOf(sessionFlagOff)) })
        assertEquals(WorkspaceSleepState.AWAKE, WorkspaceManager.getSleepState(ws.id))
        assertFalse(sessionFlagOff.isInputGated)

        // terminateWorkspaceSessions, terminateSingleSession, reconcileColdStartLeakedProcesses emit zero ProcSessionController signals.
        assertTrue(runBlocking { controller.terminateWorkspaceSessions(ws.id, listOf(sessionFlagOff)) })
        assertTrue(runBlocking { controller.terminateSingleSession(ws.id, sessionFlagOff) })
        runBlocking { controller.reconcileColdStartLeakedProcesses(10912) }
        assertTrue("Zero ProcSessionController signals allowed when hibernation flag is off", opsFlagOff.recordedSignals.isEmpty())
    }

    @Test
    fun sleepWorkspaceCancellationRollsBackSigcontUngatesAndAvoidsSuspendingStuckState() {
        var cancelTargetJob: Job? = null
        val ops = FakeProcessOps(
            listOf(
                FakeProcess(pid = 910, comm = "bash", state = 'S', ppid = 1, pgid = 910, sid = 910, uid = 10912, startTimeTicks = 3000L),
                FakeProcess(pid = 920, comm = "bash", state = 'S', ppid = 1, pgid = 920, sid = 920, uid = 10912, startTimeTicks = 3001L)
            )
        )
        ops.onSignalSent = { pid, signal ->
            if (pid == 910 && signal == OsConstants.SIGSTOP) {
                cancelTargetJob?.cancel()
            }
        }
        val ws = WorkspaceManager.createWorkspace("CancelSleep", "/tmp/cancelsleep")
        val s1 = newTestSession(910)
        val s2 = newTestSession(920)
        val controller = WorkspaceHibernationController(context, ops, ownerUidProvider = { 10912 })

        var caughtCancellation = false
        runBlocking {
            val job = launch {
                try {
                    controller.sleepWorkspace(ws.id, listOf(s1, s2))
                } catch (e: CancellationException) {
                    caughtCancellation = true
                    throw e
                }
            }
            cancelTargetJob = job
            job.join()
        }

        assertTrue("Expected CancellationException during sleepWorkspace", caughtCancellation)
        assertNotEquals(WorkspaceSleepState.SUSPENDING, WorkspaceManager.getSleepState(ws.id))
        assertEquals(WorkspaceSleepState.AWAKE, WorkspaceManager.getSleepState(ws.id))
        assertFalse(s1.isInputGated)
        assertFalse(s2.isInputGated)
        assertEquals('S', ops.processes[910]?.state)
        assertEquals('S', ops.processes[920]?.state)
        assertTrue(ops.recordedSignals.any { it == (910 to OsConstants.SIGCONT) })
        assertNull(
            context.getSharedPreferences("betermux_hibernation_sids", Context.MODE_PRIVATE)
                .getString("managed_sids_json", null)
        )
    }

    @Test
    fun wakeWorkspaceBlockedProcTransitionsToErrorAndKeepsInputGated() {
        val ops = FakeProcessOps(
            listOf(
                FakeProcess(pid = 930, comm = "bash", state = 'S', ppid = 1, pgid = 930, sid = 930, uid = 10912, startTimeTicks = 4000L)
            )
        )
        val ws = WorkspaceManager.createWorkspace("WakeFail", "/tmp/wakefail")
        val session = newTestSession(930)
        val controller = WorkspaceHibernationController(context, ops, ownerUidProvider = { 10912 })

        assertEquals(WorkspaceSleepState.SLEEPING, runBlocking { controller.sleepWorkspace(ws.id, listOf(session)) })
        assertTrue(session.isInputGated)

        ops.procBlocked = true
        val stateAfterWake = runBlocking { controller.wakeWorkspace(ws.id, listOf(session)) }
        assertEquals(WorkspaceSleepState.ERROR, stateAfterWake)
        assertEquals(WorkspaceSleepState.ERROR, WorkspaceManager.getSleepState(ws.id))
        assertTrue("Session must remain gated when wake fails", session.isInputGated)
    }

    @Test
    fun closingLastSessionOfSleepingWorkspaceResetsSleepStateToAwake() {
        val ops = FakeProcessOps(
            listOf(
                FakeProcess(pid = 940, comm = "bash", state = 'S', ppid = 1, pgid = 940, sid = 940, uid = 10912, startTimeTicks = 5000L),
                FakeProcess(pid = 941, comm = "bash", state = 'S', ppid = 1, pgid = 941, sid = 941, uid = 10912, startTimeTicks = 5001L)
            )
        )
        val ws = WorkspaceManager.createWorkspace("LastClose", "/tmp/lastclose")
        val s1 = newTestSession(940)
        val s2 = newTestSession(941)
        val controller = WorkspaceHibernationController(context, ops, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)

        assertEquals(WorkspaceSleepState.SLEEPING, runBlocking { controller.sleepWorkspace(ws.id, listOf(s1, s2)) })
        assertTrue(runBlocking { controller.terminateSingleSession(ws.id, s1) })
        assertEquals(
            "Closing first of two sessions must leave workspace SLEEPING",
            WorkspaceSleepState.SLEEPING,
            WorkspaceManager.getSleepState(ws.id)
        )

        assertTrue(runBlocking { controller.terminateSingleSession(ws.id, s2) })
        assertEquals(
            "Closing last session must reset SLEEPING workspace to AWAKE",
            WorkspaceSleepState.AWAKE,
            WorkspaceManager.getSleepState(ws.id)
        )
        assertFalse(controller.hasRecordsForWorkspace(ws.id))
    }

    @Test
    fun closingDeadShellStillTerminatesPersistedChildProcessesInSid() {
        val ops = FakeProcessOps(
            listOf(
                FakeProcess(pid = 950, comm = "bash", state = 'S', ppid = 1, pgid = 950, sid = 950, uid = 10912, startTimeTicks = 6000L),
                FakeProcess(pid = 951, comm = "node", state = 'S', ppid = 950, pgid = 951, sid = 950, uid = 10912, startTimeTicks = 6005L)
            )
        )
        val ws = WorkspaceManager.createWorkspace("DeadShell", "/tmp/deadshell")
        val session = newTestSession(950)
        val controller = WorkspaceHibernationController(context, ops, ownerUidProvider = { 10912 }, terminationTimeoutMs = 100L)

        assertEquals(WorkspaceSleepState.SLEEPING, runBlocking { controller.sleepWorkspace(ws.id, listOf(session)) })

        // Simulate shell exiting so session.pid drops to 0 while child 951 remains in SID 950.
        ops.processes.remove(950)
        val pidField = TerminalSession::class.java.getDeclaredField("mShellPid")
        pidField.isAccessible = true
        pidField.setInt(session, 0)

        assertTrue(runBlocking { controller.terminateSingleSession(ws.id, session) })
        assertFalse("Leaked child process in SID 950 must be terminated even when shell pid <= 0", ops.processes.containsKey(951))
        assertEquals(WorkspaceSleepState.AWAKE, WorkspaceManager.getSleepState(ws.id))
        assertFalse(controller.hasRecordsForWorkspace(ws.id))
    }
}
