package com.example.voicecompanion.ui.viewmodel

import com.example.voicecompanion.app.CompanionUiRuntime
import com.example.voicecompanion.ui.event.CompanionTouch
import com.example.voicecompanion.ui.event.CompanionUiEffect

/** 터치 이벤트를 앱 동작으로 한곳에서 변환한다. 단위 테스트는 Android ViewModel 없이 이 규칙을 검증한다. */
internal fun routeTouch(runtime: CompanionUiRuntime, touch: CompanionTouch): CompanionUiEffect? = when (touch) {
    is CompanionTouch.LineChanged -> { runtime.setLine(touch.event, touch.text); null }
    is CompanionTouch.PresetSelected -> { runtime.choosePreset(touch.preset); null }
    is CompanionTouch.PackSelected -> { runtime.selectPack(touch.id); null }
    is CompanionTouch.PackPreviewRequested -> { runtime.previewPack(touch.pack); null }
    is CompanionTouch.CharacterPackSelected -> { runtime.selectCharacterPack(touch.id); null }
    is CompanionTouch.VrEnabledChanged -> { runtime.changeVrEnabled(touch.enabled); null }
    CompanionTouch.VrPermissionRequested -> { runtime.openVrOverlayPermissionSettings(); null }
    CompanionTouch.VrStarted -> if (runtime.beginVr()) CompanionUiEffect.MoveTaskToBackForVr else null
    is CompanionTouch.DrivingChanged -> { runtime.changeDriving(touch.driving); null }
    is CompanionTouch.VoiceWhileDrivingChanged -> { runtime.changeVoiceWhileDriving(touch.enabled); null }
    is CompanionTouch.NavigationBusyChanged -> { runtime.changeNavBusy(touch.busy); null }
    is CompanionTouch.VehicleEventRequested -> { runtime.emit(touch.event); null }
    CompanionTouch.EventBurstRequested -> { runtime.burst(); null }
    CompanionTouch.LogsCleared -> { runtime.clearLogs(); null }
}
