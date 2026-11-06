package com.example.voicecompanion.app

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/** Owns the vehicle/runtime session after the setup Activity is closed. */
class CompanionRuntimeService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val runtime = (application as CompanionApp).runtime
        if (intent?.action == ACTION_BACKGROUND || intent == null) {
            runtime.startBackgroundSession()
        } else {
            runtime.start()
            runtime.setSettingsVisible(true)
        }
        Log.i(TAG, "runtime started; action=${intent?.action ?: "sticky restart"}")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_BACKGROUND = "com.example.voicecompanion.action.START_BACKGROUND"
        const val ACTION_SETTINGS = "com.example.voicecompanion.action.START_SETTINGS"
        private const val TAG = "VoiceCompanionService"
    }
}
