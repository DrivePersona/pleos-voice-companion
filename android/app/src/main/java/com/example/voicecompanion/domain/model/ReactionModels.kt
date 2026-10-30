package com.example.voicecompanion.domain.model

/** 같은 순간에 여러 이벤트가 오면 순서가 앞선 쪽 하나만 말한다. 기획서 §6: 안전 > 상태 보고 > 연출. */
enum class Rank { SAFETY, STATUS, FLOURISH }

/**
 * 반응할 차량 이벤트. 대사는 기획서 v0.2 §5 예시 그대로다.
 * ponytail: cooldownMs는 기획서에서 N 미정 — 임시값. 에뮬레이터 실측 뒤 조정.
 */
enum class VehicleEvent(
    val label: String,
    val rank: Rank,
    val cooldownMs: Long,
    val face: String,
    val defaultLine: String,
    val neutral: Boolean = false, // 연출 없이 중립 음성으로(§6 "안전 이벤트는 연출하지 않습니다")
) {
    BRAKE("브레이크 밟기", Rank.FLOURISH, 10_000, "😱", "으앗!"),
    TURN("방향지시등 켜기", Rank.STATUS, 8_000, "😉", "깜빡이 켰어~"),
    MODE("드라이브 모드 바꾸기", Rank.FLOURISH, 3_000, "😎", "모드 체인지!"),
    TIRE("타이어 공기압 낮음", Rank.STATUS, 60_000, "🥺", "발이 좀 아파…"),
    WARNING("차량 경고", Rank.SAFETY, 10_000, "😐", "차량 경고가 있어요.", neutral = true),
}

enum class DropReason { COOLDOWN, PRIORITY, FOCUS, DRIVING_SILENT }

sealed interface LogEntry {
    val event: VehicleEvent

    /** 말하기를 '요청'했다. 실제로 재생됐는지는 플랫폼이 따로 알려 준다. */
    data class Speak(override val event: VehicleEvent, val text: String) : LogEntry

    data class Drop(
        override val event: VehicleEvent,
        val why: DropReason,
        val waitMs: Long = 0,
        val by: VehicleEvent? = null,
    ) : LogEntry
}
