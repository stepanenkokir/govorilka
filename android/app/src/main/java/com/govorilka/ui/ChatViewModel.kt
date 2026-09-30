package com.govorilka.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

class ChatViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val conversationId: String = checkNotNull(savedStateHandle[Routes.CONVERSATION_ID])

    val title: State<String?> = mutableStateOf(null)
    val draft: State<String> = mutableStateOf("")
    val inputEnabled: State<Boolean> = mutableStateOf(false)
    val micEnabled: State<Boolean> = mutableStateOf(false)

    fun onDraftChange(value: String) = Unit

    fun onSend() = Unit

    fun onMicClick() = Unit
}
