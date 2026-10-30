package com.example.voicecompanion.ui.viewmodel

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.voicecompanion.app.CompanionRuntime
import com.example.voicecompanion.app.CompanionUiRuntime
import com.example.voicecompanion.app.model.CompanionUiState
import com.example.voicecompanion.ui.event.CompanionTouch
import com.example.voicecompanion.ui.event.CompanionUiEffect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** UI 상태와 터치 입력을 Runtime에 연결한다. 차량 연결은 Activity보다 오래 사는 Runtime이 계속 소유한다. */
class CompanionViewModel(
    private val runtime: CompanionUiRuntime,
) : ViewModel() {
    private val _uiState = MutableStateFlow(runtime.snapshot())
    val uiState: StateFlow<CompanionUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CompanionUiEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    init {
        runtime.start()
        viewModelScope.launch {
            snapshotFlow { runtime.snapshot() }.collect { _uiState.value = it }
        }
    }

    fun onTouch(touch: CompanionTouch) {
        routeTouch(runtime, touch)?.let { _effects.tryEmit(it) }
    }

    fun setSettingsVisible(visible: Boolean) = runtime.setSettingsVisible(visible)

    class Factory(private val runtime: CompanionRuntime) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CompanionViewModel::class.java)) { "지원하지 않는 ViewModel: ${modelClass.name}" }
            return CompanionViewModel(runtime) as T
        }
    }
}
