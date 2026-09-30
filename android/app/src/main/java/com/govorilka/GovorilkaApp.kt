package com.govorilka

import android.app.Application
import org.webrtc.PeerConnectionFactory

class GovorilkaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions(),
        )
        container = AppContainer(this)
    }
}
