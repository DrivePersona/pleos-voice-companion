package com.example.voicecompanion.data.vehicle

import com.example.voicecompanion.domain.model.VehicleEvent


import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.DrivingModeType
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DrivingModeStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltNotBuckledWarningListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context

/**
 * Pleos Vehicle SDK 2.3.2에서 차량 신호를 읽어 반응 이벤트로 바꾼다. 읽기 전용(제어 API는 쓰지 않는다).
 *
 * 연결: 브레이크 페달(CAR_DRIVE) · 방향지시등(CAR_LIGHTS) · 드라이브 모드(CAR_DRIVE) · 안전벨트 경고(CAR_SAFETY).
 * 연결하지 않은 것:
 *  - 차량 경고(telltale)는 PRIVATE_CAR_SAFETY라 서드파티가 못 쓴다. 공개 API인 안전벨트 경고로 대신한다.
 *  - 타이어는 getLowTirePressure(Float) 값의 뜻을 문서에서 못 찾아 자동 반응에 쓰지 않는다. 시뮬레이터 버튼으로만 낸다.
 *
 * 콜백이 어느 스레드에서 오는지 문서에 없다. onEvent·onStatus를 받는 쪽이 메인으로 넘겨야 한다.
 * 차량 서비스가 없는 환경(폰 에뮬레이터 등)에선 연결 실패를 상태로 알리고 조용히 끝난다.
 */
class PleosVehicleSource(
    context: Context,
    private val onEvent: (VehicleEvent) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    private val vehicle = Vehicle(context.applicationContext)
    private val undo = mutableListOf<() -> Unit>()
    private var started = false

    fun start() {
        if (started) return
        try {
            vehicle.initialize()
        } catch (e: Exception) {
            onStatus("연결하지 못했어요 · ${describe(e)}")
            return
        }
        started = true
        val ok = mutableListOf<String>()
        val failed = mutableListOf<String>()
        // 하나가 실패해도 나머지는 계속 구독한다.
        fun subscribe(name: String, register: () -> Unit) {
            try {
                register()
                ok += name
            } catch (e: Exception) {
                failed += "$name(${describe(e)})"
            }
        }
        subscribe("브레이크") { watchBrake() }
        subscribe("방향지시등") { watchTurnSignal() }
        subscribe("드라이브 모드") { watchDrivingMode() }
        subscribe("안전벨트 경고") { watchSeatBelt() }
        summary = buildString {
            append("초기화됨") // initialize()가 예외 없이 끝났다는 뜻. 서비스에 실제로 붙었는지는 구독 결과가 말해 준다
            if (ok.isNotEmpty()) append(" · 구독 요청: ${ok.joinToString()}")
            if (failed.isNotEmpty()) append(" · 요청 실패: ${failed.joinToString()}")
            if (ok.isEmpty()) append(" · 폰 이미지처럼 Pleos 차량 서비스가 없는 환경이면 정상이에요")
        }
        publish()
    }

    // 구독 요청 결과(summary)와, 나중에 비동기로 오는 읽기 실패(failures)를 함께 보여 준다. 마지막 실패가 앞의 내용을 덮어쓰지 않게.
    private var summary = ""
    private val failures = LinkedHashSet<String>()

    private fun publish() {
        val text = synchronized(failures) {
            if (failures.isEmpty()) summary else "$summary\n읽기 실패: ${failures.joinToString(" / ")}"
        }
        onStatus(text)
    }

    private fun describe(e: Exception) = e.javaClass.simpleName + (e.message?.let { ": ${it.take(80)}" } ?: "")

    private fun fail(name: String, e: Exception) {
        synchronized(failures) { failures += "$name(${describe(e)})" }
        publish()
    }

    // 브레이크: 눌리기 시작하는 순간(false→true)만 이벤트로 본다.
    private fun watchBrake() {
        val brake = vehicle.getBrake()
        var pressed = false
        val listener = object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(status: Boolean?) {
                val now = status == true
                if (now && !pressed) onEvent(VehicleEvent.BRAKE)
                pressed = now
            }

            override fun onFailed(e: Exception) = fail("브레이크", e)
        }
        brake.registerBrakePedalStatus(listener)
        undo += { brake.unregisterBrakePedalStatus(listener) }
    }

    // 방향지시등: 구독 직후 첫 값은 기준으로만 쓰고, LEFT/RIGHT로 바뀔 때 이벤트로 본다.
    private fun watchTurnSignal() {
        val turn = vehicle.getTurnSignal()
        var last: TurnSignalValue? = null
        var seen = false
        val listener = object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
                val on = state == TurnSignalValue.LEFT || state == TurnSignalValue.RIGHT
                if (seen && on && state != last) onEvent(VehicleEvent.TURN)
                last = state
                seen = true
            }

            override fun onFailed(e: Exception) = fail("방향지시등", e)
        }
        turn.registerTurnSignalState(listener)
        undo += { turn.unregisterTurnSignalState(listener) }
    }

    // 드라이브 모드: 구독 직후 첫 값은 기준으로만 쓰고, 값이 바뀔 때 이벤트로 본다.
    private fun watchDrivingMode() {
        val mode = vehicle.getDrivingMode()
        var last: DrivingModeType? = null
        val listener = object : DrivingModeStateListener {
            override fun onDrivingModeStateUpdated(state: DrivingModeType?) {
                if (last != null && state != null && state != last) onEvent(VehicleEvent.MODE)
                if (state != null) last = state
            }

            override fun onFailed(e: Exception) = fail("드라이브 모드", e)
        }
        mode.registerDrivingModeState(listener)
        undo += { mode.unregisterDrivingModeState(listener) }
    }

    // 안전벨트 경고: 앞줄 두 좌석. 경고가 켜지는 순간(false→true)만 이벤트로 본다.
    private fun watchSeatBelt() {
        val safety = vehicle.getSafety()
        for (seat in listOf(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT)) {
            val area = SeatArea(seat)
            var warning = false
            val listener = object : SeatBeltNotBuckledWarningListener {
                override fun onSeatBeltNotBuckledWarningUpdated(status: Boolean?) {
                    val now = status == true
                    if (now && !warning) onEvent(VehicleEvent.WARNING)
                    warning = now
                }

                override fun onFailed(e: Exception) = fail("안전벨트 경고(${if (seat == SeatValue.ROW_1_LEFT) "1열 왼쪽" else "1열 오른쪽"})", e)
            }
            safety.registerSeatBeltNotBuckledWarning(area, listener)
            undo += { safety.unregisterSeatBeltNotBuckledWarning(area, listener) }
        }
    }

    fun stop() {
        if (!started) return
        undo.forEach { runCatching(it) }
        undo.clear()
        runCatching { vehicle.release() }
        started = false
    }
}
