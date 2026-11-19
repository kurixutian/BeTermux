package com.betermux.workspace

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File

enum class SignalStatus {
    OK,
    ESRCH,
    EPERM,
    ERROR
}

interface ProcessOps {
    fun listProcPids(): List<Int>?
    fun readProcStat(pid: Int): String?
    fun readProcUid(pid: Int): Int?
    fun sendSignal(pid: Int, signal: Int): SignalStatus
    fun sleepMs(millis: Long) {
        Thread.sleep(millis)
    }
    fun nowMs(): Long {
        return System.nanoTime() / 1_000_000L
    }
}

object OsProcessOps : ProcessOps {
    private val WHITESPACE_REGEX = Regex("\\s+")

    override fun listProcPids(): List<Int>? {
        return try {
            val entries = File("/proc").listFiles() ?: return null
            val pids = ArrayList<Int>(entries.size)
            for (entry in entries) {
                val name = entry.name
                if (name.isEmpty() || !name.all { it in '0'..'9' }) continue
                val pid = name.toIntOrNull() ?: continue
                if (pid > 0) pids.add(pid)
            }
            pids
        } catch (_: Throwable) {
            null
        }
    }

    override fun readProcStat(pid: Int): String? {
        if (pid <= 0) return null
        return try {
            File("/proc/$pid/stat").bufferedReader().use { it.readLine() }
        } catch (_: Throwable) {
            null
        }
    }

    override fun readProcUid(pid: Int): Int? {
        if (pid <= 0) return null
        return try {
            File("/proc/$pid/status").bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("Uid:")) {
                        val rest = line.substringAfter(':').trim()
                        if (rest.isEmpty()) return@useLines null
                        return@useLines rest.split(WHITESPACE_REGEX).firstOrNull()?.toIntOrNull()
                    }
                }
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    override fun sendSignal(pid: Int, signal: Int): SignalStatus {
        if (pid <= 1) return SignalStatus.ERROR
        return try {
            Os.kill(pid, signal)
            SignalStatus.OK
        } catch (e: ErrnoException) {
            when (e.errno) {
                OsConstants.ESRCH -> SignalStatus.ESRCH
                OsConstants.EPERM -> SignalStatus.EPERM
                else -> SignalStatus.ERROR
            }
        } catch (_: Throwable) {
            SignalStatus.ERROR
        }
    }
}

data class ProcStatInfo(
    val pid: Int,
    val comm: String,
    val state: Char,
    val ppid: Int,
    val pgid: Int,
    val sid: Int,
    val uid: Int,
    val startTimeTicks: Long
)

sealed class ProcEnumerationResult {
    data class Success(val processes: List<ProcStatInfo>) : ProcEnumerationResult()
    data class Blocked(val reason: String) : ProcEnumerationResult()
}

sealed class SuspendResult {
    data class Success(
        val sid: Int,
        val pids: List<Int>,
        val shellStartTimeTicks: Long,
        val pidStartTimeTicks: Map<Int, Long>
    ) : SuspendResult()

    data class Failed(val reason: String) : SuspendResult()
}

sealed class ResumeResult {
    data class Success(val resumedPids: List<Int>) : ResumeResult()
    data class Failed(val reason: String) : ResumeResult()
}

sealed class TerminateResult {
    data class Success(val terminatedPids: List<Int>, val killedPids: List<Int>) : TerminateResult()
    data class Failed(val reason: String) : TerminateResult()
}

/**
 * SID-based process enumeration, suspension (D1), resumption, and bounded termination (D2).
 *
 * Uses `/proc/<pid>/stat` and `/proc/<pid>/status` so it works on all API levels (21+)
 * without requiring `Os.getsid` (API 29+).
 */
object ProcSessionController {
    private val WHITESPACE_REGEX = Regex("\\s+")
    private const val DEFAULT_STOP_VERIFY_TIMEOUT_MS = 400L
    private const val STOP_POLL_INTERVAL_MS = 20L
    private const val TERMINATE_POLL_INTERVAL_MS = 50L
    private const val KILL_VERIFY_TIMEOUT_MS = 500L

