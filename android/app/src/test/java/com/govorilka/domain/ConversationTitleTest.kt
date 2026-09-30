package com.govorilka.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationTitleTest {
    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals("Привет", conversationTitle("  Привет \n"))
    }

    @Test
    fun emptyTextGivesDefaultTitle() {
        assertEquals(NEW_CONVERSATION_TITLE, conversationTitle(""))
    }

    @Test
    fun blankTextGivesDefaultTitle() {
        assertEquals(NEW_CONVERSATION_TITLE, conversationTitle(" \t\n "))
    }

    @Test
    fun keepsExactly48Characters() {
        val text = "а".repeat(48)
        assertEquals(text, conversationTitle(text))
    }

    @Test
    fun drops49thCharacterAfterTrim() {
        val text = "б".repeat(48)
        assertEquals(text, conversationTitle("  ${text}в  "))
    }
}
