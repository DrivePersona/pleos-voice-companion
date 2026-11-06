package com.example.voicecompanion.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.voicecompanion.BuildConfig
import com.example.voicecompanion.app.model.CompanionUiState
import com.example.voicecompanion.characterpack.model.CharacterPack
import com.example.voicecompanion.characterpack.model.CharacterPackState
import com.example.voicecompanion.data.audio.VoicePreset
import com.example.voicecompanion.domain.model.VehicleEvent
import com.example.voicecompanion.domain.model.VehicleAvailability
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
import kotlinx.coroutines.flow.collect

@Composable
internal fun CompanionScreen(viewModel: CompanionViewModel, onStartVrEffect: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is CompanionUiEffect.CloseSetupForVr) onStartVrEffect()
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
                        ScreenCard(state, onTouch)
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
                ScreenCard(state, onTouch)
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
private fun ScreenCard(state: CompanionUiState, onTouch: (CompanionTouch) -> Unit) {
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
            // 캐릭터 선택은 상태 카드에서 바로 이어져야 한다. 개발용 Vehicle 디버그 로그보다 위에 둔다.
            CharacterPackPicker(state, onTouch)
        }
        StatusMetric("차량 서비스", state.pleosStatus)
        StatusMetric("주행 규제", state.carUxStatus)
        StatusMetric("음성 합성", state.ttsStatus)
        state.packStatus?.let { StatusMetric("연결된 목소리", it) }
        state.characterPackNotice?.let { StatusMetric("캐릭터 팩", it) }
        if (BuildConfig.DEBUG) VehicleDataDebugCard(state)
    }
}

@Composable
private fun VehicleDataDebugCard(state: CompanionUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Vehicle 데이터 디버그", style = MaterialTheme.typography.titleSmall)
        state.vehicleData.forEach { item ->
            val timestamp = item.updatedAtEpochMs?.let {
                SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(it))
            }
            val availability = when (item.availability) {
                VehicleAvailability.CHECKING -> "확인 중"
                VehicleAvailability.ACTIVE -> "수신 중"
                VehicleAvailability.UNSUPPORTED -> "미지원"
                VehicleAvailability.ERROR -> "오류"
            }
            Text(
                "${item.definition.displayName} · $availability${item.value?.let { " · $it" } ?: ""}" +
                    (timestamp?.let { " · $it" } ?: "") +
                    "\n${item.definition.capability} · ${item.definition.permission}" +
                    (item.detail?.let { "\n$it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = if (item.availability == VehicleAvailability.ERROR) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        if (state.dialogueRows.isNotEmpty()) {
            state.activeCharacterPack?.let { character ->
                val preview = remember(character.id) {
                    character.previewPng?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                }
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        contentDescription = "${character.name} 캐릭터 이미지",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                    )
                }
            }
            state.dialogueRows.forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.label, style = MaterialTheme.typography.labelMedium)
                    row.variants.forEach { variant ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${variant.index + 1}. ${variant.text}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            if (row.audioReady) {
                                TextButton(
                                    onClick = { onTouch(CompanionTouch.DialoguePreviewRequested(row.eventId, variant.index)) },
                                    enabled = !state.driving,
                                ) { Text("듣기") }
                            } else {
                                Text("음성 준비 중", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            HelperText("선택한 캐릭터의 13개 대사예요. 준비된 WAV만 듣기를 제공하고, 나머지는 문구 승인 후 음성을 생성해요.")
        } else {
            state.availableEvents.forEach { event ->
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
        }

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
private fun CharacterPackPicker(state: CompanionUiState, onTouch: (CompanionTouch) -> Unit) {
    CompanionSectionHeader("캐릭터 팩", "정차 중 일반 배경 위에 표시할 캐릭터를 고르세요.")
    state.characterPackList.forEach { CharacterPackRow(state, it, onTouch) }
    if (!state.characterPacksLoaded) {
        HelperText("캐릭터 팩을 읽는 중이에요…")
    } else if (state.characterPackList.isEmpty()) {
        HelperText("출시 로스터의 음성·권리·표현 검증을 통과한 캐릭터가 없어 VR 표시를 사용할 수 없어요.")
    } else if (BuildConfig.DEBUG && state.characterPackList.any { it.info?.voicePackId == null }) {
        HelperText("내부 후보 미리보기예요. 음성 팩과 상업 이용 승인을 마치기 전까지 출시 목록에는 들어가지 않아요.")
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
        pack.model != null && pack.info?.voicePackId == null -> "내부 미리보기 · GLB 3D 모델"
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
        state.availableEvents.forEach { event ->
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
