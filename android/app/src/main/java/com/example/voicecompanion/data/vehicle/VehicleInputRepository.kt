package com.example.voicecompanion.data.vehicle

import android.content.Context
import com.example.voicecompanion.domain.model.VehicleInput

/** 차량 입력원을 바꿔 끼우기 위한 저장소 계약. 테스트·시뮬레이터·다른 차량 SDK도 같은 모델을 전달한다. */
interface VehicleInputRepository {
    fun start(onInput: (VehicleInput) -> Unit)
    fun stop()
}

/** Pleos Vehicle SDK와 CarUxRestrictions를 공통 [VehicleInput] 모델로 바꾼다. */
class PleosVehicleInputRepository(
    private val context: Context,
) : VehicleInputRepository {
    private var carUx: CarUxSource? = null
    private var pleos: PleosVehicleSource? = null
    private var started = false

    override fun start(onInput: (VehicleInput) -> Unit) {
        if (started) return
        started = true
        carUx = CarUxSource(
            context = context,
            onDriving = { onInput(VehicleInput.Driving(it)) },
            onStatus = { onInput(VehicleInput.Status(VehicleInput.Source.CAR_UX, it)) },
        ).also { it.start() }
        pleos = PleosVehicleSource(
            context = context,
            onEvent = { onInput(VehicleInput.Event(it)) },
            onStatus = { onInput(VehicleInput.Status(VehicleInput.Source.PLEOS_VEHICLE, it)) },
        ).also { it.start() }
    }

    override fun stop() {
        carUx?.stop()
        pleos?.stop()
        carUx = null
        pleos = null
        started = false
    }
}
