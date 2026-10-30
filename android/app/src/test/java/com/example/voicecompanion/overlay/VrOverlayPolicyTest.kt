package com.example.voicecompanion.ui.overlay


import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VrOverlayPolicyTest {
    private fun input(
        enabled: Boolean = true,
        stationary: Boolean = true,
        settingsVisible: Boolean = false,
        readyCharacter: Boolean = true,
        overlayPermissionGranted: Boolean = true,
    ) = VrOverlayInput(enabled, stationary, settingsVisible, readyCharacter, overlayPermissionGranted)

    @Test
    fun `모든 조건이 충족되면 정차 배경 위에 표시한다`() {
        assertTrue(VrOverlayPolicy.shouldShow(input()))
    }

    @Test
    fun `주행 중에는 VR을 켜도 즉시 숨긴다`() {
        assertFalse(VrOverlayPolicy.shouldShow(input(stationary = false)))
    }

    @Test
    fun `설정 화면이나 미준비 팩 권한 없음에서는 표시하지 않는다`() {
        assertFalse(VrOverlayPolicy.shouldShow(input(settingsVisible = true)))
        assertFalse(VrOverlayPolicy.shouldShow(input(readyCharacter = false)))
        assertFalse(VrOverlayPolicy.shouldShow(input(overlayPermissionGranted = false)))
    }
}
