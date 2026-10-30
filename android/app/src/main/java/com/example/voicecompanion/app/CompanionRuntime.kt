package com.example.voicecompanion.app

import com.example.voicecompanion.data.audio.AndroidAudio
import com.example.voicecompanion.data.audio.VoicePreset
import com.example.voicecompanion.data.vehicle.PleosVehicleInputRepository
import com.example.voicecompanion.data.vehicle.VehicleInputRepository
import com.example.voicecompanion.domain.Platform
import com.example.voicecompanion.domain.ReactionEngine
import com.example.voicecompanion.domain.model.DropReason
import com.example.voicecompanion.domain.model.LogEntry
import com.example.voicecompanion.domain.model.VehicleEvent
import com.example.voicecompanion.domain.model.VehicleInput
import com.example.voicecompanion.app.model.CompanionUiState
import com.example.voicecompanion.app.model.ReactionLogItem
import com.example.voicecompanion.ui.overlay.CharacterOverlayHost
import com.example.voicecompanion.ui.overlay.OverlayShowResult
import com.example.voicecompanion.ui.overlay.VrOverlayInput
import com.example.voicecompanion.ui.overlay.VrOverlayPolicy
import com.example.voicecompanion.voicepack.data.AssetPackSource
import com.example.voicecompanion.voicepack.data.PackRepository
import com.example.voicecompanion.voicepack.domain.ClipPackFactory
import com.example.voicecompanion.voicepack.domain.PackKind
import com.example.voicecompanion.voicepack.domain.PackSelector
import com.example.voicecompanion.voicepack.domain.PackState
import com.example.voicecompanion.voicepack.domain.SpeakRequest
import com.example.voicecompanion.voicepack.domain.VoicePack
import com.example.voicecompanion.characterpack.data.AssetCharacterPackSource
import com.example.voicecompanion.characterpack.data.CharacterPackRepository
import com.example.voicecompanion.characterpack.domain.CharacterPackSelector
import com.example.voicecompanion.characterpack.model.CharacterPack
import com.example.voicecompanion.characterpack.model.CharacterPackState
import com.example.voicecompanion.characterpack.model.isRenderable


import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 반응 기록 한 줄. 재생 결과('재생됨'·'끊김')는 나중에 note에 덧붙는다. */
class LogRow(val time: String, val text: String) {
    var note by mutableStateOf("")
}

/**
 * 앱이 사는 동안 하나. 화면(Activity)이 닫혀도 — 주행 중엔 UI가 막힌다 — 엔진과 차량 연결은 이어진다.
 * 상태는 전부 Compose 상태라 화면이 다시 만들어져도 그대로 보인다.
 */
class CompanionRuntime(private val app: Application) : Platform, CompanionUiRuntime {
    private val main = Handler(Looper.getMainLooper())
    private val prefs = app.getSharedPreferences("voice-companion", Context.MODE_PRIVATE)
    private val vehicleInputs: VehicleInputRepository = PleosVehicleInputRepository(app)
    private val lastSpeakRow = HashMap<VehicleEvent, LogRow>()
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    // 화면 상태
    var driving by mutableStateOf(false)
        private set
    var voiceWhileDriving by mutableStateOf(true)
        private set
    var navBusy by mutableStateOf(false)
        private set
    var face by mutableStateOf("🙂")
        private set
    var bubble by mutableStateOf(IDLE_BUBBLE)
        private set
    var preset by mutableStateOf(VoicePreset.BRIGHT)
        private set
    var pleosStatus by mutableStateOf("시작 전")
        private set
    var carUxStatus by mutableStateOf("시작 전")
        private set
    var ttsStatus by mutableStateOf("음성 합성을 준비하는 중이에요")
        private set
    val typedLines = mutableStateMapOf<VehicleEvent, String>()
    val logs = mutableStateListOf<LogRow>()

    // 위 상태 프로퍼티보다 뒤에 둔다. 앞에 두면 생성 중에 콜백이 아직 만들어지지 않은 상태 값을 건드려 NPE가 난다.
    private val audio = AndroidAudio(
        app,
        onFocusLost = { onMain { engine.focusLost() } },
        onState = { s -> onMain { ttsStatus = s } },
    )

