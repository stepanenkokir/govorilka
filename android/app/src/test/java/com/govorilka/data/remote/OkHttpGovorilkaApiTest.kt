package com.govorilka.data.remote

import com.govorilka.domain.ApiResult
import com.govorilka.domain.ChatLine
import com.govorilka.domain.MessageRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OkHttpGovorilkaApiTest {
    private val api = OkHttpGovorilkaApi()
    private val messages = listOf(ChatLine(MessageRole.User, "Привет"))

    @Test
    fun blankBaseUrlFailsWithoutRequest() = runBlocking {
        assertEquals(ApiResult.Failure(MISSING_BASE_URL), api.sendChat(" ", "s", messages, ""))
        assertEquals(ApiResult.Failure(MISSING_BASE_URL), api.createSession("", "s", "v=0", ""))
    }

    @Test
    fun baseUrlWithoutSchemeFailsWithoutRequest() = runBlocking {
        assertEquals(ApiResult.Failure(INVALID_BASE_URL), api.sendChat("192.168.1.5:3000", "s", messages, ""))
    }

    @Test
    fun secretWithCyrillicFailsWithoutRequest() = runBlocking {
        assertEquals(
            ApiResult.Failure(INVALID_SECRET),
            api.sendChat("http://127.0.0.1:3000", "секрет", messages, ""),
        )
    }
}
