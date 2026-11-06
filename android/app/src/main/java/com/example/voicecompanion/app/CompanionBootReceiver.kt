package com.example.voicecompanion.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Starts the background session after Android finishes booting; it never launches an Activity. */
class CompanionBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            context.startService(
                Intent(context, CompanionRuntimeService::class.java)
                    .setAction(CompanionRuntimeService.ACTION_BACKGROUND),
            )
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Android did not allow the runtime service to start after boot", e)
        }
    }

    private companion object {
        const val TAG = "VoiceCompanionBoot"
    }
}
