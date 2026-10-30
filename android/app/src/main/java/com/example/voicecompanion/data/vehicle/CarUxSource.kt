package com.example.voicecompanion.data.vehicle


import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper

/**
 * 주행 규제(CarUxRestrictions)를 읽어 정차/주행을 알려 준다 (Pleos 주행 규제 개발 가이드).
 * 규제가 켜져 있으면(DO 필요) 주행으로 본다. 자동차 환경이 아니면(폰 이미지 등) 조용히 건너뛰고 이유만 알린다.
 */
class CarUxSource(
    private val context: Context,
    private val onDriving: (Boolean) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start() {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            onStatus("자동차 환경이 아니라서 주행 규제를 읽지 않아요")
            return
        }
        try {
            car = Car.createCar(context, Handler(Looper.getMainLooper()), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
                if (ready) attach(c) else onStatus("주행 규제 서비스가 끊겼어요")
            }
        } catch (t: Throwable) { // android.car가 없는 이미지의 NoClassDefFoundError 포함
            onStatus("주행 규제를 읽지 못했어요 (${t.javaClass.simpleName})")
        }
    }

    private fun attach(c: Car) {
        val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
        manager = m
        m.registerListener { onDriving(it.isRequiresDistractionOptimization) }
        onDriving(m.currentCarUxRestrictions.isRequiresDistractionOptimization)
        onStatus("주행 규제를 읽는 중이에요")
    }

    fun stop() {
        try {
            manager?.unregisterListener()
            car?.disconnect()
        } catch (_: Throwable) {
            // 이미 끊겼으면 할 일이 없다
        }
        manager = null
        car = null
    }
}
