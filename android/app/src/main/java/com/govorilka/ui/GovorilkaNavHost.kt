package com.govorilka.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object Routes {
    const val CONVERSATIONS = "conversations"
    const val SETTINGS = "settings"
    const val CONVERSATION_ID = "conversationId"
    const val CHAT = "chat/{$CONVERSATION_ID}"

    fun chat(conversationId: String) = "chat/$conversationId"
}

@Composable
fun GovorilkaNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.CONVERSATIONS) {
        composable(Routes.CONVERSATIONS) {
            val viewModel: ConversationsViewModel = viewModel(factory = GovorilkaViewModelFactory)
            LaunchedEffect(viewModel) {
                viewModel.openChat.collect { event ->
                    if (navController.currentDestination?.route == Routes.CONVERSATIONS) {
                        navController.navigate(Routes.chat(event.conversationId))
                    }
                }
            }
            ConversationsScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                viewModel = viewModel,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.navigateUp() })
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument(Routes.CONVERSATION_ID) { type = NavType.StringType }),
        ) {
            ChatScreen(onBack = { navController.navigateUp() })
        }
    }
}
