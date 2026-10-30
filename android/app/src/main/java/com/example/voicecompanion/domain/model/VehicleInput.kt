package com.example.voicecompanion.domain.model

/** 차량 SDK·시뮬레이터 등 어떤 입력원도 Runtime에 전달하는 공통 모델. */
sealed interface VehicleInput {
    data class Event(val event: VehicleEvent) : VehicleInput
    data class Driving(val isDriving: Boolean) : VehicleInput
    data class Status(val source: Source, val message: String) : VehicleInput

    enum class Source { PLEOS_VEHICLE, CAR_UX }
}
