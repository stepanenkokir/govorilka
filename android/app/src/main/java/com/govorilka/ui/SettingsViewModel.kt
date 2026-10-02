package com.govorilka.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.govorilka.domain.SettingsStore
import com.govorilka.domain.Voice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(store: SettingsStore) : ViewModel() {
    private val serverBaseUrlField = Field("", store::setServerBaseUrl)
    private val appSecretField = Field("", store::setAppSecret)
    private val instructionsField = Field("", store::setInstructions)
    private val voiceField = Field(Voice.DEFAULT, store::setVoice)
    private val webSearchField = Field(false, store::setWebSearch)

    val serverBaseUrl: State<String> = serverBaseUrlField.state
    val appSecret: State<String> = appSecretField.state
    val instructions: State<String> = instructionsField.state
    val voice: State<Voice> = voiceField.state
    val webSearch: State<Boolean> = webSearchField.state

    init {
        viewModelScope.launch {
            val saved = store.settings.first()
            serverBaseUrlField.load(saved.serverBaseUrl)
            appSecretField.load(saved.appSecret)
            instructionsField.load(saved.instructions)
            voiceField.load(saved.voice)
            webSearchField.load(saved.webSearch)
        }
    }

    fun onServerBaseUrlChange(value: String) = serverBaseUrlField.change(value)

    fun onAppSecretChange(value: String) = appSecretField.change(value)

    fun onInstructionsChange(value: String) = instructionsField.change(value)

    fun onVoiceChange(value: Voice) = voiceField.change(value)

    fun onWebSearchChange(value: Boolean) = webSearchField.change(value)

    private inner class Field<T>(initial: T, private val save: suspend (T) -> Unit) {
        val state = mutableStateOf(initial)
        private var edited = false

        fun load(value: T) {
            if (!edited) state.value = value
        }

        fun change(value: T) {
            edited = true
            state.value = value
            viewModelScope.launch { save(value) }
        }
    }
}