    // 어느 팩이 말할지·대사가 무엇인지는 여기서 정한다. 설치된 파일 팩은 시작할 때 백그라운드에서 읽어 채운다.
    @Volatile
    private var installed: List<VoicePack> = emptyList()
    private val packs = PackSelector(audio.builtinPack) { installed }
    private val repository = PackRepository(
        source = AssetPackSource(app.assets, "voicepacks"),
        factories = mapOf(ClipPackFactory.KIND to ClipPackFactory(newOutput = audio::newPcmOutput)),
        allowedEvents = VehicleEvent.entries.filter { !it.neutral }.map { it.name }.toSet(), // 중립 음성 이벤트는 팩을 쓰지 않는다
        appVersion = app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode,
    )
    // 캐릭터 팩(GLB·2D 스프라이트)은 음성 팩과 완전히 별개다. 표현 파일·선택·검증만 맡고 음성 경로에는 관여하지 않는다.
    @Volatile
    private var installedCharacters: List<CharacterPack> = emptyList()
    private val characters = CharacterPackSelector { installedCharacters }
    private val characterRepository = CharacterPackRepository(
        source = AssetCharacterPackSource(app.assets, "characterpacks"),
        allowedEvents = VehicleEvent.entries.filter { !it.neutral }.map { it.name }.toSet(),
        appVersion = app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode,
    )
    private var speakSeq = 0 // 가장 최근 말하기 요청의 번호. 늦게 온 이전 요청의 done이 새 요청의 포커스를 놓지 않게 한다
    private var previewSeq = -1 // 가장 최근 미리듣기 요청의 번호. 주행이 시작될 때 그 소리가 아직 최근 요청이면 끊는다

    // 음성 팩 화면 상태. 선택기(packs)는 Compose가 모르는 일반 객체라서, 바뀔 때마다 refreshPacks()가 여기로 옮겨 적는다.
    var packList by mutableStateOf<List<VoicePack>>(listOf(audio.builtinPack))
        private set
    var activePack by mutableStateOf(audio.builtinPack) // 지금 말하고 대사를 정하는 팩(고른 팩이 못 쓰는 상태면 기본 음성)
        private set
    var packsLoaded by mutableStateOf(false) // 설치된 팩 읽기가 끝났나(그 전엔 기본 음성만 보인다)
        private set
    var packNotice by mutableStateOf<String?>(null) // 저장해 둔 팩을 못 써서 기본 음성으로 말하는 이유
        private set
    var previewStatus by mutableStateOf("")
        private set

    // 캐릭터 팩 화면 상태. 선택한 캐릭터는 설정 화면이 아닌 WindowManager 오버레이에만 올린다.
    var characterPackList by mutableStateOf<List<CharacterPack>>(emptyList())
        private set
    var activeCharacterPack by mutableStateOf<CharacterPack?>(null)
        private set
    var characterPacksLoaded by mutableStateOf(false)
        private set
    var characterPackNotice by mutableStateOf<String?>(null)
        private set
    var characterAnimation by mutableStateOf("idle")
        private set
    var vrEnabled by mutableStateOf(false)
        private set
    var vrOverlayStatus by mutableStateOf("VR 모드가 꺼져 있어요")
        private set
    var vrOverlayPermissionRequired by mutableStateOf(false)
        private set
    private var settingsVisible = true
    private val characterOverlay = CharacterOverlayHost(app, prefs)

    /** 'VR 시작'을 눌러도 되는지. 이 값이 false면 설정 화면은 그대로 남는다. */
    val canStartVr: Boolean
        get() = vrEnabled &&
            !driving &&
            activeCharacterPack?.isRenderable == true &&
            characterOverlay.permissionGranted

    /** 상태줄에 보일 음성 팩 안내. 알릴 게 없으면 null. */
    val packStatus: String?
        get() = packNotice ?: activePack.takeIf { it.kind == PackKind.CLIP }?.let {
            "음성 팩 ${it.name} · 녹음 클립이라 목소리 프리셋은 적용되지 않아요 · 클립이 없는 반응은 기본 음성이 읽어요"
        }

