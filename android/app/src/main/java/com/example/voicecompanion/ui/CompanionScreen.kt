package com.example.voicecompanion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.voicecompanion.BuildConfig
import com.example.voicecompanion.app.model.CompanionUiState
import com.example.voicecompanion.characterpack.model.CharacterPack
import com.example.voicecompanion.characterpack.model.CharacterPackState
import com.example.voicecompanion.data.audio.VoicePreset
import com.example.voicecompanion.domain.model.VehicleEvent
import com.example.voicecompanion.ui.component.CompanionBadge
import com.example.voicecompanion.ui.component.CompanionBadgeTone
import com.example.voicecompanion.ui.component.CompanionCard
import com.example.voicecompanion.ui.component.CompanionListRow
import com.example.voicecompanion.ui.component.CompanionPrimaryButton
import com.example.voicecompanion.ui.component.CompanionSecondaryButton
import com.example.voicecompanion.ui.component.CompanionSectionHeader
import com.example.voicecompanion.ui.event.CompanionTouch
import com.example.voicecompanion.ui.event.CompanionUiEffect
import com.example.voicecompanion.ui.theme.CompanionTokens
import com.example.voicecompanion.ui.viewmodel.CompanionViewModel
import com.example.voicecompanion.voicepack.domain.PackKind
import com.example.voicecompanion.voicepack.domain.PackState
import com.example.voicecompanion.voicepack.domain.VoicePack
import kotlinx.coroutines.flow.collect

@Composable
internal fun CompanionScreen(viewModel: CompanionViewModel, onStartVrEffect: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is CompanionUiEffect.MoveTaskToBackForVr) onStartVrEffect()
        }
    }
    CompanionContent(state, viewModel::onTouch)
}

@Composable
private fun CompanionContent(state: CompanionUiState, onTouch: (CompanionTouch) -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (state.driving) {
            DrivingContent(state, onTouch)
        } else {
            val wide = maxWidth >= 720.dp
            if (wide) {
            Column(Modifier.fillMaxSize().padding(CompanionTokens.PagePadding)) {
                CompanionTopHeader(state)
                Row(
                    Modifier.fillMaxWidth().weight(1f).padding(top = CompanionTokens.SectionGap),
                    horizontalArrangement = Arrangement.spacedBy(CompanionTokens.SectionGap),
                ) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(CompanionTokens.SectionGap),
                    ) {
                        ScreenCard(state)
                        CustomCard(state, onTouch)
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(CompanionTokens.SectionGap),
                    ) {
                        if (BuildConfig.DEBUG) SimulatorCard(state, onTouch)
                        LogCard(state, onTouch)
                    }
                }
            }
            } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(CompanionTokens.CompactPagePadding),
                verticalArrangement = Arrangement.spacedBy(CompanionTokens.SectionGap),
            ) {
                CompanionTopHeader(state)
                ScreenCard(state)
                // 폰에서는 실제 반응 확인과 기록을 상세 편집보다 먼저 배치한다.
                if (BuildConfig.DEBUG) SimulatorCard(state, onTouch)
                LogCard(state, onTouch)
                CustomCard(state, onTouch)
            }
            }
        }
    }
}

