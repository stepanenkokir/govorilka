package com.govorilka.domain

import kotlinx.coroutines.flow.Flow

const val DEFAULT_INSTRUCTIONS =
    "Ты голосовой ассистент. Говори по-русски, кратко и естественно. " +
        "На сложные вопросы опирайся на ответ бэкенда и пересказывай его простым языком."

enum class Voice(val wireName: String) {
    Gleam("gleam"),
    Meridian("meridian"),
    Delta("delta"),
    Cinder("cinder");

    companion object {
        val DEFAULT = Gleam

        fun fromWireName(value: String?): Voice = entries.firstOrNull { it.wireName == value } ?: DEFAULT
    }
}

data class Settings(
    val serverBaseUrl: String,
    val appSecret: String,
    val instructions: String,
    val voice: Voice,
    val webSearch: Boolean,
)

interface SettingsStore {
    val settings: Flow<Settings>

    suspend fun setServerBaseUrl(value: String)

    suspend fun setAppSecret(value: String)

    suspend fun setInstructions(value: String)

    suspend fun setVoice(value: Voice)

    suspend fun setWebSearch(value: Boolean)
}
