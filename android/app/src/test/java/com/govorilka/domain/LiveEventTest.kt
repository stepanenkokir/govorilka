package com.govorilka.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveEventTest {
    @Test
    fun inputTranscriptIsUser() {
        val json = """{"type":"session.input_transcript.delta","delta":" привет","start_ms":10,"end_ms":250,"extra":1}"""
        assertEquals(
            ParsedLiveEvent.Event(VoiceEvent.Transcript(MessageRole.User, " привет", 10, 250)),
            parseLiveEvent(json),
        )
    }

    @Test
    fun outputTranscriptIsAssistant() {
        val json = """{"type":"session.output_transcript.delta","delta":"Да. ","start_ms":0,"end_ms":90}"""
        assertEquals(
            ParsedLiveEvent.Event(VoiceEvent.Transcript(MessageRole.Assistant, "Да. ", 0, 90)),
            parseLiveEvent(json),
        )
    }

    @Test
    fun sessionStartedCarriesId() {
        val json = """{"type":"session.started","session":{"id":"sess_1","model":"gpt-live-1"}}"""
        assertEquals(ParsedLiveEvent.Event(VoiceEvent.Connected("sess_1")), parseLiveEvent(json))
    }

    @Test
    fun sessionClosed() {
        assertEquals(ParsedLiveEvent.Event(VoiceEvent.Closed), parseLiveEvent("""{"type":"session.closed"}"""))
    }

    @Test
    fun unknownTypeIsSkipped() {
        assertEquals(ParsedLiveEvent.Skip, parseLiveEvent("""{"type":"response.event","payload":{}}"""))
    }

    @Test
    fun brokenJsonIsMalformed() {
        assertTrue(parseLiveEvent("""{"type":"session.closed"""") is ParsedLiveEvent.Malformed)
    }

    @Test
    fun nonObjectIsMalformed() {
        assertTrue(parseLiveEvent("[1,2]") is ParsedLiveEvent.Malformed)
    }

    @Test
    fun transcriptWithoutTimingIsMalformed() {
        val json = """{"type":"session.input_transcript.delta","delta":"a","start_ms":"10","end_ms":20}"""
        assertTrue(parseLiveEvent(json) is ParsedLiveEvent.Malformed)
    }

    @Test
    fun startedWithoutIdIsMalformed() {
        assertTrue(parseLiveEvent("""{"type":"session.started","session":{}}""") is ParsedLiveEvent.Malformed)
    }
}
