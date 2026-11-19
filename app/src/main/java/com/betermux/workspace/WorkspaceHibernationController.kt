package com.betermux.workspace

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import com.betermux.features.BeTermuxSettings
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Coordinates D3 session-boundary input gating, D1 SID-based process hibernation,
 * D2 bounded process-tree termination, and cold-start leaked `'T'` process cleanup.
 */
class WorkspaceHibernationController(
    context: Context,
    private val ops: ProcessOps = OsProcessOps,
    private val ownerUidProvider: () -> Int = { Process.myUid() },
    private val terminationTimeoutMs: Long = 5000L
) {
    private val appContext: Context = context.applicationContext ?: context
    private val mutexMapLock = Any()
    private val workspaceMutexes = HashMap<String, Mutex>()
    private val prefsLock = Any()

    internal data class ManagedSidRecord(
        val workspaceId: String,
        val sessionHandle: String,
        val shellPid: Int,
        val sid: Int,
        val pids: List<Int>,
        val shellStartTimeTicks: Long,
        val pidStartTimeTicks: Map<Int, Long> = emptyMap()
    )

    private fun mutexFor(workspaceId: String): Mutex {
        synchronized(mutexMapLock) {
            return workspaceMutexes.getOrPut(workspaceId) { Mutex() }
        }
    }

    suspend fun sleepWorkspace(
        workspaceId: String,
        sessions: List<TerminalSession>
    ): WorkspaceSleepState {
        return mutexFor(workspaceId).withLock {
            if (!BeTermuxSettings.isWorkspaceHibernationEnabled(appContext)) {
                sessions.forEach { it.setInputGated(false) }
                withContext(Dispatchers.IO) {
                    removeRecordsForWorkspace(workspaceId)
                }
                WorkspaceManager.setSleepState(workspaceId, WorkspaceSleepState.AWAKE)
                return@withLock WorkspaceSleepState.AWAKE
            }
            if (WorkspaceManager.getSleepState(workspaceId) == WorkspaceSleepState.SLEEPING) {
                return@withLock WorkspaceSleepState.SLEEPING
            }

            val ownerUid = ownerUidProvider()
            val liveSessions = sessions.filter { it.pid > 1 }

            WorkspaceManager.setSleepState(workspaceId, WorkspaceSleepState.SUSPENDING)

            // Gate all session inputs BEFORE sending SIGSTOP (D3).
            liveSessions.forEach { it.setInputGated(true) }

            val suspendedRecords = ArrayList<ManagedSidRecord>(liveSessions.size)
            var currentAttemptSession: TerminalSession? = null
            var failureReason: String? = null

            try {
                withContext(Dispatchers.IO) {
                    for (session in liveSessions) {
                        currentCoroutineContext().ensureActive()
                        currentAttemptSession = session
                        val shellPid = session.pid
                        when (val res = ProcSessionController.suspendSession(shellPid, ownerUid, ops)) {
                            is SuspendResult.Success -> {
                                suspendedRecords.add(
                                    ManagedSidRecord(
                                        workspaceId = workspaceId,
                                        sessionHandle = session.mHandle,
                                        shellPid = shellPid,
                                        sid = res.sid,
                                        pids = res.pids,
                                        shellStartTimeTicks = res.shellStartTimeTicks,
                                        pidStartTimeTicks = res.pidStartTimeTicks
                                    )
                                )
                                currentAttemptSession = null
                            }
                            is SuspendResult.Failed -> {
                                currentAttemptSession = null
                                failureReason = res.reason
                                break
                            }
                        }
                    }

                    if (failureReason != null) {
                        // Fail closed: resume any already-stopped sessions and drop persisted records.
                        for (rec in suspendedRecords) {
                            ProcSessionController.resumeSession(rec.pids, rec.sid, ownerUid, ops)
                        }
                        removeRecordsForWorkspace(workspaceId)
                    } else {
                        currentCoroutineContext().ensureActive()
                        replaceRecordsForWorkspace(workspaceId, suspendedRecords)
                    }
                }
            } catch (t: Throwable) {
                val fallbackState = if (t is CancellationException) {
                    WorkspaceSleepState.AWAKE
                } else {
                    WorkspaceSleepState.ERROR
                }
                withContext(NonCancellable + Dispatchers.IO) {
                    for (rec in suspendedRecords) {
                        ProcSessionController.resumeSession(rec.pids, rec.sid, ownerUid, ops)
                    }
                    val inFlight = currentAttemptSession
                    if (inFlight != null && inFlight.pid > 1) {
                        val pid = inFlight.pid
                        val uid = ops.readProcUid(pid)
                        val stat = ops.readProcStat(pid)
                        val info = if (uid == ownerUid && stat != null) {
                            ProcSessionController.parseProcStat(pid, stat, uid)
                        } else {
                            null
                        }
                        if (info != null && info.sid > 0 && info.pgid > 0) {
                            ProcSessionController.resumeSession(listOf(pid), info.sid, ownerUid, ops)
                        }
                    }
                    removeRecordsForWorkspace(workspaceId)
                    liveSessions.forEach { it.setInputGated(false) }
                    WorkspaceManager.setSleepState(workspaceId, fallbackState)
                }
                throw t
            }

            if (failureReason != null) {
                liveSessions.forEach { it.setInputGated(false) }
                WorkspaceManager.setSleepState(workspaceId, WorkspaceSleepState.ERROR)
                return@withLock WorkspaceSleepState.ERROR
            }

            WorkspaceManager.setSleepState(workspaceId, WorkspaceSleepState.SLEEPING)
            WorkspaceSleepState.SLEEPING
        }
    }

    suspend fun wakeWorkspace(
        workspaceId: String,
        sessions: List<TerminalSession>
    ): WorkspaceSleepState {
        return mutexFor(workspaceId).withLock {
            if (!BeTermuxSettings.isWorkspaceHibernationEnabled(appContext)) {
                withContext(Dispatchers.IO) {
                    removeRecordsForWorkspace(workspaceId)
                }
                sessions.forEach { it.setInputGated(false) }
                WorkspaceManager.setSleepState(workspaceId, WorkspaceSleepState.AWAKE)
                return@withLock WorkspaceSleepState.AWAKE
            }

            val currentState = WorkspaceManager.getSleepState(workspaceId)
            val persistedRecords = loadRecordsForWorkspace(workspaceId)
            if (currentState == WorkspaceSleepState.AWAKE &&
                persistedRecords.isEmpty() &&
                sessions.none { it.isInputGated }
            ) {
                return@withLock WorkspaceSleepState.AWAKE
            }

            val ownerUid = ownerUidProvider()
            WorkspaceManager.setSleepState(workspaceId, WorkspaceSleepState.RESUMING)

            val allResumed = try {
                withContext(Dispatchers.IO) {
                    val sidToPids = LinkedHashMap<Int, List<Int>>()
                    for (rec in persistedRecords) {
                        sidToPids[rec.sid] = rec.pids
                    }
                    for (session in sessions) {
                        val pid = session.pid
                        if (pid <= 1) continue
                        val uid = ops.readProcUid(pid) ?: continue
                        if (uid != ownerUid) continue
                        val stat = ops.readProcStat(pid) ?: continue
                        val info = ProcSessionController.parseProcStat(pid, stat, uid) ?: continue
                        if (info.sid > 0 && info.pgid > 0 && info.sid !in sidToPids) {
                            sidToPids[info.sid] = listOf(pid)
                        }
                    }

                    // Union any process forked into a recorded SID after sleep so it is
                    // resumed too instead of staying SIGSTOPped (ghost process).
                    // findSessionProcesses already filters uid==ownerUid, sid==target sid,
                    // pgid>0, pid>1, so every returned pid is safe to SIGCONT.
                    for (sid in sidToPids.keys.toList()) {
                        val enumRes = ProcSessionController.findSessionProcesses(sid, ownerUid, ops)
                        if (enumRes is ProcEnumerationResult.Success) {
                            val current = sidToPids[sid] ?: continue
                            val extra = enumRes.processes.map { it.pid }.filter { pid -> pid !in current }
                            sidToPids[sid] = current + extra
                        }
                    }

                    var ok = true
                    for ((sid, pids) in sidToPids) {
                        val res = ProcSessionController.resumeSession(pids, sid, ownerUid, ops)
                        if (res is ResumeResult.Failed) {
                            ok = false
                        }
                    }
                    if (ok) {
                        removeRecordsForWorkspace(workspaceId)
                    }
                    ok
                }
            } catch (t: Throwable) {
                WorkspaceManager.setSleepState(
                    workspaceId,
                    if (t is CancellationException) currentState else WorkspaceSleepState.ERROR
                )
                throw t
            }

            if (allResumed) {
                // Un-gate input ONLY AFTER SIGCONT succeeds.
                sessions.forEach { it.setInputGated(false) }
                WorkspaceManager.setSleepState(workspaceId, WorkspaceSleepState.AWAKE)
                WorkspaceSleepState.AWAKE
            } else {
                WorkspaceManager.setSleepState(workspaceId, WorkspaceSleepState.ERROR)
                WorkspaceSleepState.ERROR
            }
        }
    }

    suspend fun terminateWorkspaceSessions(
        workspaceId: String,
        sessions: List<TerminalSession>
    ): Boolean {
        return mutexFor(workspaceId).withLock {
            if (!BeTermuxSettings.isWorkspaceHibernationEnabled(appContext)) {
                sessions.forEach {
                    it.setInputGated(false)
                    it.finishIfRunning()
                }
                withContext(Dispatchers.IO) {
                    removeRecordsForWorkspace(workspaceId)
                }
                WorkspaceManager.setSleepState(workspaceId, WorkspaceSleepState.AWAKE)
                return@withLock true
            }

            val ownerUid = ownerUidProvider()
            val persistedRecords = loadRecordsForWorkspace(workspaceId)
            sessions.forEach { it.setInputGated(true) }

            val allTerminated = withContext(Dispatchers.IO) {
                val sidToPids = LinkedHashMap<Int, MutableList<Int>>()
                for (rec in persistedRecords) {
                    sidToPids.getOrPut(rec.sid) { ArrayList() }.addAll(rec.pids)
                }
                for (session in sessions) {
                    val pid = session.pid
                    if (pid <= 1) continue
                    val uid = ops.readProcUid(pid) ?: continue
                    if (uid != ownerUid) continue
                    val stat = ops.readProcStat(pid) ?: continue
                    val info = ProcSessionController.parseProcStat(pid, stat, uid) ?: continue
                    if (info.sid > 0 && info.pgid > 0) {
                        sidToPids.getOrPut(info.sid) { ArrayList() }.add(pid)
                    }
                }

                // Union any process forked into a recorded SID after sleep so it is
                // terminated too instead of leaking as a stopped ghost process.
                // findSessionProcesses already filters uid==ownerUid, sid==target sid,
                // pgid>0, pid>1, so every returned pid is safe to SIGTERM/SIGKILL.
                for (sid in sidToPids.keys.toList()) {
                    val enumRes = ProcSessionController.findSessionProcesses(sid, ownerUid, ops)
                    if (enumRes is ProcEnumerationResult.Success) {
                        val current = sidToPids[sid] ?: continue
                        val extra = enumRes.processes.map { it.pid }.filter { pid -> pid !in current }
                        current.addAll(extra)
                    }
                }

                var ok = true
                for ((sid, pids) in sidToPids) {
                    val res = ProcSessionController.terminateSession(
                        recordedPids = pids.distinct(),
                        sid = sid,
                        ownerUid = ownerUid,
                        timeoutMs = terminationTimeoutMs,
                        ops = ops
                    )
                    if (res is TerminateResult.Failed) {
                        ok = false
                    }
                }
                if (ok) {
                    removeRecordsForWorkspace(workspaceId)
                }
                ok
            }

            if (allTerminated) {
                WorkspaceManager.setSleepState(workspaceId, WorkspaceSleepState.AWAKE)
            } else {
                WorkspaceManager.setSleepState(workspaceId, WorkspaceSleepState.ERROR)
            }
            allTerminated
        }
    }

    suspend fun terminateSingleSession(
        workspaceId: String,
        session: TerminalSession
    ): Boolean {
        return mutexFor(workspaceId).withLock {
            if (!BeTermuxSettings.isWorkspaceHibernationEnabled(appContext)) {
                session.setInputGated(false)
                session.finishIfRunning()
                withContext(Dispatchers.IO) {
                    removeRecordByHandle(workspaceId, session.mHandle)
                }
                if (WorkspaceManager.getSleepState(workspaceId) != WorkspaceSleepState.ERROR) {
                    WorkspaceManager.setSleepState(workspaceId, WorkspaceSleepState.AWAKE)
                }
                return@withLock true
            }

            val priorState = WorkspaceManager.getSleepState(workspaceId)
            val ownerUid = ownerUidProvider()
            session.setInputGated(true)
            val persisted = loadRecordsForWorkspace(workspaceId)
                .firstOrNull { it.sessionHandle == session.mHandle }

            val terminated = withContext(Dispatchers.IO) {
                val pid = session.pid
                val targetSid: Int
                val targetPids: List<Int>

                if (persisted != null) {
                    targetSid = persisted.sid
                    targetPids = persisted.pids
                } else if (pid > 1) {
                    val uid = ops.readProcUid(pid)
                    val stat = ops.readProcStat(pid)
                    if (uid == null || stat == null) {
                        // Process already gone (only if /proc itself isn't blocked).
                        if (ops.listProcPids() == null) {
                            return@withContext false
                        }
                        removeRecordByHandle(workspaceId, session.mHandle)
                        return@withContext true
                    }
                    if (uid != ownerUid) return@withContext false
                    val info = ProcSessionController.parseProcStat(pid, stat, uid)
                        ?: return@withContext false
                    if (info.sid <= 0 || info.pgid <= 0) return@withContext false
                    targetSid = info.sid
                    targetPids = listOf(pid)
                } else {
                    removeRecordByHandle(workspaceId, session.mHandle)
                    return@withContext true
                }

                val res = ProcSessionController.terminateSession(
                    recordedPids = targetPids,
                    sid = targetSid,
                    ownerUid = ownerUid,
                    timeoutMs = terminationTimeoutMs,
                    ops = ops
                )
                if (res is TerminateResult.Success) {
                    removeRecordByHandle(workspaceId, session.mHandle)
                    true
                } else {
                    false
                }
            }

            if (terminated) {
                val noRecordsRemain = withContext(Dispatchers.IO) {
                    loadRecordsForWorkspace(workspaceId).isEmpty()
                }
                if (noRecordsRemain &&
                    WorkspaceManager.getSleepState(workspaceId) != WorkspaceSleepState.ERROR
                ) {
                    WorkspaceManager.setSleepState(workspaceId, WorkspaceSleepState.AWAKE)
                }
            } else {
                if (priorState != WorkspaceSleepState.SLEEPING) {
                    session.setInputGated(false)
                }
                WorkspaceManager.setSleepState(workspaceId, WorkspaceSleepState.ERROR)
            }
            terminated
        }
    }

    suspend fun reconcileColdStartLeakedProcesses(ownerUid: Int) {
        if (!BeTermuxSettings.isWorkspaceHibernationEnabled(appContext)) return

        val allRecords = loadAllRecords()
        if (allRecords.isEmpty()) return

        for (record in allRecords) {
            mutexFor(record.workspaceId).withLock {
                withContext(Dispatchers.IO) {
                    if (isVerifiedLeakedStoppedSession(record, ownerUid)) {
                        val res = ProcSessionController.terminateSession(
                            recordedPids = record.pids,
                            sid = record.sid,
                            ownerUid = ownerUid,
                            timeoutMs = terminationTimeoutMs,
                            ops = ops
                        )
                        if (res is TerminateResult.Success) {
                            removeRecordByHandle(record.workspaceId, record.sessionHandle)
                        } else {
                            WorkspaceManager.setSleepState(record.workspaceId, WorkspaceSleepState.ERROR)
                        }
                    } else {
                        removeRecordByHandle(record.workspaceId, record.sessionHandle)
                    }
                }
            }
        }
    }

    private fun isVerifiedLeakedStoppedSession(record: ManagedSidRecord, ownerUid: Int): Boolean {
        val shellPid = if (record.shellPid > 1) record.shellPid else record.sid
        if (shellPid > 1 && record.shellStartTimeTicks > 0L) {
            val uid = ops.readProcUid(shellPid)
            val rawStat = ops.readProcStat(shellPid)
            if (uid == ownerUid && rawStat != null) {
                val info = ProcSessionController.parseProcStat(shellPid, rawStat, uid)
                if (info != null &&
                    info.sid == record.sid &&
                    info.pgid > 0 &&
                    info.startTimeTicks == record.shellStartTimeTicks &&
                    info.state == 'T'
                ) {
                    return true
                }
            }
        }

        // Fallback if shell exited but recorded children remain in 'T' with exact startTimeTicks.
        for ((pid, expectedStartTicks) in record.pidStartTimeTicks) {
            if (pid <= 1 || expectedStartTicks <= 0L) continue
            val uid = ops.readProcUid(pid) ?: continue
            if (uid != ownerUid) continue
            val rawStat = ops.readProcStat(pid) ?: continue
            val info = ProcSessionController.parseProcStat(pid, rawStat, uid) ?: continue
            if (info.sid == record.sid &&
                info.pgid > 0 &&
                info.startTimeTicks == expectedStartTicks &&
                info.state == 'T'
            ) {
                return true
            }
        }
        return false
    }

    private fun prefs(): SharedPreferences {
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun loadAllRecords(): List<ManagedSidRecord> {
        synchronized(prefsLock) {
            val raw = prefs().getString(KEY_MANAGED_SIDS_JSON, null) ?: return emptyList()
            if (raw.isBlank()) return emptyList()
            return try {
                val array = JSONArray(raw)
                val out = ArrayList<ManagedSidRecord>(array.length())
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val workspaceId = obj.optString("workspaceId", "").trim()
                    val sessionHandle = obj.optString("sessionHandle", "").trim()
                    val sid = obj.optInt("sid", -1)
                    val shellPid = obj.optInt("shellPid", sid)
                    val shellStartTimeTicks = obj.optLong("shellStartTimeTicks", -1L)
                    if (workspaceId.isEmpty() || sid <= 0 || shellStartTimeTicks <= 0L) continue

                    val pidsArray = obj.optJSONArray("pids")
                    val pids = ArrayList<Int>()
                    if (pidsArray != null) {
                        for (j in 0 until pidsArray.length()) {
                            val p = pidsArray.optInt(j, -1)
                            if (p > 1) pids.add(p)
                        }
                    }

                    val pidStartTimes = HashMap<Int, Long>()
                    val startTimesObj = obj.optJSONObject("pidStartTimeTicks")
                    if (startTimesObj != null) {
                        val keys = startTimesObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val pidKey = key.toIntOrNull() ?: continue
                            val ticks = startTimesObj.optLong(key, -1L)
                            if (pidKey > 1 && ticks > 0L) {
                                pidStartTimes[pidKey] = ticks
                            }
                        }
                    }

                    out.add(
                        ManagedSidRecord(
                            workspaceId = workspaceId,
                            sessionHandle = sessionHandle,
                            shellPid = shellPid,
                            sid = sid,
                            pids = pids,
                            shellStartTimeTicks = shellStartTimeTicks,
                            pidStartTimeTicks = pidStartTimes
                        )
                    )
                }
                out
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    internal fun hasRecordsForWorkspace(workspaceId: String): Boolean {
        return loadRecordsForWorkspace(workspaceId).isNotEmpty()
    }

    internal fun hasAnyRecords(): Boolean {
        return loadAllRecords().isNotEmpty()
    }

    internal fun recordedWorkspaceIds(): List<String> {
        return loadAllRecords().map { it.workspaceId }.distinct()
    }

    internal fun clearAllRecordsForRollback() {
        synchronized(prefsLock) {
            saveAllRecordsLocked(emptyList())
        }
    }

    private fun loadRecordsForWorkspace(workspaceId: String): List<ManagedSidRecord> {
        return loadAllRecords().filter { it.workspaceId == workspaceId }
    }

    private fun replaceRecordsForWorkspace(workspaceId: String, newRecords: List<ManagedSidRecord>) {
        synchronized(prefsLock) {
            val remaining = loadAllRecords().filterNot { it.workspaceId == workspaceId }
            saveAllRecordsLocked(remaining + newRecords)
        }
    }

    private fun removeRecordsForWorkspace(workspaceId: String) {
        synchronized(prefsLock) {
            val remaining = loadAllRecords().filterNot { it.workspaceId == workspaceId }
            saveAllRecordsLocked(remaining)
        }
    }

    private fun removeRecordByHandle(workspaceId: String, sessionHandle: String) {
        synchronized(prefsLock) {
            val remaining = loadAllRecords().filterNot {
                it.workspaceId == workspaceId && it.sessionHandle == sessionHandle
            }
            saveAllRecordsLocked(remaining)
        }
    }

    private fun saveAllRecordsLocked(records: List<ManagedSidRecord>) {
        if (records.isEmpty()) {
            prefs().edit().remove(KEY_MANAGED_SIDS_JSON).commit()
            return
        }
        val array = JSONArray()
        for (rec in records) {
            val obj = JSONObject()
            obj.put("workspaceId", rec.workspaceId)
            obj.put("sessionHandle", rec.sessionHandle)
            obj.put("shellPid", rec.shellPid)
            obj.put("sid", rec.sid)
            obj.put("shellStartTimeTicks", rec.shellStartTimeTicks)
            val pidsArray = JSONArray()
            for (p in rec.pids) {
                pidsArray.put(p)
            }
            obj.put("pids", pidsArray)
            val startTimesObj = JSONObject()
            for ((pidKey, ticks) in rec.pidStartTimeTicks) {
                startTimesObj.put(pidKey.toString(), ticks)
            }
            obj.put("pidStartTimeTicks", startTimesObj)
            array.put(obj)
        }
        prefs().edit().putString(KEY_MANAGED_SIDS_JSON, array.toString()).commit()
    }

    companion object {
        private const val PREFS_NAME = "betermux_hibernation_sids"
        private const val KEY_MANAGED_SIDS_JSON = "managed_sids_json"

        @Volatile
        private var sharedInstance: WorkspaceHibernationController? = null

        @JvmStatic
        fun getInstance(context: Context): WorkspaceHibernationController {
            val existing = sharedInstance
            if (existing != null) return existing
            return synchronized(this) {
                sharedInstance ?: WorkspaceHibernationController(context.applicationContext ?: context).also {
                    sharedInstance = it
                }
            }
        }
    }
}