@Composable
private fun CompanionTopHeader(state: CompanionUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("보이스 컴패니언", style = MaterialTheme.typography.headlineSmall)
            CompanionBadge(
                label = if (state.driving) "주행 중" else "정차 중",
                tone = if (state.driving) CompanionBadgeTone.DRIVING else CompanionBadgeTone.PARKED,
            )
        }
        Text(
            if (state.driving) "차량 상태를 짧고 안전하게 음성으로 알려드려요."
            else "내 차의 목소리와 캐릭터를 편안하게 설정해 보세요.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 주행 중에는 설정·캐릭터·CTA를 노출하지 않는다.
 * 차량 제품 정책상 이 화면이 플랫폼에 가려질 수도 있지만, 허용된 화면에서는 같은 안전 원칙을 유지한다.
 */
@Composable
private fun DrivingContent(state: CompanionUiState, onTouch: (CompanionTouch) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(CompanionTokens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(CompanionTokens.SectionGap),
    ) {
        CompanionTopHeader(state)
        CompanionCard {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("│▂▅▂│", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text("목소리로만 알려드릴게요", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(
                    if (state.voiceWhileDriving) "설정과 캐릭터는 정차하면 다시 사용할 수 있어요."
                    else "현재 차량 정책에 따라 음성 반응도 잠시 멈췄어요.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        CompanionCard {
            CompanionSectionHeader("현재 상태", "주행 중에는 필요한 상태만 간단히 보여드려요.")
            StatusMetric("음성 반응", if (state.voiceWhileDriving) "켜짐" else "꺼짐")
            StatusMetric("차량 서비스", state.pleosStatus)
            StatusMetric("주행 규제", state.carUxStatus)
        }
        if (BuildConfig.DEBUG) {
            CompanionSectionHeader("개발 도구", "디버그 빌드에서만 표시돼요.")
            SimulatorCard(state, onTouch)
            LogCard(state, onTouch)
        }
    }
}

/** 차량 설정 화면. 3D 캐릭터는 앱 카드가 아니라 WindowManager 오버레이에서만 보인다. */
@Composable
private fun ScreenCard(state: CompanionUiState) {
    val title = when {
        !state.driving -> "정차 중이에요"
        state.voiceWhileDriving -> "목소리로만 알려드릴게요"
        else -> "정차할 때까지 조용히 있을게요"
    }
    val description = when {
        !state.driving -> "캐릭터와 목소리를 바꾸고, 미리 들어볼 수 있어요."
        state.voiceWhileDriving -> "주행 중에는 캐릭터와 설정을 잠시 숨겨요."
        else -> "현재 차량 정책에 따라 주행 중 음성 반응을 멈췄어요."
    }
    CompanionCard(emphasized = !state.driving) {
        CompanionSectionHeader(title, description)
        if (!state.driving) {
            val character = state.activeCharacterPack
            StatusMetric("VR 캐릭터", character?.name ?: "선택한 캐릭터가 없어요")
            StatusMetric("VR 표시", state.vrOverlayStatus)
        }
        StatusMetric("차량 서비스", state.pleosStatus)
        StatusMetric("주행 규제", state.carUxStatus)
        StatusMetric("음성 합성", state.ttsStatus)
        state.packStatus?.let { StatusMetric("음성 팩", it) }
        state.characterPackNotice?.let { StatusMetric("캐릭터 팩", it) }
    }
}

@Composable
private fun StatusMetric(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.widthIn(min = 72.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CustomCard(state: CompanionUiState, onTouch: (CompanionTouch) -> Unit) {
    CompanionCard {
        CompanionSectionHeader(
            title = "내 컴패니언",
            description = if (state.driving) "정차하면 다시 설정할 수 있어요." else "대사와 목소리, 캐릭터를 내 취향에 맞게 바꿔 보세요.",
        )

        Text("대사", style = MaterialTheme.typography.titleSmall)
        VehicleEvent.entries.forEach { event ->
            val fixed = state.fixedLine(event)
            OutlinedTextField(
                value = fixed ?: state.typedLines[event] ?: event.defaultLine,
                onValueChange = { onTouch(CompanionTouch.LineChanged(event, it)) },
                label = { Text(event.label + if (event.neutral) " · 중립 음성" else "") },
                supportingText = if (fixed != null) ({ Text("녹음된 대사라 수정할 수 없어요") }) else null,
                singleLine = true,
                enabled = !state.driving && fixed == null,
                shape = RoundedCornerShape(CompanionTokens.RowRadius),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            "대사를 비우면 기본 대사가 적용돼요. 안전 경고는 항상 중립 음성으로 읽어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        CharacterPackPicker(state, onTouch)
        PackPicker(state, onTouch)

        Text("기본 목소리", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoicePreset.entries.forEach { preset ->
                FilterChip(
                    selected = state.preset == preset,
                    onClick = { onTouch(CompanionTouch.PresetSelected(preset)) },
                    enabled = !state.driving,
                    label = { Text(preset.label) },
                    shape = RoundedCornerShape(100.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PackPicker(state: CompanionUiState, onTouch: (CompanionTouch) -> Unit) {
    CompanionSectionHeader("음성 팩", "목소리를 선택하고, 설치된 팩의 정보를 확인할 수 있어요.")
    state.packList.forEach { PackRow(state, it, onTouch) }
    if (!state.packsLoaded) {
        HelperText("음성 팩을 읽는 중이에요…")
    } else if (state.packList.size == 1) {
        HelperText("설치된 음성 팩이 없어 기본 음성을 사용하고 있어요.")
    }
    if (state.previewStatus.isNotEmpty()) HelperText(state.previewStatus)
}

@Composable
private fun CharacterPackPicker(state: CompanionUiState, onTouch: (CompanionTouch) -> Unit) {
    CompanionSectionHeader("캐릭터 팩", "정차 중 일반 배경 위에 표시할 캐릭터를 고르세요.")
    state.characterPackList.forEach { CharacterPackRow(state, it, onTouch) }
    if (!state.characterPacksLoaded) {
        HelperText("캐릭터 팩을 읽는 중이에요…")
    } else if (state.characterPackList.isEmpty()) {
        HelperText("설치된 캐릭터 팩이 없어 VR 캐릭터를 표시할 수 없어요.")
    }
    Text("VR 표시", style = MaterialTheme.typography.titleSmall)
    SwitchRow(
        label = "VR 캐릭터 켜기",
        hint = "정차 중에만 일반 배경 위에 떠요. 캐릭터를 누르면 이 설정 화면으로 돌아와요.",
        checked = state.vrEnabled,
        onChange = { onTouch(CompanionTouch.VrEnabledChanged(it)) },
    )
    HelperText(state.vrOverlayStatus)
    if (state.vrOverlayPermissionRequired) {
        CompanionSecondaryButton("VR 표시 권한 설정", { onTouch(CompanionTouch.VrPermissionRequested) })
    }
    CompanionPrimaryButton("VR 시작", { onTouch(CompanionTouch.VrStarted) }, enabled = state.canStartVr)
    HelperText("주행이 시작되면 캐릭터를 즉시 숨기고, 음성 반응 규칙은 그대로 유지해요.")
}

@Composable
private fun CharacterPackRow(state: CompanionUiState, pack: CharacterPack, onTouch: (CompanionTouch) -> Unit) {
    val ready = pack.state is CharacterPackState.Ready
    val selected = state.activeCharacterPack?.id == pack.id
    var showInfo by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CompanionListRow(
            title = pack.name,
            description = characterPackStateText(pack),
            selected = selected,
            enabled = ready && !state.driving,
            onClick = { onTouch(CompanionTouch.CharacterPackSelected(pack.id)) },
        )
        if (ready) {
            TextButton({ showInfo = !showInfo }, Modifier.padding(start = 8.dp)) { Text(if (showInfo) "정보 접기" else "정보") }
            if (showInfo) HelperText(characterPackInfoText(pack))
        }
    }
}

private fun characterPackStateText(pack: CharacterPack): String = when (val state = pack.state) {
    is CharacterPackState.Ready -> when {
        pack.model != null -> "준비됨 · GLB 3D 모델"
        pack.sprite != null -> "준비됨 · 2D 애니메이션 스프라이트"
        else -> "사용할 수 없어요 · 표현 파일이 없어요"
    }
    is CharacterPackState.Invalid -> "사용할 수 없어요 · ${state.reason}"
}

private fun characterPackInfoText(pack: CharacterPack): String {
    val info = pack.info ?: return "정보를 읽을 수 없어요"
    return buildString {
        append("버전 ${info.version} · 라이선스 ${info.provenance.license} · 상업 이용 ${if (info.provenance.commercialUse) "가능" else "불가"}")
        info.provenance.attribution?.let { append("\n출처 표기: $it") }
        append("\n")
        append(if (info.provenance.character == "original") "독자 캐릭터 선언" else "참조 기반 캐릭터 · 디버그 전용")
        append(" · ")
        append(if (pack.model != null) "GLB 애니메이션 검증 완료" else "스프라이트 프레임 검증 완료")
    }
}

@Composable
private fun PackRow(state: CompanionUiState, pack: VoicePack, onTouch: (CompanionTouch) -> Unit) {
    val ready = pack.state is PackState.Ready
    val selected = state.activePack.id == pack.id
    var showInfo by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CompanionListRow(
            title = pack.name,
            description = packStateText(pack),
            selected = selected,
            enabled = ready && !state.driving,
            onClick = { onTouch(CompanionTouch.PackSelected(pack.id)) },
        )
        if (ready) {
            Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onTouch(CompanionTouch.PackPreviewRequested(pack)) },
                    enabled = !state.driving,
                ) { Text(if (pack.greeting() != null) "인사 듣기" else "미리듣기") }
                TextButton({ showInfo = !showInfo }) { Text(if (showInfo) "정보 접기" else "정보") }
            }
            if (showInfo) HelperText(packInfoText(pack))
        }
    }
}

private fun packStateText(pack: VoicePack): String = when (val state = pack.state) {
    is PackState.Ready -> when (pack.kind) {
        PackKind.CLIP -> "준비됨 · 녹음 클립 · 대사는 녹음된 문장이에요"
        else -> "준비됨 · 대사를 바꿀 수 있어요"
    }
    is PackState.Loading -> "읽는 중이에요…"
    is PackState.Invalid -> "사용할 수 없어요 · ${state.reason}"
}

private fun packInfoText(pack: VoicePack): String {
    val info = pack.info ?: return "폰이나 차량의 음성 합성(TTS)이 읽어요. 모든 반응에 쓰이고 대사를 바꿀 수 있어요."
    val voice = when (info.provenance.voiceSource) {
        "self-recorded" -> "직접 녹음"
        "actor-contract" -> "성우 계약"
        "open-license" -> "공개 라이선스 음원"
        "synthetic" -> "합성"
        else -> info.provenance.voiceSource
    }
    val reactions = VehicleEvent.entries.filter { !it.neutral }
    return buildString {
        append("버전 ${info.version} · 라이선스 ${info.provenance.license} · 상업 이용 ${if (info.provenance.commercialUse) "가능" else "불가"}")
        append("\n목소리 출처: $voice")
        info.provenance.attribution?.let { append("\n출처 표기: $it") }
        if (!pack.freeText) {
            pack.greeting()?.let { append("\n인사말: ${it.text}") }
            append("\n이 팩이 말하는 반응: " + reactions.filter { pack.canSpeak(it.name) }.joinToString { it.label })
            reactions.filterNot { pack.canSpeak(it.name) }.takeIf { it.isNotEmpty() }?.let {
                append("\n기본 음성이 읽는 반응: " + it.joinToString { event -> event.label })
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(CompanionTokens.RowRadius),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = CompanionTokens.MinimumTouchHeight)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Switch(checked = checked, onCheckedChange = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 실제 차에는 없는 개발용 패널. Pleos 신호 대신 이벤트·상태를 손으로 만든다. */
@Composable
private fun SimulatorCard(state: CompanionUiState, onTouch: (CompanionTouch) -> Unit) {
    CompanionCard {
        CompanionSectionHeader("시뮬레이터", "개발용 도구예요. 실제 차량에는 표시되지 않아요.")
        SwitchRow("주행 중", "끄면 정차 상태예요.", state.driving) { onTouch(CompanionTouch.DrivingChanged(it)) }
        SwitchRow("주행 중 음성 허용", "끄면 주행 중 음성 반응을 멈춰요.", state.voiceWhileDriving) { onTouch(CompanionTouch.VoiceWhileDrivingChanged(it)) }
        SwitchRow("내비 안내 중", "오디오 포커스를 잃어 반응이 멈춰요.", state.navBusy) { onTouch(CompanionTouch.NavigationBusyChanged(it)) }
        Text("차량 이벤트", style = MaterialTheme.typography.titleSmall)
        VehicleEvent.entries.forEach { event ->
            CompanionSecondaryButton(event.label, { onTouch(CompanionTouch.VehicleEventRequested(event)) })
        }
        CompanionPrimaryButton("동시에 3종 반응 확인", { onTouch(CompanionTouch.EventBurstRequested) })
        HelperText("같은 순간 여러 이벤트가 오면 안전도가 가장 높은 하나만 말해요.")
    }
}

@Composable
private fun LogCard(state: CompanionUiState, onTouch: (CompanionTouch) -> Unit) {
    CompanionCard {
        CompanionSectionHeader("반응 기록", "요청과 실제 재생 시작을 구분해 보여드려요.")
        if (state.logs.isEmpty()) {
            HelperText("아직 반응이 없어요. 시뮬레이터에서 차량 이벤트를 눌러 보세요.")
        } else {
            state.logs.forEach { item ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(CompanionTokens.RowRadius),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.text + item.note, style = MaterialTheme.typography.bodyMedium)
                        Text(item.time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton({ onTouch(CompanionTouch.LogsCleared) }) { Text("기록 지우기") }
        }
    }
}

@Composable
private fun HelperText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
    )
}
