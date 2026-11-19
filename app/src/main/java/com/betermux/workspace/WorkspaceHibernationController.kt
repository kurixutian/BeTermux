package com.betermux.workspace

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import com.betermux.features.BeTermuxSettings
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.Dispatchers
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
                return@withLock WorkspaceManager.getSleepState(workspaceId)
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
            var failureReason: String? = null

            withContext(Dispatchers.IO) {
                for (session in liveSessions) {
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
                        }
                        is SuspendResult.Failed -> {
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
                    replaceRecordsForWorkspace(workspaceId, suspendedRecords)
                }
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

            val allResumed = withContext(Dispatchers.IO) {
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
            val ownerUid = ownerUidProvider()
            session.setInputGated(true)
            val persisted = loadRecordsForWorkspace(workspaceId)
                .firstOrNull { it.sessionHandle == session.mHandle }

            withContext(Dispatchers.IO) {
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
                        // Process already gone.
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
        }
    }

    suspend fun reconcileColdStartLeakedProcesses(ownerUid: Int) {
        val allRecords = loadAllRecords()
        if (allRecords.isEmpty()) return

        for (record in allRecords) {
            mutexFor(record.workspaceId).withLock {
                withContext(Dispatchers.IO) {
                    if (isVerifiedLeakedStoppedSession(record, ownerUid)) {
                        ProcSessionController.terminateSession(
                            recordedPids = record.pids,
                            sid = record.sid,
                            ownerUid = ownerUid,
                            timeoutMs = terminationTimeoutMs,
                            ops = ops
                        )
                    }
                    removeRecordByHandle(record.workspaceId, record.sessionHandle)
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
    }
}