    val engine = ReactionEngine(
        platform = this,
        now = SystemClock::elapsedRealtime,
        schedule = { delayMs, block ->
            val r = Runnable(block)
            main.postDelayed(r, delayMs)
            val cancel: () -> Unit = { main.removeCallbacks(r) }
            cancel
        },
        maxVolume = MAX_VOLUME,
    )

    private var started = false
    override fun start() {
        if (started) return
        started = true
        vrEnabled = prefs.getBoolean(PREF_VR_ENABLED, false)
        VehicleEvent.entries.forEach { e ->
            val saved = prefs.getString("line.${e.name}", null)?.take(MAX_LINE)
            typedLines[e] = saved ?: e.defaultLine
        }
        refreshLines()
        prefs.getString("preset", null)?.let { name -> VoicePreset.entries.find { it.name == name }?.let { preset = it } }

        engine.setVoiceWhileDriving(voiceWhileDriving)
        // 자동차에선 주행 규제가 알려 줄 때까지 주행으로 본다(닫힌 쪽이 안전). 폰에선 정차로 시작해 시뮬레이터를 쓸 수 있게 한다.
        val automotive = app.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        changeDriving(automotive)
        vehicleInputs.start(::acceptVehicleInput)
        Thread({
            val list = repository.load()
            onMain {
                installed = list
                packNotice = packs.restore(prefs.getString(PREF_PACK, null))?.let { "$it · 기본 음성으로 말해요" }
                packsLoaded = true
                refreshPacks()
            }
        }, "voicepack-load").apply { isDaemon = true }.start()
        Thread({
            val list = characterRepository.load()
            onMain {
                installedCharacters = list
                characterPackNotice = characters.restore(prefs.getString(PREF_CHARACTER_PACK, null))
                characterPacksLoaded = true
                refreshCharacterPacks()
            }
        }, "characterpack-load").apply { isDaemon = true }.start()
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    /** Repository가 SDK별 콜백을 공통 차량 입력 모델로 바꾼 뒤 이곳으로 보낸다. */
    private fun acceptVehicleInput(input: VehicleInput) = onMain {
        when (input) {
            is VehicleInput.Event -> engine.emit(input.event)
            is VehicleInput.Driving -> changeDriving(input.isDriving)
            is VehicleInput.Status -> when (input.source) {
                VehicleInput.Source.CAR_UX -> carUxStatus = input.message
                VehicleInput.Source.PLEOS_VEHICLE -> pleosStatus = input.message
            }
        }
    }

    /** ViewModel이 수집하는 화면 전용 스냅샷. Compose 가변 상태를 바깥으로 노출하지 않는다. */
    override fun snapshot(): CompanionUiState = CompanionUiState(
        driving = driving,
        voiceWhileDriving = voiceWhileDriving,
        navBusy = navBusy,
        preset = preset,
        pleosStatus = pleosStatus,
        carUxStatus = carUxStatus,
        ttsStatus = ttsStatus,
        typedLines = typedLines.toMap(),
        logs = logs.map { ReactionLogItem(it.time, it.text, it.note) },
        packList = packList,
        activePack = activePack,
        packsLoaded = packsLoaded,
        packStatus = packStatus,
        previewStatus = previewStatus,
        fixedLines = VehicleEvent.entries.associateWith(::fixedLine),
        characterPackList = characterPackList,
        activeCharacterPack = activeCharacterPack,
        characterPacksLoaded = characterPacksLoaded,
        characterPackNotice = characterPackNotice,
        vrEnabled = vrEnabled,
        vrOverlayStatus = vrOverlayStatus,
        vrOverlayPermissionRequired = vrOverlayPermissionRequired,
        canStartVr = canStartVr,
    )

    // ---- 입력 (Pleos·주행 규제·시뮬레이터 버튼이 같은 길로 들어온다) ----
    override fun emit(e: VehicleEvent) = onMain { engine.emit(e) }

    override fun burst() = onMain { listOf(VehicleEvent.BRAKE, VehicleEvent.TURN, VehicleEvent.WARNING).forEach(engine::emit) }

    override fun changeDriving(value: Boolean) = onMain {
        driving = value
        engine.setDriving(value)
        if (value && previewSeq == speakSeq) stop() // 미리듣기는 정차 중에만: 주행이 시작되면 그 소리를 끊는다(반응 소리는 엔진 규칙대로 둔다)
        syncCharacterOverlay()
    }

    override fun changeVoiceWhileDriving(value: Boolean) = onMain {
        voiceWhileDriving = value
        engine.setVoiceWhileDriving(value)
    }

    override fun changeNavBusy(value: Boolean) = onMain {
        navBusy = value
        if (value) engine.focusLost()
    }

    override fun choosePreset(value: VoicePreset) {
        preset = value
        prefs.edit().putString("preset", value.name).apply()
    }

    override fun setLine(e: VehicleEvent, text: String) {
        val t = text.take(MAX_LINE)
        typedLines[e] = t
        engine.setLine(e, packs.lineFor(e.name, t))
        prefs.edit().putString("line.${e.name}", t).apply()
    }

    /** 활성 팩이 정한 대사(자유 문장 팩이면 사용자 대사 그대로)를 엔진에 넣는다. */
    private fun refreshLines() {
        VehicleEvent.entries.forEach { e -> engine.setLine(e, packs.lineFor(e.name, typedLines[e] ?: e.defaultLine)) }
    }

    override fun clearLogs() = logs.clear()

    // ---- 음성 팩 ----
    /** 선택기의 상태를 화면 상태로 옮기고 대사를 다시 채운다. 설치된 팩을 읽은 뒤와 팩을 고른 뒤에 부른다. */
    private fun refreshPacks() {
        packList = listOf(audio.builtinPack) + installed
        activePack = packs.active()
        refreshLines()
    }

    // ---- 캐릭터 팩 ----
    private fun refreshCharacterPacks() {
        characterPackList = installedCharacters
        activeCharacterPack = characters.active()
        characterAnimation = activeCharacterPack?.animations?.idle ?: "idle"
        syncCharacterOverlay()
    }

    /** 정차 중에만 READY 캐릭터를 고르고 저장한다. 선택한 표현은 다음 오버레이 표시부터 적용된다. */
    override fun selectCharacterPack(id: String) {
        val pack = characterPackList.firstOrNull { it.id == id } ?: return
        if (driving || pack.state !is CharacterPackState.Ready) return
        characters.select(id)?.let { why ->
            Log.w(TAG, why)
            return
        }
        prefs.edit().putString(PREF_CHARACTER_PACK, id).apply()
        characterPackNotice = null
        refreshCharacterPacks()
    }

    /** 설정 Activity가 앞에 있을 때는 오버레이를 숨긴다. 뒤로 가면 저장된 VR 상태대로 다시 판단한다. */
    override fun setSettingsVisible(value: Boolean) = onMain {
        settingsVisible = value
        syncCharacterOverlay()
    }

    /** 사용자가 고른 VR 사용 여부를 저장한다. 실제 표시는 권한·정차·팩 준비 조건을 모두 확인한다. */
    override fun changeVrEnabled(value: Boolean) = onMain {
        vrEnabled = value
        prefs.edit().putBoolean(PREF_VR_ENABLED, value).apply()
        syncCharacterOverlay()
    }

    /** 설정을 닫아 배경 위의 캐릭터를 바로 보여 주기 위한 진입점. */
    override fun beginVr(): Boolean {
        if (!canStartVr) {
            syncCharacterOverlay()
            return false
        }
        settingsVisible = false
        syncCharacterOverlay()
        return true
    }

    override fun openVrOverlayPermissionSettings() {
        if (!characterOverlay.openPermissionSettings()) {
            vrOverlayStatus = "이 기기에서는 VR 표시 권한을 앱에서 켤 수 없어요 (권한 설정 화면이 없어요)"
        }
    }

    private fun syncCharacterOverlay() {
        val pack = activeCharacterPack
        val permissionGranted = characterOverlay.permissionGranted
        vrOverlayPermissionRequired = vrEnabled && !permissionGranted
        val input = VrOverlayInput(
            enabled = vrEnabled,
            stationary = !driving,
            settingsVisible = settingsVisible,
            readyCharacter = pack?.isRenderable == true,
            overlayPermissionGranted = permissionGranted,
        )
        if (VrOverlayPolicy.shouldShow(input)) {
            when (characterOverlay.show(pack!!, characterAnimation)) {
                OverlayShowResult.SHOWING -> vrOverlayStatus = "정차 배경 위에 ${pack.name}를 표시 중이에요 · 캐릭터를 누르면 설정을 열어요"
                OverlayShowResult.PERMISSION_REQUIRED -> vrOverlayStatus = "VR 표시 권한이 필요해요"
                OverlayShowResult.REJECTED -> vrOverlayStatus = "이 기기에서는 VR 오버레이 창을 열지 못했어요"
            }
            return
        }

        characterOverlay.hide()
        vrOverlayStatus = when {
            !vrEnabled -> "VR 모드가 꺼져 있어요"
            driving -> "주행 중에는 VR 캐릭터를 즉시 숨겨요"
            settingsVisible -> "설정 화면을 보고 있어요 · 뒤로 가거나 VR 시작을 누르면 배경에 표시해요"
            pack?.isRenderable != true -> "표시할 준비된 캐릭터 팩이 없어요"
            else -> "VR 표시 권한이 필요해요"
        }
    }

    /** 활성 팩이 이 반응의 대사를 정해 두었으면 그 대사(자막). 아니면 null — 사용자가 바꿀 수 있다. */
    fun fixedLine(e: VehicleEvent): String? =
        activePack.takeIf { !it.freeText && it.canSpeak(e.name) }?.lineFor(e.name, e.defaultLine)

    /** 정차 중에만. READY인 팩을 고르고 저장한다. 못 쓰는 팩(INVALID·읽는 중)은 고를 수 없다. */
    override fun selectPack(id: String) {
        val pack = packList.firstOrNull { it.id == id } ?: return
        if (driving || pack.state !is PackState.Ready) return
        packs.select(id)?.let { why ->
            Log.w(TAG, why)
            return
        }
        prefs.edit().putString(PREF_PACK, id).apply()
        packNotice = null // 사용자가 직접 골랐으니 저장해 둔 팩을 못 쓴다는 안내는 끝났다
        refreshPacks()
    }

    /**
     * 이 팩이 말하는 걸 한 번 들려 준다(정차 중에만). 반응 엔진을 거치지 않아서 쿨다운도 반응 기록도 없고,
     * 팩이 맡지 못해도 기본 음성이 대신 말하지 않는다(그 팩이 실제로 어떻게 들리는지 보여 줘야 한다). 결과는 [previewStatus]에 적는다.
     */
    override fun previewPack(pack: VoicePack) {
        if (driving || pack.state !is PackState.Ready) return
        val greeting = pack.greeting()
        val vehiclePreview = VehicleEvent.entries.firstOrNull { !it.neutral && pack.canSpeak(it.name) }
        if (greeting == null && vehiclePreview == null) return
        val eventId = greeting?.eventId ?: checkNotNull(vehiclePreview).name
        val text = greeting?.text ?: checkNotNull(vehiclePreview).let { event ->
            pack.lineFor(event.name, typedLines[event] ?: event.defaultLine)
        }
        val stamp = clock.format(Date()) // 반응 기록처럼 시각을 붙인다. 지난 결과(특히 실패)가 지금 상태처럼 읽히면 안 된다
        val head = "$stamp  ${if (greeting != null) "인사말" else "미리듣기"} 요청 · ${pack.name} \"$text\""
        if (!requestFocus()) {
            previewStatus = "$stamp  미리듣기를 하지 않았어요 · 오디오 포커스를 얻지 못했어요 (내비 등이 말하는 중)"
            return
        }
        val seq = ++speakSeq
        previewSeq = seq
        previewStatus = head // 여기서 아는 건 '요청함'까지. 재생됐는지는 음성이 따로 알려 준다
        packs.stopAll() // 말하던 소리는 먼저 끊는다
        val request = SpeakRequest(eventId, text, neutral = false, volume = MAX_VOLUME, pitch = preset.pitch, rate = preset.rate)
        val started = pack.voice.speak(
            request,
            note = { msg -> onMain { if (seq == previewSeq) previewStatus += " · $msg" } },
            done = { onMain { if (seq == speakSeq) audio.abandonFocus() } },
        )
        if (!started) {
            previewStatus = "$head · 재생하지 못했어요"
            audio.abandonFocus()
        }
    }

    // ---- Platform: 엔진이 바깥 세계와 만나는 곳 ----
    override fun speak(event: VehicleEvent, text: String, neutral: Boolean, volume: Float) {
        val row = lastSpeakRow[event] // 엔진이 speak 직전에 log했으니 방금 그 행이다
        val seq = ++speakSeq
        // ReactionEngine이 곧 showAvatar를 부를 정차·비중립 경로에서만 실제 캐릭터 연출을 바꾼다.
        if (!driving && !neutral) {
            characterAnimation = activeCharacterPack?.animations?.forEvent(event.name) ?: "idle"
            syncCharacterOverlay()
        }
        val request = SpeakRequest(
            eventId = event.name,
            text = text,
            neutral = neutral,
            volume = volume,
            pitch = if (neutral) 1f else preset.pitch,
            rate = if (neutral) 1f else preset.rate,
        )
        packs.speak(
            request,
            note = { msg -> onMain { row?.let { it.note += " · $msg" } } },
            // 포커스는 가장 최근 요청이 끝났을 때만 놓는다. 끊긴 이전 요청의 늦은 done이 새 요청의 포커스를 놓으면 안 된다.
            done = { onMain { if (seq == speakSeq) audio.abandonFocus() } },
            onFallback = { why -> row?.let { it.note += " · $why" } },
        )
    }

    override fun stop() {
        packs.stopAll()
        audio.abandonFocus()
    }

    override fun requestFocus(): Boolean = !navBusy && audio.requestFocus()

    override fun showAvatar(face: String, text: String) {
        this.face = face
        bubble = text
    }

    override fun hideAvatar() {
        // 다음 정차는 대기 동작부터 시작한다. 주행 중에는 sync가 WindowManager 창도 즉시 뗀다.
        characterAnimation = activeCharacterPack?.animations?.idle ?: "idle"
        syncCharacterOverlay()
    }

    override fun log(entry: LogEntry) {
        val what = when (entry) {
            is LogEntry.Speak -> "말하기 요청 \"${entry.text}\""
            is LogEntry.Drop -> "버림 · " + when (entry.why) {
                DropReason.COOLDOWN -> "쿨다운 ${(entry.waitMs + 999) / 1000}초 남음"
                DropReason.PRIORITY -> "더 급한 반응(${entry.by?.label})이 먼저였어요"
                DropReason.FOCUS -> "오디오 포커스를 잃은 상태예요 (내비 등이 말하는 중)"
                DropReason.DRIVING_SILENT -> "주행 중 음성이 불허된 상태예요"
            }
        }
        val row = LogRow(clock.format(Date()), "${entry.event.label} → $what")
        logs.add(0, row)
        if (logs.size > MAX_LOGS) logs.removeAt(logs.lastIndex)
        if (entry is LogEntry.Speak) lastSpeakRow[entry.event] = row
    }

    companion object {
        const val IDLE_BUBBLE = "차량 이벤트가 생기면 여기서 말해요."
        const val MAX_LINE = 60
        private const val MAX_LOGS = 50
        private const val MAX_VOLUME = 0.8f // 음량 상한(§6). 엔진과 미리듣기가 같은 값을 쓴다
        private const val PREF_PACK = "voicepack.selected"
        private const val PREF_CHARACTER_PACK = "characterpack.selected"
        private const val PREF_VR_ENABLED = "characterpack.vr.enabled"
        private const val TAG = "VoiceCompanion"
    }
}
