package com.govorilka.data.remote

import com.govorilka.domain.ApiResult
import com.govorilka.domain.ChatLine
import com.govorilka.domain.LiveSession
import com.govorilka.domain.MessageRole
import com.govorilka.domain.Voice
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ApiCodecTest {
    private fun parseJson(body: String): JsonObject = Json.parseToJsonElement(body).jsonObject

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun session(sdp: String = "v=0", instructions: String = "", voice: Voice = Voice.DEFAULT, webSearch: Boolean = false) =
        parseJson(sessionBody(sdp, instructions, voice, webSearch))

    private fun chat(messages: List<ChatLine>, instructions: String = "", webSearch: Boolean = false) =
        parseJson(chatBody(messages, instructions, webSearch))

    @Test
    fun sessionBodyCarriesSdpAndInstructions() {
        val body = session(instructions = "  Говори кратко  ")
        assertEquals("v=0", body.string("sdp"))
        assertEquals("  Говори кратко  ", body.string("instructions"))
    }

    @Test
    fun sessionBodyCarriesVoiceAndWebSearch() {
        val body = session(voice = Voice.Cinder, webSearch = true)
        assertEquals("cinder", body.string("voice"))
        assertEquals(true, body.getValue("webSearch").jsonPrimitive.boolean)
    }

    @Test
    fun chatBodyCarriesWebSearch() {
        val messages = listOf(ChatLine(MessageRole.User, "Привет"))
        assertEquals(false, chat(messages).getValue("webSearch").jsonPrimitive.boolean)
        assertEquals(true, chat(messages, webSearch = true).getValue("webSearch").jsonPrimitive.boolean)
    }

    @Test
    fun unknownVoiceFallsBackToDefault() {
        assertEquals(Voice.Gleam, Voice.fromWireName("marin"))
        assertEquals(Voice.Gleam, Voice.fromWireName(null))
        assertEquals(Voice.Delta, Voice.fromWireName("delta"))
    }

    @Test
    fun blankInstructionsAreOmitted() {
        assertFalse("instructions" in session())
        assertFalse("instructions" in session(instructions = " \n "))
        assertFalse("instructions" in chat(listOf(ChatLine(MessageRole.User, "Привет")), " "))
    }

    @Test
    fun sdpWithQuotesRoundTrips() {
        val sdp = "a=\"quoted\"\r\nb=\\x"
        assertEquals(sdp, session(sdp = sdp).string("sdp"))
    }

    @Test
    fun chatBodyEncodesRolesAndKeepsWhitespace() {
        val messages = listOf(
            ChatLine(MessageRole.User, "Привет,   как  дела? "),
            ChatLine(MessageRole.Assistant, "Отлично"),
        )
        val encoded = chat(messages, "Промпт").getValue("messages").jsonArray.map { it.jsonObject }
        assertEquals(listOf("user", "assistant"), encoded.map { it.string("role") })
        assertEquals("Привет,   как  дела? ", encoded[0].string("content"))
    }

    @Test
    fun endpointDropsTrailingSlash() {
        assertEquals("http://127.0.0.1:3000/api/session", endpoint(" http://127.0.0.1:3000/ ", SESSION_PATH).toString())
        assertEquals("http://127.0.0.1:3000/api/chat", endpoint("http://127.0.0.1:3000", CHAT_PATH).toString())
    }

    @Test
    fun endpointRejectsAddressWithoutScheme() {
        assertNull(endpoint("192.168.1.5:3000", SESSION_PATH))
    }

    @Test
    fun requestHasBearerHeaderAndJsonBody() {
        val request = buildRequest(endpoint("http://127.0.0.1:3000", CHAT_PATH)!!, "s3cret", "{}")
        assertEquals("POST", request.method)
        assertEquals("Bearer s3cret", request.header("Authorization"))
        assertEquals("application/json; charset=utf-8", request.body!!.contentType().toString())
        assertEquals("{}", Buffer().also { request.body!!.writeTo(it) }.readUtf8())
    }

    @Test(expected = IllegalArgumentException::class)
    fun requestRejectsSecretWithLineBreak() {
        buildRequest(endpoint("http://127.0.0.1:3000", CHAT_PATH)!!, "a\nb", "{}")
    }

    @Test
    fun sessionResponseIgnoresUnknownFields() {
        val body = """{"session":{"id":"sess_1","extra":1},"transport":{"type":"webrtc","sdp":"v=0"},"more":true}"""
        assertEquals(ApiResult.Success(LiveSession("sess_1", "v=0")), parseSession(201, body))
    }

    @Test
    fun sessionResponseAcceptsEmptyId() {
        val body = """{"session":{"id":""},"transport":{"type":"webrtc","sdp":"v=0"}}"""
        assertEquals(ApiResult.Success(LiveSession("", "v=0")), parseSession(201, body))
    }

    @Test
    fun malformedSessionResponsesFail() {
        val unexpected = ApiResult.Failure(UNEXPECTED_RESPONSE)
        assertEquals(unexpected, parseSession(201, """{"session":{"id":"s"},"transport":{"type":"ws","sdp":"v=0"}}"""))
        assertEquals(unexpected, parseSession(201, """{"session":{"id":"s"},"transport":{"type":"webrtc"}}"""))
        assertEquals(unexpected, parseSession(201, """{"session":{"id":5},"transport":{"type":"webrtc","sdp":"v=0"}}"""))
        assertEquals(unexpected, parseSession(201, "not json"))
    }

    @Test
    fun chatResponseIgnoresUnknownFields() {
        assertEquals(ApiResult.Success("Ответ"), parseChat(200, """{"text":"Ответ","id":"x"}"""))
    }

    @Test
    fun chatResponseWithoutTextFails() {
        assertEquals(ApiResult.Failure(UNEXPECTED_RESPONSE), parseChat(200, """{"reply":"x"}"""))
    }

    @Test
    fun errorFieldBecomesMessage() {
        assertEquals(ApiResult.Failure("Unauthorized"), parseChat(401, """{"error":"Unauthorized"}"""))
        assertEquals(ApiResult.Failure("OpenAI request failed"), parseSession(502, """{"error":"OpenAI request failed"}"""))
    }

    @Test
    fun errorWithoutMessageFallsBackToStatusCode() {
        val secret = "s3cret"
        val failures = listOf(
            parseChat(500, ""),
            parseChat(500, "<html>$secret</html>"),
            parseChat(500, """{"detail":"$secret"}"""),
            parseChat(500, """{"error":""}"""),
        )
        failures.forEach { assertEquals(ApiResult.Failure(serverFailure(500)), it) }
    }
}
