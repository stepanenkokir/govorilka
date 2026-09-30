package com.govorilka.ui

import androidx.compose.runtime.Composable
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
}

@Composable
fun GovorilkaNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.CONVERSATIONS) {
        composable(Routes.CONVERSATIONS) {
            ConversationsScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
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
