package com.govorilka.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.govorilka.domain.ConversationStore
import kotlinx.coroutines.launch

class ChatViewModel(savedStateHandle: SavedStateHandle, store: ConversationStore) : ViewModel() {
    private val conversationId: String = checkNotNull(savedStateHandle[Routes.CONVERSATION_ID])

    private val _title = mutableStateOf<String?>(null)

    val title: State<String?> = _title
    val draft: State<String> = mutableStateOf("")
    val inputEnabled: State<Boolean> = mutableStateOf(false)
    val micEnabled: State<Boolean> = mutableStateOf(false)

    init {
        viewModelScope.launch {
            store.observe(conversationId).collect { _title.value = it?.title }
        }
    }

    fun onDraftChange(value: String) = Unit

    fun onSend() = Unit

    fun onMicClick() = Unit
}
