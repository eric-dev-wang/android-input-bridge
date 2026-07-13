package com.ericdevwang.androidinputbridge.plugin.clipboard

import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection
import javax.swing.SwingUtilities

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
    private val runOnEdt: ((() -> Unit) -> Unit) = ::runOnEdt,
) : ClipboardWriter {
    override fun write(text: String): ClipboardWriteResult {
        var failure: Throwable? = null
        return try {
            runOnEdt {
                try {
                    sink.setContents(StringSelection(text))
                } catch (exception: Throwable) {
                    failure = exception
                }
            }
            failure?.let { ClipboardWriteResult.Failure(FAILURE_MESSAGE, it) }
                ?: ClipboardWriteResult.Success
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

        fun runOnEdt(action: () -> Unit) {
            if (SwingUtilities.isEventDispatchThread()) {
                action()
            } else {
                SwingUtilities.invokeAndWait(action)
            }
        }
    }
}
