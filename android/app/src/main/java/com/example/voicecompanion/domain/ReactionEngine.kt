package com.example.voicecompanion.domain

import com.example.voicecompanion.domain.model.DropReason
import com.example.voicecompanion.domain.model.LogEntry
import com.example.voicecompanion.domain.model.VehicleEvent

// 보이스 컴패니언 반응 엔진: 차량 이벤트 in → 음성·아바타 out.
// 안드로이드도 Pleos SDK도 모른다(웹 시뮬레이터 engine.js의 이식). 바깥 세계는 Platform으로만 만난다.

/** 엔진이 바깥 세계와 만나는 곳. 안드로이드 구현은 AndroidPlatform, 테스트는 가짜. */
interface Platform {
    fun speak(event: VehicleEvent, text: String, neutral: Boolean, volume: Float)
    fun stop()
    fun requestFocus(): Boolean
    fun showAvatar(face: String, text: String)
    fun hideAvatar()
    fun log(entry: LogEntry)
}

/** 한 스레드(메인)에서만 부른다. */
class ReactionEngine(
    private val platform: Platform,
    private val now: () -> Long,
    /** delayMs 뒤에 block을 실행하고, 취소 함수를 돌려준다. */
    private val schedule: (delayMs: Long, block: () -> Unit) -> () -> Unit,
    private val windowMs: Long = 300, // 이 시간 안에 모인 이벤트끼리 경쟁한다
    private val maxVolume: Float = 0.8f, // 음량 상한(§6)
) {
    // 상태를 아직 못 읽은 기동 직후는 주행으로 본다. 닫힌 쪽이 안전(§6 정차 게이트).
    private var driving = true
    private var voiceWhileDriving = true
    private val last = HashMap<VehicleEvent, Long>()
    private val pending = LinkedHashSet<VehicleEvent>()
    private var cancelTimer: (() -> Unit)? = null
    private val lines = VehicleEvent.entries.associateWith { it.defaultLine }.toMutableMap()

    fun line(event: VehicleEvent): String = lines.getValue(event)

    /** 비우면 기본 대사로 돌아간다. 말없는 이벤트가 생기지 않게. */
    fun setLine(event: VehicleEvent, text: String) {
        lines[event] = text.trim().ifEmpty { event.defaultLine }
    }

    fun emit(event: VehicleEvent) {
        pending += event
        if (cancelTimer == null) cancelTimer = schedule(windowMs) { flush() }
    }

    fun flush() {
        cancelTimer?.invoke()
        cancelTimer = null
        val batch = pending.toList()
        pending.clear()
        val t = now()
        // 쿨다운을 먼저 거른다. 쿨다운 중인 이벤트가 우선순위 경쟁에서 다른 이벤트를 막으면 안 된다.
        val ready = batch.filter { e ->
            val wait = last[e]?.let { it + e.cooldownMs - t } ?: 0L
            if (wait > 0) platform.log(LogEntry.Drop(e, DropReason.COOLDOWN, waitMs = wait))
            wait <= 0
        }
        val sorted = ready.sortedBy { it.rank } // 안정 정렬: 동률이면 먼저 온 쪽
        val win = sorted.firstOrNull() ?: return
        // 밀려난 이벤트는 버린다. 늦게 말하면 의미가 없다(§6 반응 큐).
        sorted.drop(1).forEach { platform.log(LogEntry.Drop(it, DropReason.PRIORITY, by = win)) }
        react(win, t)
    }

    private fun react(e: VehicleEvent, t: Long) {
        if (driving && !voiceWhileDriving) return drop(e, DropReason.DRIVING_SILENT)
        if (!platform.requestFocus()) return drop(e, DropReason.FOCUS)
        last[e] = t // 실제로 말할 때만 쿨다운을 쓴다
        val text = line(e)
        // 여기서 아는 건 '요청함'까지. 실제로 재생됐는지는 플랫폼이 따로 알려 준다.
        platform.log(LogEntry.Speak(e, text))
        platform.speak(e, text, e.neutral, maxVolume)
        if (!driving && !e.neutral) platform.showAvatar(e.face, text)
    }

    private fun drop(e: VehicleEvent, why: DropReason) = platform.log(LogEntry.Drop(e, why))

    fun setDriving(value: Boolean) {
        driving = value
        if (!value) return
        platform.hideAvatar() // 주행이 시작되면 연출을 멈추고 UI를 숨긴다(§3)
        if (!voiceWhileDriving) platform.stop()
    }

    fun setVoiceWhileDriving(value: Boolean) {
        voiceWhileDriving = value
        if (driving && !value) platform.stop()
    }

    /** 내비·통화·긴급 안내에 바로 양보한다(§6 오디오 포커스). */
    fun focusLost() = platform.stop()
}
