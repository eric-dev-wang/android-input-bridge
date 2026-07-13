package com.ericdevwang.androidinputbridge.plugin.clipboard

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardWriterTest {
    @Test
    fun writerPreservesUnicodeAndMultilineText() {
        var captured: StringSelection? = null
        val writer = IntellijClipboardWriter(
            sink = ClipboardSink { captured = it },
            runOnEdt = { action -> action() },
        )

        val result = writer.write("你好 👋\ncode")

        assertEquals(ClipboardWriteResult.Success, result)
        assertEquals("你好 👋\ncode", captured!!.getTransferData(DataFlavor.stringFlavor))
    }

    @Test
    fun writerConvertsPlatformExceptionToFailure() {
        val writer = IntellijClipboardWriter(
            sink = ClipboardSink { throw IllegalStateException("clipboard unavailable") },
            runOnEdt = { action -> action() },
        )

        val result = writer.write("text")

        val failure = result as ClipboardWriteResult.Failure
        assertEquals("Clipboard write failed.", failure.message)
        assertTrue(failure.cause is IllegalStateException)
    }
}
