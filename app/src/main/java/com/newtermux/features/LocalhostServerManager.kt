package com.newtermux.features

import android.os.Process
import android.system.Os
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Discovers active localhost development servers from /proc/net/tcp{,6} (with TCP connect fallback)
 * and resolves owning Termux-UID processes so servers can be inspected and stopped.
 */
object LocalhostServerManager {

    data class LocalhostServer(
        val port: Int,
        val pid: Int? = null,
        val processName: String = "localhost",
        val commandLine: String = "",
    ) {
        val displayTitle: String
            get() = if (processName.isNotBlank() && processName != "localhost") {
                ":$port · $processName"
            } else {
                ":$port"
            }
    }

    internal data class ListeningSocket(
        val port: Int,
        val inode: Long,
        val uid: Int,
    )

    val COMMON_DEV_PORTS = listOf(
        3000, 3001, 3002,
        4000, 4173, 4200, 4321,
        5000, 5001, 5173, 5174, 5175,
        8000, 8080, 8081, 8088, 8888,
        9000, 9090,
    )

    private val LOCAL_V4_ADDRS = setOf("0100007F", "00000000")
    private val LOCAL_V6_ADDRS = setOf(
        "00000000000000000000000000000000", // ::
        "00000000000000000000000001000000", // ::1 (little-endian 32-bit words)
        "00000000000000000000000000000001", // ::1 (big-endian)
        "0000000000000000FFFF00000100007F", // ::ffff:127.0.0.1 (LE words)
        "0000000000000000FFFF000000000000", // ::ffff:0.0.0.0
    )

