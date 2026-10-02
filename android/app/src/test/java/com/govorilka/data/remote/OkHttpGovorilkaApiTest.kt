package com.govorilka.data.remote

import com.govorilka.domain.ApiResult
import com.govorilka.domain.ChatLine
import com.govorilka.domain.MessageRole
import com.govorilka.domain.Voice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OkHttpGovorilkaApiTest {
    private val api = OkHttpGovorilkaApi()
    private val messages = listOf(ChatLine(MessageRole.User, "Привет"))

    @Test
    fun blankBaseUrlFailsWithoutRequest() = runBlocking {
        assertEquals(ApiResult.Failure(MISSING_BASE_URL), api.sendChat(" ", "s", messages, "", false))
        assertEquals(ApiResult.Failure(MISSING_BASE_URL), api.createSession("", "s", "v=0", "", Voice.DEFAULT, false))
    }

    @Test
    fun baseUrlWithoutSchemeFailsWithoutRequest() = runBlocking {
        assertEquals(ApiResult.Failure(INVALID_BASE_URL), api.sendChat("192.168.1.5:3000", "s", messages, "", false))
    }

    @Test
    fun secretWithCyrillicFailsWithoutRequest() = runBlocking {
        assertEquals(
            ApiResult.Failure(INVALID_SECRET),
            api.sendChat("http://127.0.0.1:3000", "секрет", messages, "", false),
        )
    }
}
