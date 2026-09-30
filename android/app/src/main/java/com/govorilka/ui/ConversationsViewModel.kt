package com.govorilka.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.govorilka.domain.Conversation
import com.govorilka.domain.ConversationStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

data class OpenChat(val conversationId: String)

class ConversationsViewModel(private val store: ConversationStore) : ViewModel() {
    private val _conversations = mutableStateOf(emptyList<Conversation>())
    private val _pendingDelete = mutableStateOf<Conversation?>(null)
    private val _openChat = MutableSharedFlow<OpenChat>(extraBufferCapacity = 1)

    val conversations: State<List<Conversation>> = _conversations
    val pendingDelete: State<Conversation?> = _pendingDelete
    val openChat: SharedFlow<OpenChat> = _openChat

    init {
        viewModelScope.launch {
            store.observeAll().collect { _conversations.value = it }
        }
    }

    fun onNewConversation() {
        viewModelScope.launch { _openChat.emit(OpenChat(store.create().id)) }
    }

    fun onOpenConversation(id: String) {
        viewModelScope.launch { _openChat.emit(OpenChat(id)) }
    }

    fun onDeleteRequest(conversation: Conversation) {
        _pendingDelete.value = conversation
    }

    fun onDeleteDismiss() {
        _pendingDelete.value = null
    }

    fun onDeleteConfirm() {
        val conversation = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch { store.delete(conversation.id) }
    }
}
