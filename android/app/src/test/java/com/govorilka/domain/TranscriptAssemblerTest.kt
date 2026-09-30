package com.govorilka.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TranscriptAssemblerTest {
    private var nextId = 0
    private val ids = { "u${nextId++}" }

    private fun TranscriptAssembler.add(role: MessageRole, delta: String, startMs: Long, endMs: Long) =
        append(VoiceEvent.Transcript(role, delta, startMs, endMs), ids)

    private val user = MessageRole.User
    private val assistant = MessageRole.Assistant

    @Test
    fun shortPauseAppendsDeltaAsIs() {
        val result = TranscriptAssembler()
            .add(user, "Привет", 0, 400)
            .add(user, ", как дела", 1399, 1800)

        assertEquals(listOf(Utterance("u0", user, "Привет, как дела", 0, 1800)), result.utterances)
        assertEquals(emptyList<Utterance>(), result.closed)
    }

    @Test
    fun pauseOfOneSecondStartsNewUtterance() {
        val result = TranscriptAssembler()
            .add(user, "Раз", 0, 400)
            .add(user, "Два", 1400, 1600)

        assertEquals(listOf(Utterance("u0", user, "Раз", 0, 400)), result.closed)
        assertEquals(Utterance("u1", user, "Два", 1400, 1600), result.open[user])
    }

    @Test
    fun rolesDoNotMergeWhenSpeakingAtOnce() {
        val result = TranscriptAssembler()
            .add(user, "Подожди", 0, 300)
            .add(assistant, "Итак", 100, 400)
            .add(user, " секунду", 350, 700)

        assertEquals(
            listOf(
                Utterance("u0", user, "Подожди секунду", 0, 700),
                Utterance("u1", assistant, "Итак", 100, 400),
            ),
            result.utterances,
        )
    }

    @Test
    fun keepsMinStartAndMaxEnd() {
        val result = TranscriptAssembler()
            .add(assistant, "b", 500, 900)
            .add(assistant, "c", 300, 700)

        assertEquals(Utterance("u0", assistant, "bc", 300, 900), result.open[assistant])
    }

    @Test
    fun emptyDeltaChangesNothing() {
        val before = TranscriptAssembler().add(user, "Да", 0, 100)

        assertSame(before, before.append(VoiceEvent.Transcript(user, "", 5000, 5100), ids))
    }

    @Test
    fun finishClosesOpenNonBlankUtterances() {
        val result = TranscriptAssembler()
            .add(user, "Пока", 0, 200)
            .add(assistant, "  ", 100, 150)
            .finish()

        assertEquals(listOf(Utterance("u0", user, "Пока", 0, 200)), result.closed)
        assertEquals(emptyMap<MessageRole, Utterance>(), result.open)
    }

    @Test
    fun blankOpenUtteranceIsHiddenAndDroppedOnPause() {
        val result = TranscriptAssembler()
            .add(user, " ", 0, 100)
            .add(user, "Алло", 2000, 2300)

        assertEquals(emptyList<Utterance>(), result.closed)
        assertEquals(listOf(Utterance("u1", user, "Алло", 2000, 2300)), result.utterances)
    }

    @Test
    fun closedStaysInTailUntilSaved() {
        val result = TranscriptAssembler()
            .add(user, "Раз", 0, 400)
            .add(user, "Два", 1400, 1600)

        assertEquals(listOf("u0", "u1"), result.feedTail(emptySet()).map { it.id })
        assertEquals(listOf("u1"), result.feedTail(setOf("u0")).map { it.id })
    }

    @Test
    fun openIsDrawnBelowUnsavedClosedEvenIfItStartedEarlier() {
        val result = TranscriptAssembler()
            .add(assistant, "Длинный ответ", 0, 500)
            .add(user, "Да", 100, 200)
            .add(user, "Нет", 1500, 1700)

        assertEquals(listOf("u1", "u0", "u2"), result.feedTail(emptySet()).map { it.id })
    }

    @Test
    fun unsavedClosedSkipsOpenAndSaved() {
        val result = TranscriptAssembler()
            .add(user, "Раз", 0, 400)
            .add(user, "Два", 1400, 1600)
            .add(assistant, "Ок", 1500, 1700)

        assertEquals(listOf("u0"), result.unsavedClosed(emptySet()).map { it.id })
        assertEquals(emptyList<Utterance>(), result.unsavedClosed(setOf("u0")))
    }

    @Test
    fun emptyDeltaClosesNothing() {
        val before = TranscriptAssembler().add(user, "Да", 0, 100)
        val after = before.append(VoiceEvent.Transcript(user, "", 5000, 5100), ids)

        assertEquals(emptyList<Utterance>(), after.unsavedClosed(emptySet()))
    }

    @Test
    fun finishedUtterancesAreNotUnsavedOnceStored() {
        val finished = TranscriptAssembler()
            .add(user, "Пока", 0, 200)
            .add(assistant, "До встречи", 100, 400)
            .finish()

        assertEquals(listOf("u0", "u1"), finished.unsavedClosed(emptySet()).map { it.id })
        assertEquals(emptyList<Utterance>(), finished.unsavedClosed(setOf("u0", "u1")))
    }
}
