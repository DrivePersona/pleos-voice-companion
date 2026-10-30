package com.example.voicecompanion.app.model

import com.example.voicecompanion.data.audio.VoicePreset
import com.example.voicecompanion.domain.model.VehicleEvent
import com.example.voicecompanion.voicepack.domain.VoicePack
import com.example.voicecompanion.characterpack.model.CharacterPack

/** 화면이 렌더링하는 읽기 전용 상태. UI는 Runtime의 가변 상태를 직접 읽지 않는다. */
data class CompanionUiState(
    val driving: Boolean,
    val voiceWhileDriving: Boolean,
    val navBusy: Boolean,
    val preset: VoicePreset,
    val pleosStatus: String,
    val carUxStatus: String,
    val ttsStatus: String,
    val typedLines: Map<VehicleEvent, String>,
    val logs: List<ReactionLogItem>,
    val packList: List<VoicePack>,
    val activePack: VoicePack,
    val packsLoaded: Boolean,
    val packStatus: String?,
    val previewStatus: String,
    val fixedLines: Map<VehicleEvent, String?>,
    val characterPackList: List<CharacterPack>,
    val activeCharacterPack: CharacterPack?,
    val characterPacksLoaded: Boolean,
    val characterPackNotice: String?,
    val vrEnabled: Boolean,
    val vrOverlayStatus: String,
    val vrOverlayPermissionRequired: Boolean,
    val canStartVr: Boolean,
) {
    fun fixedLine(event: VehicleEvent): String? = fixedLines[event]
}

data class ReactionLogItem(
    val time: String,
    val text: String,
    val note: String,
)
