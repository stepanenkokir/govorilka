package com.govorilka.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {
    private val _serverBaseUrl = mutableStateOf("")
    private val _appSecret = mutableStateOf("")
    private val _instructions = mutableStateOf("")

    val serverBaseUrl: State<String> = _serverBaseUrl
    val appSecret: State<String> = _appSecret
    val instructions: State<String> = _instructions

    fun onServerBaseUrlChange(value: String) {
        _serverBaseUrl.value = value
    }

    fun onAppSecretChange(value: String) {
        _appSecret.value = value
    }

    fun onInstructionsChange(value: String) {
        _instructions.value = value
    }
}