    fun parseProcStat(pid: Int, rawStat: String, uid: Int): ProcStatInfo? {
        if (pid <= 0 || uid < 0) return null
        val firstParen = rawStat.indexOf('(')
        val lastParen = rawStat.lastIndexOf(')')
        if (firstParen <= 0 || lastParen <= firstParen) return null

        val parsedPid = rawStat.substring(0, firstParen).trim().toIntOrNull() ?: return null
        if (parsedPid != pid) return null

        val comm = rawStat.substring(firstParen + 1, lastParen)
        val after = rawStat.substring(lastParen + 1).trim()
        if (after.isEmpty()) return null

        val tokens = after.split(WHITESPACE_REGEX)
        // Token 0 is field 3 (state); token 19 is field 22 (starttime).
        if (tokens.size < 20) return null

        val stateToken = tokens[0]
        if (stateToken.length != 1) return null

        val state = stateToken[0]
        val ppid = tokens[1].toIntOrNull() ?: return null
        val pgid = tokens[2].toIntOrNull() ?: return null
        val sid = tokens[3].toIntOrNull() ?: return null
        val startTimeTicks = tokens[19].toLongOrNull() ?: return null

        return ProcStatInfo(
            pid = pid,
            comm = comm,
            state = state,
            ppid = ppid,
            pgid = pgid,
            sid = sid,
            uid = uid,
            startTimeTicks = startTimeTicks
        )
    }

    private fun findSessionProcessesInternal(
        sid: Int,
        ownerUid: Int,
        ops: ProcessOps,
        treatEmptyAsBlocked: Boolean
    ): ProcEnumerationResult {
        if (sid <= 0 || ownerUid < 0) {
            return ProcEnumerationResult.Blocked("Invalid sid=$sid or ownerUid=$ownerUid")
        }
        val allPids = ops.listProcPids()
            ?: return ProcEnumerationResult.Blocked("/proc enumeration returned null")
        if (allPids.isEmpty()) {
            return if (treatEmptyAsBlocked) {
                ProcEnumerationResult.Blocked("/proc enumeration returned empty")
            } else {
                ProcEnumerationResult.Success(emptyList())
            }
        }

        val matches = ArrayList<ProcStatInfo>()
        for (pid in allPids) {
            if (pid <= 1) continue
            val statRaw = ops.readProcStat(pid) ?: continue
            val uid = ops.readProcUid(pid) ?: continue
            if (uid != ownerUid) continue
            val info = parseProcStat(pid, statRaw, uid) ?: continue
            if (info.pgid <= 0 || info.sid <= 0) continue
            if (info.sid == sid) {
                matches.add(info)
            }
        }
        return ProcEnumerationResult.Success(matches)
    }

    fun findSessionProcesses(
        sid: Int,
        ownerUid: Int,
        ops: ProcessOps = OsProcessOps
    ): ProcEnumerationResult {
        return findSessionProcessesInternal(sid, ownerUid, ops, treatEmptyAsBlocked = true)
    }

