package com.govorilka.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.govorilka.domain.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(store: SettingsStore) : ViewModel() {
    private val serverBaseUrlField = Field(store::setServerBaseUrl)
    private val appSecretField = Field(store::setAppSecret)
    private val instructionsField = Field(store::setInstructions)

    val serverBaseUrl: State<String> = serverBaseUrlField.state
    val appSecret: State<String> = appSecretField.state
    val instructions: State<String> = instructionsField.state

    init {
        viewModelScope.launch {
            val saved = store.settings.first()
            serverBaseUrlField.load(saved.serverBaseUrl)
            appSecretField.load(saved.appSecret)
            instructionsField.load(saved.instructions)
        }
    }

    fun onServerBaseUrlChange(value: String) = serverBaseUrlField.change(value)

    fun onAppSecretChange(value: String) = appSecretField.change(value)

    fun onInstructionsChange(value: String) = instructionsField.change(value)

    private inner class Field(private val save: suspend (String) -> Unit) {
        val state = mutableStateOf("")
        private var edited = false

        fun load(value: String) {
            if (!edited) state.value = value
        }

        fun change(value: String) {
            edited = true
            state.value = value
            viewModelScope.launch { save(value) }
        }
    }
}
