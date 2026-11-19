package com.termux.app.activities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileManagerClipboardStateTest {

    @Test
    fun copyStateUsesCopyLabels() {
        val state = FileClipboardState(ClipboardOperation.COPY, listOf(File("one.txt")))

        assertEquals("1 item ready to copy", state.readyTitle())
        assertEquals("Copy Here", state.pendingOperation?.confirmationLabel)
    }

    @Test
    fun moveStateUsesMoveLabels() {
        val state = FileClipboardState(ClipboardOperation.MOVE, listOf(File("one.txt"), File("two.txt")))

        assertEquals("2 items ready to move", state.readyTitle())
        assertEquals("Move Here", state.pendingOperation?.confirmationLabel)
    }

    @Test
    fun clearedStateDropsOperationAndItems() {
        val cleared = FileClipboardState()

        assertNull(cleared.pendingOperation)
        assertTrue(cleared.selectedItems.isEmpty())
    }
}
