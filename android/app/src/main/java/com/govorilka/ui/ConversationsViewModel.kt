package com.govorilka.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.govorilka.domain.Conversation
import com.govorilka.domain.ConversationStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class OpenChat(val conversationId: String)

class ConversationsViewModel(private val store: ConversationStore) : ViewModel() {
    private val _conversations = mutableStateOf(emptyList<Conversation>())
    private val _pendingDelete = mutableStateOf<Conversation?>(null)
    private val _openChat = Channel<OpenChat>(Channel.BUFFERED)
    private var creating: Job? = null

    val conversations: State<List<Conversation>> = _conversations
    val pendingDelete: State<Conversation?> = _pendingDelete
    val openChat: Flow<OpenChat> = _openChat.receiveAsFlow()

    init {
        viewModelScope.launch {
            store.observeAll().collect { _conversations.value = it }
        }
    }

    fun onNewConversation() {
        if (creating?.isActive == true) return
        creating = viewModelScope.launch { _openChat.send(OpenChat(store.create().id)) }
    }

    fun onOpenConversation(id: String) {
        _openChat.trySend(OpenChat(id))
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
