package com.govorilka.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.govorilka.GovorilkaApp
import kotlin.reflect.KClass

object GovorilkaViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
        val container = (checkNotNull(extras[APPLICATION_KEY]) as GovorilkaApp).container
        val viewModel = when (modelClass) {
            ConversationsViewModel::class -> ConversationsViewModel(container.conversationStore)
            SettingsViewModel::class -> SettingsViewModel(container.settingsStore)
            ChatViewModel::class -> ChatViewModel(
                extras.createSavedStateHandle(),
                container.conversationStore,
                container.settingsStore,
                container.voiceCall,
                container.govorilkaApi,
                container.writeScope,
            )
            else -> error("Unknown ViewModel: $modelClass")
        }
        @Suppress("UNCHECKED_CAST")
        return viewModel as T
    }
}
