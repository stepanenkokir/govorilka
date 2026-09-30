package com.govorilka.data.remote

import com.govorilka.domain.ApiResult
import com.govorilka.domain.ChatLine
import com.govorilka.domain.GovorilkaApi
import com.govorilka.domain.LiveSession
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response

private const val SESSION_TIMEOUT_SECONDS = 30L
private const val CHAT_TIMEOUT_SECONDS = 60L

class OkHttpGovorilkaApi : GovorilkaApi {
    // The whole-call timeout governs; the default 10 s read timeout would cut off slow model replies.
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    override suspend fun createSession(
        baseUrl: String,
        appSecret: String,
        sdp: String,
        instructions: String,
    ): ApiResult<LiveSession> =
        execute(baseUrl, SESSION_PATH, appSecret, sessionBody(sdp, instructions), SESSION_TIMEOUT_SECONDS, ::parseSession)

    override suspend fun sendChat(
        baseUrl: String,
        appSecret: String,
        messages: List<ChatLine>,
        instructions: String,
    ): ApiResult<String> =
        execute(baseUrl, CHAT_PATH, appSecret, chatBody(messages, instructions), CHAT_TIMEOUT_SECONDS, ::parseChat)

    private suspend fun <T> execute(
        baseUrl: String,
        path: String,
        appSecret: String,
        body: String,
        timeoutSeconds: Long,
        parse: (Int, String) -> ApiResult<T>,
    ): ApiResult<T> {
        if (baseUrl.isBlank()) return ApiResult.Failure(MISSING_BASE_URL)
        val url = endpoint(baseUrl, path) ?: return ApiResult.Failure(INVALID_BASE_URL)
        val request = try {
            buildRequest(url, appSecret, body)
        } catch (_: IllegalArgumentException) {
            return ApiResult.Failure(INVALID_SECRET)
        }
        val call = client.newCall(request)
        call.timeout().timeout(timeoutSeconds, TimeUnit.SECONDS)
        return call.await(parse)
    }
}

// The body is read on OkHttp's thread so callers on the main dispatcher never do blocking I/O.
private suspend fun <T> Call.await(parse: (Int, String) -> ApiResult<T>): ApiResult<T> =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(ApiResult.Failure(NETWORK_FAILURE))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use { parse(it.code, it.body?.string().orEmpty()) }
                } catch (_: IOException) {
                    ApiResult.Failure(NETWORK_FAILURE)
                }
                continuation.resume(result)
            }
        })
    }
