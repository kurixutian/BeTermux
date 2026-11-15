package com.termux.app.activities

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevBrowserUrlTest {

    @Test
    fun acceptsLocalAndPrivateDevHosts() {
        assertTrue(isLocalDevUrl("http://localhost:3000"))
        assertTrue(isLocalDevUrl("https://127.0.0.1:8080/api"))
        assertTrue(isLocalDevUrl("http://[::1]:5173"))
        assertTrue(isLocalDevUrl("http://10.0.2.2:8000"))
        assertTrue(isLocalDevUrl("http://mybox.local:3000"))
        assertTrue(isLocalDevUrl("http://192.168.1.42:3000"))
        assertTrue(isLocalDevUrl("http://172.16.0.5:3000"))
        assertTrue(isLocalDevUrl("http://172.31.255.1:3000"))
    }

    @Test
    fun rejectsExternalHosts() {
        assertFalse(isLocalDevUrl("https://accounts.google.com/o/oauth2/v2/auth"))
        assertFalse(isLocalDevUrl("https://example.com"))
        assertFalse(isLocalDevUrl("http://172.32.0.1:3000"))
        assertFalse(isLocalDevUrl("not a url"))
    }
}
