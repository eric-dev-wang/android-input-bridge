package com.ericdevwang.androidinputbridge.plugin.clipboard

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import javax.swing.SwingUtilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardWriterTest {
    @Test
    fun writerPreservesUnicodeAndMultilineText() {
        var captured: StringSelection? = null
        val writer = IntellijClipboardWriter(
            sink = ClipboardSink { captured = it },
        )

        val result = writer.write("你好 👋\ncode")

        assertEquals(ClipboardWriteResult.Success, result)
        assertEquals("你好 👋\ncode", captured!!.getTransferData(DataFlavor.stringFlavor))
    }

    @Test
    fun writerConvertsPlatformExceptionToFailure() {
        val writer = IntellijClipboardWriter(
            sink = ClipboardSink { throw IllegalStateException("clipboard unavailable") },
        )

        val result = writer.write("text")

        val failure = result as ClipboardWriteResult.Failure
        assertEquals("Clipboard write failed.", failure.message)
        assertTrue(failure.cause is IllegalStateException)
    }

    @Test
    fun writerDoesNotDispatchClipboardWriteToEdt() {
        var calledOnEdt = true
        val writer = IntellijClipboardWriter(
            sink = ClipboardSink { calledOnEdt = SwingUtilities.isEventDispatchThread() },
        )

        val result = writer.write("text")

        assertEquals(ClipboardWriteResult.Success, result)
        assertTrue(!calledOnEdt)
    }
}
