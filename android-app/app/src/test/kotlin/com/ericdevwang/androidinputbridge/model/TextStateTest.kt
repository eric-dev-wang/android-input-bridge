package com.ericdevwang.androidinputbridge.model

import junit.framework.TestCase.assertEquals
import org.junit.Test

class TextStateTest {
    @Test
    fun changedTextIncrementsVersionAndUpdatesTimestamp() {
        val old = TextState("old", version = 4L, updatedAt = 100L)

        assertEquals(
            TextChangeResult.Accepted(TextState("new", 5L, 200L)),
            old.changeText("new", nowMillis = 200L),
        )
    }

    @Test
    fun equalTextKeepsVersionAndTimestamp() {
        val old = TextState("same", 4L, 100L)

        assertEquals(TextChangeResult.Accepted(old), old.changeText("same", 200L))
    }

    @Test
    fun overLimitTextIsRejectedWithoutChangingState() {
        val old = TextState("keep", 4L, 100L)

        assertEquals(
            TextChangeResult.RejectedTooLong,
            old.changeText("a".repeat(MAX_TEXT_CODE_POINTS + 1), 200L),
        )
    }

    @Test
    fun clearEmptyTextIsNoOp() {
        val old = TextState("", 4L, 100L)

        assertEquals(old, old.clear(200L))
    }

    @Test
    fun clearNonEmptyTextIncrementsVersionAndUpdatesTimestamp() {
        val old = TextState("text", 4L, 100L)

        assertEquals(TextState("", 5L, 200L), old.clear(200L))
    }

    @Test
    fun supplementaryCharacterTextAtCodePointLimitIsAccepted() {
        val text = "\uD83D\uDE00".repeat(MAX_TEXT_CODE_POINTS)

        assertEquals(
            TextChangeResult.Accepted(TextState(text, 1L, 200L)),
            TextState.initial(100L).changeText(text, nowMillis = 200L),
        )
    }

    @Test
    fun supplementaryCharacterTextOverCodePointLimitIsRejected() {
        val text = "\uD83D\uDE00".repeat(MAX_TEXT_CODE_POINTS + 1)

        assertEquals(
            TextChangeResult.RejectedTooLong,
            TextState.initial(100L).changeText(text, nowMillis = 200L),
        )
    }
}
