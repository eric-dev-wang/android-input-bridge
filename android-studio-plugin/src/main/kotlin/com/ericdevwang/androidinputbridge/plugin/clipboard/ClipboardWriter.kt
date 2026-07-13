package com.ericdevwang.androidinputbridge.plugin.clipboard

import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

interface ClipboardWriter {
    fun write(text: String): ClipboardWriteResult
}

sealed interface ClipboardWriteResult {
    data object Success : ClipboardWriteResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : ClipboardWriteResult
}

fun interface ClipboardSink {
    fun setContents(selection: StringSelection)
}

class IntellijClipboardWriter(
    private val sink: ClipboardSink = CopyPasteManagerSink(),
) : ClipboardWriter {
    override fun write(text: String): ClipboardWriteResult {
        return try {
            sink.setContents(StringSelection(text))
            ClipboardWriteResult.Success
        } catch (exception: Throwable) {
            ClipboardWriteResult.Failure(FAILURE_MESSAGE, exception)
        }
    }

    private class CopyPasteManagerSink : ClipboardSink {
        override fun setContents(selection: StringSelection) {
            CopyPasteManager.getInstance().setContents(selection)
        }
    }

    private companion object {
        const val FAILURE_MESSAGE = "Clipboard write failed."
    }
}