    fun suspendSession(
        shellPid: Int,
        ownerUid: Int,
        ops: ProcessOps = OsProcessOps,
        verifyTimeoutMs: Long = DEFAULT_STOP_VERIFY_TIMEOUT_MS
    ): SuspendResult {
        if (shellPid <= 1 || ownerUid < 0) {
            return SuspendResult.Failed("Invalid shellPid=$shellPid or ownerUid=$ownerUid")
        }

        val shellRawStat = ops.readProcStat(shellPid)
            ?: return SuspendResult.Failed("Unable to read /proc/$shellPid/stat")
        val shellUid = ops.readProcUid(shellPid)
            ?: return SuspendResult.Failed("Unable to read /proc/$shellPid/status")
        if (shellUid != ownerUid) {
            return SuspendResult.Failed("UID mismatch for shellPid=$shellPid: expected=$ownerUid actual=$shellUid")
        }

        val shellInfo = parseProcStat(shellPid, shellRawStat, shellUid)
            ?: return SuspendResult.Failed("Malformed stat for shellPid=$shellPid")
        if (shellInfo.sid <= 0 || shellInfo.pgid <= 0) {
            return SuspendResult.Failed("Invalid sid=${shellInfo.sid} or pgid=${shellInfo.pgid}")
        }

        val initialEnum = findSessionProcesses(shellInfo.sid, ownerUid, ops)
        if (initialEnum !is ProcEnumerationResult.Success) {
            return SuspendResult.Failed((initialEnum as ProcEnumerationResult.Blocked).reason)
        }
        val initialTargets = initialEnum.processes.filter {
            it.pid > 1 && it.pgid > 0 && it.sid == shellInfo.sid && it.uid == ownerUid
        }
        if (initialTargets.none { it.pid == shellPid }) {
            return SuspendResult.Failed("Shell pid=$shellPid absent from SID=${shellInfo.sid} enumeration")
        }

        val stoppedByUs = LinkedHashSet<Int>()
        // Stop non-shell descendants first, then the session leader shell, to avoid shell job-control races.
        val orderedTargets = initialTargets.sortedBy { if (it.pid == shellPid) 1 else 0 }
        for (proc in orderedTargets) {
            when (ops.sendSignal(proc.pid, OsConstants.SIGSTOP)) {
                SignalStatus.OK -> stoppedByUs.add(proc.pid)
                SignalStatus.ESRCH -> {
                    if (proc.pid == shellPid) {
                        rollbackStopped(stoppedByUs, shellInfo.sid, ownerUid, ops)
                        return SuspendResult.Failed("Shell pid=$shellPid vanished during SIGSTOP")
                    }
                }
                SignalStatus.EPERM, SignalStatus.ERROR -> {
                    rollbackStopped(stoppedByUs, shellInfo.sid, ownerUid, ops)
                    return SuspendResult.Failed("SIGSTOP failed for pid=${proc.pid}")
                }
            }
        }

        val deadline = ops.nowMs() + verifyTimeoutMs.coerceAtLeast(0L)
        while (true) {
            val checkEnum = findSessionProcesses(shellInfo.sid, ownerUid, ops)
            if (checkEnum !is ProcEnumerationResult.Success) {
                rollbackStopped(stoppedByUs, shellInfo.sid, ownerUid, ops)
                return SuspendResult.Failed((checkEnum as ProcEnumerationResult.Blocked).reason)
            }

            val live = checkEnum.processes.filter {
                it.pid > 1 && it.pgid > 0 && it.sid == shellInfo.sid && it.uid == ownerUid
            }
            val liveShell = live.firstOrNull { it.pid == shellPid }
            if (liveShell == null || liveShell.state == 'Z' || liveShell.state == 'X') {
                rollbackStopped(stoppedByUs, shellInfo.sid, ownerUid, ops)
                return SuspendResult.Failed("Shell pid=$shellPid exited or became zombie during suspend")
            }

            // Catch any child forked right before its parent received SIGSTOP.
            var sentNewStop = false
            for (proc in live) {
                if (proc.pid !in stoppedByUs && proc.state != 'T') {
                    when (ops.sendSignal(proc.pid, OsConstants.SIGSTOP)) {
                        SignalStatus.OK -> {
                            stoppedByUs.add(proc.pid)
                            sentNewStop = true
                        }
                        SignalStatus.ESRCH -> Unit
                        SignalStatus.EPERM, SignalStatus.ERROR -> {
                            rollbackStopped(stoppedByUs, shellInfo.sid, ownerUid, ops)
                            return SuspendResult.Failed("SIGSTOP failed for newly spawned pid=${proc.pid}")
                        }
                    }
                }
            }

            if (!sentNewStop && live.all { it.state == 'T' }) {
                return SuspendResult.Success(
                    sid = shellInfo.sid,
                    pids = live.map { it.pid },
                    shellStartTimeTicks = shellInfo.startTimeTicks,
                    pidStartTimeTicks = live.associate { it.pid to it.startTimeTicks }
                )
            }

            if (ops.nowMs() >= deadline) {
                rollbackStopped(stoppedByUs, shellInfo.sid, ownerUid, ops)
                val nonStopped = live.filter { it.state != 'T' }.map { "${it.pid}:${it.state}" }
                return SuspendResult.Failed("Processes in sid=${shellInfo.sid} failed to reach T state: $nonStopped")
            }
            ops.sleepMs(STOP_POLL_INTERVAL_MS)
        }
    }

    fun resumeSession(
        recordedPids: List<Int>,
        sid: Int,
        ownerUid: Int,
        ops: ProcessOps = OsProcessOps
    ): ResumeResult {
        if (sid <= 0 || ownerUid < 0) {
            return ResumeResult.Failed("Invalid sid=$sid or ownerUid=$ownerUid")
        }
        val enumRes = findSessionProcesses(sid, ownerUid, ops)
        if (enumRes !is ProcEnumerationResult.Success) {
            return ResumeResult.Failed((enumRes as ProcEnumerationResult.Blocked).reason)
        }

        val liveTargets = enumRes.processes.filter {
            it.pid > 1 && it.pgid > 0 && it.sid == sid && it.uid == ownerUid
        }
        // Signal session leader first, then children.
        val ordered = liveTargets.sortedBy { if (it.pid == sid) 0 else 1 }
        val resumed = ArrayList<Int>(ordered.size)
        for (proc in ordered) {
            when (ops.sendSignal(proc.pid, OsConstants.SIGCONT)) {
                SignalStatus.OK -> resumed.add(proc.pid)
                SignalStatus.ESRCH -> Unit
                SignalStatus.EPERM, SignalStatus.ERROR -> {
                    return ResumeResult.Failed("SIGCONT failed for pid=${proc.pid}")
                }
            }
        }
        return ResumeResult.Success(resumed)
    }

