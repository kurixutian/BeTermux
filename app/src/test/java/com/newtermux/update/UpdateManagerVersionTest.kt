package com.newtermux.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerVersionTest {

    @Test
    fun cleanStripsPrefixAndBuildMetadata() {
        assertEquals("1.2.3", Version.clean("v1.2.3+123"))
        assertEquals("2.0.0", Version.clean(" V2.0.0 "))
    }

    @Test
    fun compareOrdersSemanticVersions() {
        assertTrue(Version.compare("1.2.0", "1.1.9") > 0)
        assertTrue(Version.compare("1.2.0", "1.2.0") == 0)
        assertTrue(Version.compare("1.2.0", "1.2.1") < 0)
        assertTrue(Version.compare("v2.0.0+42", "1.9.9") > 0)
    }
}
