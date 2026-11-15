package com.newtermux.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalhostServerManagerTest {

    @Test
    fun parseProcNetTcp_extractsListeningLoopbackAndAnySockets() {
        val sample = sequenceOf(
            "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode",
            "   0: 0100007F:0BB8 00000000:0000 0A 00000000:00000000 00:00000000 00000000 10299 0 99812 1 0000000000000000 100 0 0 10 0",
            "   1: 00000000:1435 00000000:0000 0A 00000000:00000000 00:00000000 00000000 10299 0 99813 1 0000000000000000 100 0 0 10 0",
            "   2: 0100007F:1F90 0100007F:C4E2 01 00000000:00000000 00:00000000 00000000 10299 0 99814 1 0000000000000000 100 0 0 10 0",
            "   3: 0F02000A:1F40 00000000:0000 0A 00000000:00000000 00:00000000 00000000 10299 0 99815 1 0000000000000000 100 0 0 10 0",
        )

        val parsed = LocalhostServerManager.parseProcNetTcp(sample, isIpv6 = false)

        assertEquals(2, parsed.size)
        assertEquals(3000, parsed[0].port)
        assertEquals(99812L, parsed[0].inode)
        assertEquals(10299, parsed[0].uid)
        assertEquals(5173, parsed[1].port)
        assertEquals(99813L, parsed[1].inode)
    }

    @Test
    fun parseProcNetTcp6_extractsListeningAnyAndLoopbackV6() {
        val sample = sequenceOf(
            "  sl  local_address                         remote_address                        st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode",
            "   0: 00000000000000000000000000000000:1F90 00000000000000000000000000000000:0000 0A 00000000:00000000 00:00000000 00000000 10299 0 54321 1 00000000 100 0 0 10 0",
            "   1: 00000000000000000000000001000000:1068 00000000000000000000000000000000:0000 0A 00000000:00000000 00:00000000 00000000 10299 0 54322 1 00000000 100 0 0 10 0",
        )

        val parsed = LocalhostServerManager.parseProcNetTcp(sample, isIpv6 = true)

        assertEquals(2, parsed.size)
        assertEquals(8080, parsed[0].port)
        assertEquals(4200, parsed[1].port)
    }

    @Test
    fun findMatchingProcessForPort_prefersExplicitPortMatchOverGenericRuntime() {
        val procs = listOf(
            LocalhostServerManager.ProcInfo(101, "node", "node /data/server.js --port 5173"),
            LocalhostServerManager.ProcInfo(102, "python3", "python3 -m http.server 8000"),
        )

        val match = LocalhostServerManager.findMatchingProcessForPort(8000, procs)
        assertNotNull(match)
        assertEquals(102, match?.pid)
        assertTrue(match?.comm == "python3")
    }
}
