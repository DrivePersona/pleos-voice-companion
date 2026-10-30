package com.example.voicecompanion.data.audio

import com.example.voicecompanion.voicepack.domain.SpeakRequest
import com.example.voicecompanion.voicepack.domain.Voice


import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val PREROLL_MS = 150L // Pleos 선행 묵음 가이드: 최소 150ms, 권장 150~200ms

/**
 * 안드로이드 기본 TextToSpeech(폰). Pleos 이미지에선 엔진을 못 찾아 쓸 수 없다([PleosSpeech]가 대신한다).
 * 오디오 포커스는 모른다. 콜백은 아무 스레드에서나 올 수 있다.
 */
class AndroidTtsVoice(
    context: Context,
    private val attrs: AudioAttributes,
    private val onState: (String) -> Unit, // 음성 합성 준비 상태가 바뀔 때
) : Voice {
    private class Req(val note: (String) -> Unit, val done: () -> Unit)

    @Volatile
    private var state = "음성 합성을 준비하는 중이에요"

    @Volatile
    private var ready = false
    private var tts: TextToSpeech? = null
    private var counter = 0
    private val requests = ConcurrentHashMap<String, Req>()

    private fun setState(text: String) {
        state = text
        onState(text)
    }

    /** 요청 하나를 마무리한다(note + done). 선행 묵음이나 이미 끝난 요청이면 아무 일도 안 한다. */
    private fun finish(id: String?, message: String?) {
        val r = requests.remove(id ?: return) ?: return
        message?.let(r.note)
        r.done()
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            requests[utteranceId ?: return]?.note?.invoke("재생됨")
        }

        override fun onDone(utteranceId: String?) = finish(utteranceId, null)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finish(utteranceId, "재생 실패 · 오류 -1")

        override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId, "재생 실패 · 오류 $errorCode")

        override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId, "끊김")
    }

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val t = tts
            if (status != TextToSpeech.SUCCESS || t == null) {
                setState("음성 합성을 쓸 수 없어요")
            } else {
                val lang = t.setLanguage(Locale.KOREAN)
                val hasKorean = lang != TextToSpeech.LANG_MISSING_DATA && lang != TextToSpeech.LANG_NOT_SUPPORTED
                t.setAudioAttributes(attrs)
                t.setOnUtteranceProgressListener(progress)
                ready = true
                setState(if (hasKorean) "음성 합성 준비됨" else "한국어 음성이 없어 기본 음성으로 읽어요. 발음이 어색할 수 있어요")
            }
        }
    }

    /** 늘 요청을 받는다(못 말하면 note로 알리고 끝낸다). 새 소리는 이전 소리를 끊는다(QUEUE_FLUSH → 이전 요청의 onStop). */
    override fun speak(request: SpeakRequest, note: (String) -> Unit, done: () -> Unit): Boolean {
        val t = tts
        if (t == null || !ready) {
            note("재생 불가 · $state")
            done()
            return true
        }
        val id = "u${++counter}"
        requests[id] = Req(note, done)
        t.setPitch(request.pitch)
        t.setSpeechRate(request.rate)
        // 앞부분이 잘리지 않게 무음을 먼저 재생한다(Pleos 선행 묵음 가이드). QUEUE_FLUSH가 이전 소리를 끊는다.
        t.playSilentUtterance(PREROLL_MS, TextToSpeech.QUEUE_FLUSH, "pre$id")
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, request.volume) }
        val result = t.speak(request.text, TextToSpeech.QUEUE_ADD, params, id)
        if (result != TextToSpeech.SUCCESS) finish(id, "재생 실패 · speak=$result")
        return true
    }

    override fun stop() {
        tts?.stop() // 말하던 요청이 있으면 onStop이 와서 '끊김' + done이 된다
    }
}
