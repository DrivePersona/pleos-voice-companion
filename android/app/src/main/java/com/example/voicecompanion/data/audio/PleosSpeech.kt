package com.example.voicecompanion.data.audio

import com.example.voicecompanion.voicepack.domain.SpeakRequest
import com.example.voicecompanion.voicepack.domain.Voice


import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Pleos 차량의 음성 합성(Pleos TextToSpeech SDK 3.3.1, ON_DEVICE — 서버 앱 등록이 필요 없다).
 * 안드로이드 TextToSpeech는 Pleos 이미지의 엔진을 못 찾아 쓸 수 없다. 소리는 차량의 Speech Service가 낸다.
 * 피치·속도·음량 API가 없어서 목소리 프리셋과 음량 상한은 적용되지 않는다(요청의 pitch·rate·volume은 쓰지 않는다).
 * 이벤트에 요청 번호가 없어 한 번에 하나만 추적한다. 오디오 포커스는 모른다. 콜백은 아무 스레드에서나 올 수 있다.
 */
class PleosSpeech(
    context: Context,
    attrs: AudioAttributes,
    private val onState: (String) -> Unit, // 음성 합성 준비 상태가 바뀔 때
) : Voice {
    private class Req(val note: (String) -> Unit, val done: () -> Unit)

    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)

    @Volatile
    private var state = "음성 합성을 준비하는 중이에요"

    @Volatile
    private var ready = false
    private val current = AtomicReference<Req?>(null) // 지금 말하는 요청

    private fun setState(text: String) {
        state = text
        onState(text)
    }

    /** 지금 요청을 마무리한다(note + done). 말하던 요청이 없으면 아무 일도 안 한다. */
    private fun finish(message: String?) {
        val r = current.getAndSet(null) ?: return
        message?.let(r.note)
        r.done()
    }

    private val events = object : EventListener {
        override fun onStart() {
            current.get()?.note?.invoke("재생됨")
        }

        override fun onDone() = finish(null)

        override fun onStop() = finish("끊김")

        override fun onError(errorType: ErrorType) = finish("재생 실패 · 오류 ${errorType.name}")

        override fun onReady() = Unit

        override fun onUpdatedRms(rms: Double) = Unit
    }

    init {
        try {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    tts.setAudioAttributes(attrs)
                    tts.setLocale(Locale.KOREA)
                    tts.addEventListener(events)
                    ready = true
                    setState("음성 합성 준비됨 (Pleos 차량 음성 · 목소리 프리셋은 적용되지 않아요)")
                }

                override fun onFailure(errorType: ErrorType) = setState("음성 합성을 쓸 수 없어요 · Pleos TTS ${errorType.name}")
            })
        } catch (e: Exception) { // TTS_SERVICE 퍼미션이 없으면 SecurityException
            setState("음성 합성을 쓸 수 없어요 · Pleos TTS ${e.javaClass.simpleName}: ${e.message?.take(80)}")
        }
    }

    /** 늘 요청을 받는다(못 말하면 note로 알리고 끝낸다). */
    override fun speak(request: SpeakRequest, note: (String) -> Unit, done: () -> Unit): Boolean {
        if (!ready) {
            note("재생 불가 · $state")
            done()
            return true
        }
        finish("끊김") // 서비스는 새 요청이 오면 말하던 소리를 끊는데 그때 onStop을 주지 않는다(에뮬레이터에서 확인). 이전 요청은 여기서 마무리한다
        current.set(Req(note, done))
        tts.speak(request.text, true)
        return true
    }

    override fun stop() {
        if (ready && current.get() != null) tts.stop() // 말하던 요청이 있을 때만. onStop이 와서 '끊김' + done이 된다
    }

    companion object {
        /** 이 기기에 Pleos Speech Service가 있나. SDK가 매니페스트에 이 액션의 <queries>를 넣어 줘서 보인다. */
        fun isAvailable(context: Context): Boolean = context.packageManager
            .queryIntentServices(Intent("ai.pleos.playground.caas.intent.action.SPEECH_START"), 0).isNotEmpty()
    }
}