    fun terminateSession(
        recordedPids: List<Int>,
        sid: Int,
        ownerUid: Int,
        timeoutMs: Long = 5000L,
        ops: ProcessOps = OsProcessOps
    ): TerminateResult {
        if (sid <= 0 || ownerUid < 0) {
            return TerminateResult.Failed("Invalid sid=$sid or ownerUid=$ownerUid")
        }

        val initialEnum = findSessionProcesses(sid, ownerUid, ops)
        if (initialEnum !is ProcEnumerationResult.Success) {
            return TerminateResult.Failed((initialEnum as ProcEnumerationResult.Blocked).reason)
        }

        val initialTargets = initialEnum.processes.filter {
            it.pid > 1 && it.pgid > 0 && it.sid == sid && it.uid == ownerUid
        }
        if (initialTargets.isEmpty()) {
            return TerminateResult.Success(terminatedPids = emptyList(), killedPids = emptyList())
        }

        val signaled = LinkedHashSet<Int>()
        for (proc in initialTargets) {
            val contStatus = ops.sendSignal(proc.pid, OsConstants.SIGCONT)
            if (contStatus == SignalStatus.EPERM || contStatus == SignalStatus.ERROR) {
                return TerminateResult.Failed("SIGCONT failed during termination for pid=${proc.pid}")
            }
            if (contStatus == SignalStatus.OK) {
                signaled.add(proc.pid)
            }
        }
        for (proc in initialTargets) {
            val termStatus = ops.sendSignal(proc.pid, OsConstants.SIGTERM)
            if (termStatus == SignalStatus.EPERM || termStatus == SignalStatus.ERROR) {
                return TerminateResult.Failed("SIGTERM failed during termination for pid=${proc.pid}")
            }
            if (termStatus == SignalStatus.OK) {
                signaled.add(proc.pid)
            }
        }

        val termDeadline = ops.nowMs() + timeoutMs.coerceAtLeast(0L)
        var survivors: List<ProcStatInfo>
        while (true) {
            val pollEnum = findSessionProcessesInternal(sid, ownerUid, ops, treatEmptyAsBlocked = false)
            if (pollEnum !is ProcEnumerationResult.Success) {
                return TerminateResult.Failed((pollEnum as ProcEnumerationResult.Blocked).reason)
            }
            survivors = pollEnum.processes.filter {
                it.pid > 1 && it.pgid > 0 && it.sid == sid && it.uid == ownerUid
            }
            if (survivors.isEmpty()) {
                return TerminateResult.Success(terminatedPids = signaled.toList(), killedPids = emptyList())
            }
            if (ops.nowMs() >= termDeadline) break
            ops.sleepMs(TERMINATE_POLL_INTERVAL_MS)
        }

        val killed = ArrayList<Int>(survivors.size)
        for (proc in survivors) {
            val killStatus = ops.sendSignal(proc.pid, OsConstants.SIGKILL)
            if (killStatus == SignalStatus.EPERM || killStatus == SignalStatus.ERROR) {
                return TerminateResult.Failed("SIGKILL failed for survivor pid=${proc.pid}")
            }
            if (killStatus == SignalStatus.OK) {
                killed.add(proc.pid)
                signaled.add(proc.pid)
            }
        }

        val killDeadline = ops.nowMs() + KILL_VERIFY_TIMEOUT_MS
        while (true) {
            val verifyEnum = findSessionProcessesInternal(sid, ownerUid, ops, treatEmptyAsBlocked = false)
            if (verifyEnum !is ProcEnumerationResult.Success) {
                return TerminateResult.Failed((verifyEnum as ProcEnumerationResult.Blocked).reason)
            }
            val remaining = verifyEnum.processes.filter {
                it.pid > 1 && it.pgid > 0 && it.sid == sid && it.uid == ownerUid
            }
            if (remaining.isEmpty()) {
                return TerminateResult.Success(terminatedPids = signaled.toList(), killedPids = killed)
            }
            if (ops.nowMs() >= killDeadline) {
                return TerminateResult.Failed("Processes survived SIGKILL in sid=$sid: ${remaining.map { it.pid }}")
            }
            ops.sleepMs(STOP_POLL_INTERVAL_MS)
        }
    }

    private fun rollbackStopped(
        stoppedByUs: Set<Int>,
        sid: Int,
        ownerUid: Int,
        ops: ProcessOps
    ) {
        if (stoppedByUs.isEmpty()) return
        for (pid in stoppedByUs) {
            if (pid <= 1) continue
            val uid = ops.readProcUid(pid) ?: continue
            if (uid != ownerUid) continue
            val statRaw = ops.readProcStat(pid) ?: continue
            val info = parseProcStat(pid, statRaw, uid) ?: continue
            if (info.sid == sid && info.pgid > 0) {
                ops.sendSignal(pid, OsConstants.SIGCONT)
            }
        }
    }
}
