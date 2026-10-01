package com.govorilka.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatHistoryTest {
    private fun message(index: Int, role: MessageRole, text: String, source: MessageSource) = Message(
        id = "m$index",
        conversationId = "c",
        role = role,
        text = text,
        source = source,
        startMs = null,
        endMs = null,
        liveSessionId = null,
        createdAt = index.toLong(),
    )

    @Test
    fun `voice and text history precede the prompt in feed order`() {
        val saved = listOf(
            message(1, MessageRole.User, " привет ", MessageSource.Voice),
            message(2, MessageRole.Assistant, "Здравствуйте", MessageSource.Voice),
            message(3, MessageRole.User, "как дела", MessageSource.Text),
        )

        val history = chatHistory(saved, "что нового")

        assertEquals(
            listOf(
                ChatLine(MessageRole.User, "привет"),
                ChatLine(MessageRole.Assistant, "Здравствуйте"),
                ChatLine(MessageRole.User, "как дела"),
                ChatLine(MessageRole.User, "что нового"),
            ),
            history,
        )
    }

    @Test
    fun `blank messages are dropped`() {
        val saved = listOf(message(1, MessageRole.Assistant, "  ", MessageSource.Voice))

        assertEquals(listOf(ChatLine(MessageRole.User, "вопрос")), chatHistory(saved, "вопрос"))
    }

    @Test
    fun `only the latest lines fit and the prompt stays last`() {
        val saved = (1..60).map { message(it, MessageRole.Assistant, "ответ $it", MessageSource.Text) }

        val history = chatHistory(saved, "вопрос")

        assertEquals(CHAT_HISTORY_LIMIT, history.size)
        assertEquals(ChatLine(MessageRole.Assistant, "ответ 22"), history.first())
        assertEquals(ChatLine(MessageRole.User, "вопрос"), history.last())
    }
}
