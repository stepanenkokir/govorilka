package com.govorilka.domain

import kotlinx.coroutines.flow.Flow

const val DEFAULT_INSTRUCTIONS =
    "Ты голосовой ассистент. Говори по-русски, кратко и естественно. " +
        "На сложные вопросы опирайся на ответ бэкенда и пересказывай его простым языком."

data class Settings(
    val serverBaseUrl: String,
    val appSecret: String,
    val instructions: String,
)

interface SettingsStore {
    val settings: Flow<Settings>

    suspend fun setServerBaseUrl(value: String)

    suspend fun setAppSecret(value: String)

    suspend fun setInstructions(value: String)
}
