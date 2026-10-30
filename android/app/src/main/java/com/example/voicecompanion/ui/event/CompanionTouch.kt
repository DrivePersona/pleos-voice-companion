package com.example.voicecompanion.ui.event

import com.example.voicecompanion.data.audio.VoicePreset
import com.example.voicecompanion.domain.model.VehicleEvent
import com.example.voicecompanion.voicepack.domain.VoicePack

/** Compose에서 발생한 터치·입력. 화면 요소는 Runtime을 직접 호출하지 않는다. */
sealed interface CompanionTouch {
    data class LineChanged(val event: VehicleEvent, val text: String) : CompanionTouch
    data class PresetSelected(val preset: VoicePreset) : CompanionTouch
    data class PackSelected(val id: String) : CompanionTouch
    data class PackPreviewRequested(val pack: VoicePack) : CompanionTouch
    data class CharacterPackSelected(val id: String) : CompanionTouch
    data class VrEnabledChanged(val enabled: Boolean) : CompanionTouch
    data object VrPermissionRequested : CompanionTouch
    data object VrStarted : CompanionTouch
    data class DrivingChanged(val driving: Boolean) : CompanionTouch
    data class VoiceWhileDrivingChanged(val enabled: Boolean) : CompanionTouch
    data class NavigationBusyChanged(val busy: Boolean) : CompanionTouch
    data class VehicleEventRequested(val event: VehicleEvent) : CompanionTouch
    data object EventBurstRequested : CompanionTouch
    data object LogsCleared : CompanionTouch
}

/** Activity처럼 시스템 UI를 소유한 화면 계층에서만 처리할 일회성 효과. */
sealed interface CompanionUiEffect {
    data object MoveTaskToBackForVr : CompanionUiEffect
}
