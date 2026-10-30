package com.example.voicecompanion.app

import android.app.Application

/** 앱이 사는 동안 유지할 [CompanionRuntime]의 소유자다. */
class CompanionApp : Application() {
    val runtime by lazy { CompanionRuntime(this) }
}