    /**
     * Scan running localhost listening servers. Blocking I/O; run off the main thread.
     */
    @JvmStatic
    fun scanServers(): List<LocalhostServer> {
        val myUid = Process.myUid()
        val myPid = Process.myPid()

        val sockets = mutableMapOf<Int, ListeningSocket>()
        var procReadable = false
        for ((path, isV6) in listOf("/proc/net/tcp" to false, "/proc/net/tcp6" to true)) {
            val file = File(path)
            if (file.canRead()) {
                runCatching {
                    file.bufferedReader().useLines { lines ->
                        procReadable = true
                        for (sock in parseProcNetTcp(lines, isV6)) {
                            if (sock.port in 1024..65535 && (sock.uid < 0 || sock.uid == myUid)) {
                                sockets.putIfAbsent(sock.port, sock)
                            }
                        }
                    }
                }
            }
        }

        val inodeMap = if (sockets.isNotEmpty()) {
            mapSocketInodesToProcesses(sockets.values.map { it.inode }.toSet(), myPid)
        } else {
            emptyMap()
        }

        if (sockets.isNotEmpty()) {
            return sockets.values
                .sortedBy { it.port }
                .mapNotNull { sock ->
                    val procInfo = inodeMap[sock.inode]
                    // If the socket belongs to a different process and we found myPid (shouldn't happen due to filter), skip
                    if (procInfo?.pid == myPid) return@mapNotNull null
                    LocalhostServer(
                        port = sock.port,
                        pid = procInfo?.pid,
                        processName = procInfo?.comm ?: "localhost",
                        commandLine = procInfo?.cmdline ?: "",
                    )
                }
        }

        if (procReadable) {
            return emptyList()
        }

        // Fallback when SELinux restricts /proc/net/tcp*: probe common dev ports on loopback
        val openPorts = COMMON_DEV_PORTS.filter { port ->
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress("127.0.0.1", port), 25)
                    true
                }
            } catch (_: Exception) {
                false
            }
        }
        if (openPorts.isEmpty()) return emptyList()

        val candidates = readCandidateProcesses(myPid)
        return openPorts.sorted().map { port ->
            val match = findMatchingProcessForPort(port, candidates)
            LocalhostServer(
                port = port,
                pid = match?.pid,
                processName = match?.comm ?: "localhost",
                commandLine = match?.cmdline ?: "",
            )
        }
    }

    /** Convenience method returning just sorted active port numbers. */
    @JvmStatic
    fun scanPorts(): List<Int> = scanServers().map { it.port }

    /**
     * Terminate a discovered localhost server cleanly (SIGTERM then SIGKILL fallback).
     * Never terminates PID <= 1 or the app UI process itself.
     * Blocking up to ~180ms; invoke off the main thread.
     */
    @JvmStatic
    fun stopServer(server: LocalhostServer): Boolean {
        val pid = server.pid ?: findPidForPort(server.port) ?: return false
        return terminatePid(pid)
    }

    @JvmStatic
    fun stopPort(port: Int): Boolean {
        val pid = findPidForPort(port) ?: return false
        return terminatePid(pid)
    }

    private fun findPidForPort(port: Int): Int? {
        return scanServers().firstOrNull { it.port == port }?.pid
    }

    private fun terminatePid(pid: Int): Boolean {
        val myPid = Process.myPid()
        if (pid <= 1 || pid == myPid) return false
        val procDir = File("/proc/$pid")
        return try {
            Process.sendSignal(pid, 15) // SIGTERM
            val deadline = System.currentTimeMillis() + 180L
            while (System.currentTimeMillis() < deadline && procDir.exists()) {
                Thread.sleep(25)
            }
            if (procDir.exists()) {
                Process.sendSignal(pid, 9) // SIGKILL
                Thread.sleep(40)
            }
            !procDir.exists() || true
        } catch (_: Throwable) {
            false
        }
    }

    internal fun parseProcNetTcp(lines: Sequence<String>, isIpv6: Boolean): List<ListeningSocket> {
        val out = ArrayList<ListeningSocket>()
        for (raw in lines) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("sl")) continue
            val cols = trimmed.split(Regex("\\s+"))
            if (cols.size < 10) continue

            // st == 0A means TCP_LISTEN
            if (!cols[3].equals("0A", ignoreCase = true)) continue

            val localParts = cols[1].split(':')
            if (localParts.size != 2) continue
            val addrHex = localParts[0].uppercase()
            val validAddr = if (isIpv6) {
                addrHex in LOCAL_V6_ADDRS
            } else {
                addrHex in LOCAL_V4_ADDRS || addrHex.endsWith("00007F") // 127.x.x.x loopback
            }
            if (!validAddr) continue

            val port = localParts[1].toIntOrNull(16) ?: continue
            val uid = cols[7].toIntOrNull() ?: -1
            val inode = cols[9].toLongOrNull() ?: -1L
            out.add(ListeningSocket(port = port, inode = inode, uid = uid))
        }
        return out
    }

    internal data class ProcInfo(val pid: Int, val comm: String, val cmdline: String)

    internal fun findMatchingProcessForPort(port: Int, processes: List<ProcInfo>): ProcInfo? {
        val portToken = port.toString()
        // First look for explicit port flag or number in cmdline
        processes.firstOrNull { proc ->
            proc.cmdline.contains(":$portToken") ||
                Regex("(?:^|\\s|=)$portToken(?:\\s|$)").containsMatchIn(proc.cmdline)
        }?.let { return it }

        // Next look for known dev server runtimes
        val devKeywords = listOf("node", "vite", "next", "python", "uvicorn", "flask", "django", "ruby", "rails", "php", "go ", "hugo", "bun", "deno", "http-server", "live-server")
        return processes.firstOrNull { proc ->
            val lower = "${proc.comm} ${proc.cmdline}".lowercase()
            devKeywords.any { it in lower }
        }
    }

    private fun readCandidateProcesses(myPid: Int): List<ProcInfo> {
        val procRoot = File("/proc")
        val entries = procRoot.listFiles() ?: return emptyList()
        val list = ArrayList<ProcInfo>()
        for (dir in entries) {
            val pid = dir.name.toIntOrNull() ?: continue
            if (pid == myPid || pid <= 1) continue
            if (!dir.canRead()) continue
            readProcInfo(dir, pid)?.let { list.add(it) }
        }
        return list
    }

    private fun mapSocketInodesToProcesses(inodes: Set<Long>, myPid: Int): Map<Long, ProcInfo> {
        if (inodes.isEmpty()) return emptyMap()
        val result = HashMap<Long, ProcInfo>()
        val procEntries = File("/proc").listFiles() ?: return result
        for (dir in procEntries) {
            val pid = dir.name.toIntOrNull() ?: continue
            if (pid <= 1) continue
            val fdDir = File(dir, "fd")
            val fds = fdDir.listFiles() ?: continue
            var matchedInfo: ProcInfo? = null
            for (fd in fds) {
                val target = try {
                    Os.readlink(fd.absolutePath)
                } catch (_: Throwable) {
                    continue
                }
                if (target.startsWith("socket:[") && target.endsWith("]")) {
                    val inode = target.substring(8, target.length - 1).toLongOrNull() ?: continue
                    if (inode in inodes) {
                        if (pid == myPid) continue
                        if (matchedInfo == null) {
                            matchedInfo = readProcInfo(dir, pid) ?: ProcInfo(pid, "process", "")
                        }
                        result[inode] = matchedInfo
                    }
                }
            }
        }
        return result
    }

    private fun readProcInfo(procDir: File, pid: Int): ProcInfo? {
        return try {
            val comm = File(procDir, "comm").takeIf { it.canRead() }?.readText()?.trim().orEmpty()
            val rawCmd = File(procDir, "cmdline").takeIf { it.canRead() }?.readBytes()
            val cmdline = rawCmd?.let { bytes ->
                String(bytes).replace('\u0000', ' ').trim()
            }.orEmpty()
            val name = comm.ifBlank {
                cmdline.substringBefore(' ').substringAfterLast('/').ifBlank { "localhost" }
            }
            ProcInfo(pid = pid, comm = name, cmdline = cmdline)
        } catch (_: Throwable) {
            null
        }
    }
}
