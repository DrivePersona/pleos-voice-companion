package com.example.voicecompanion.ui.overlay


/**
 * 캐릭터 오버레이를 띄워도 되는 조건을 Android UI와 분리해 둔다.
 *
 * 차량 안전 규칙은 여기서 닫힌 쪽으로 판단한다. 즉 정차 여부·팩 준비 상태·권한 중 하나라도
 * 확실하지 않으면 표시하지 않는다.
 */
data class VrOverlayInput(
    val enabled: Boolean,
    val stationary: Boolean,
    val settingsVisible: Boolean,
    val readyCharacter: Boolean,
    val overlayPermissionGranted: Boolean,
)

object VrOverlayPolicy {
    fun shouldShow(input: VrOverlayInput): Boolean =
        input.enabled &&
            input.stationary &&
            !input.settingsVisible &&
            input.readyCharacter &&
            input.overlayPermissionGranted
}
