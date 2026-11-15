package com.newtermux.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SshProfileStoreTest {

    @Test
    fun buildCommandIncludesPortKeyAndTunnelWhenConfigured() {
        val profile = SshProfile().apply {
            host = "10.0.0.5"
            port = 2222
            username = "deploy"
            keyPath = "/home/.ssh/id_ed25519"
            tunnelEnabled = true
            tunnelType = "local"
            tunnelLocalPort = 3000
            tunnelRemoteHost = "localhost"
            tunnelRemotePort = 8080
        }

        assertEquals(
            "ssh -o StrictHostKeyChecking=accept-new -L 3000:localhost:8080 -p 2222 -i /home/.ssh/id_ed25519 deploy@10.0.0.5",
            profile.buildCommand()
        )
        assertEquals("deploy@10.0.0.5:2222", profile.displayLabel())
        assertEquals("-L 3000:localhost:8080", profile.tunnelLabel())
    }

    @Test
    fun buildCommandOmitsDefaultPortAndDisabledTunnel() {
        val profile = SshProfile().apply {
            host = "example.com"
            username = "alice"
        }

        assertEquals(
            "ssh -o StrictHostKeyChecking=accept-new alice@example.com",
            profile.buildCommand()
        )
        assertEquals("alice@example.com", profile.displayLabel())
        assertNull(profile.tunnelLabel())
    }
}
