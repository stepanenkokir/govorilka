package com.govorilka

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.govorilka.data.DataStoreSettingsStore
import com.govorilka.data.GovorilkaDatabase
import com.govorilka.data.RoomConversationStore
import com.govorilka.data.remote.OkHttpGovorilkaApi
import com.govorilka.domain.ConversationStore
import com.govorilka.domain.GovorilkaApi
import com.govorilka.domain.SettingsStore

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder<GovorilkaDatabase>(context, "govorilka.db")
        .setDriver(AndroidSQLiteDriver())
        .build()

    val conversationStore: ConversationStore = RoomConversationStore(database.conversationDao())
    val settingsStore: SettingsStore = DataStoreSettingsStore(context)
    val govorilkaApi: GovorilkaApi = OkHttpGovorilkaApi()
}
