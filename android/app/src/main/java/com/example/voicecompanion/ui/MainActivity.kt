package com.example.voicecompanion.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.voicecompanion.app.CompanionApp
import com.example.voicecompanion.ui.theme.CompanionTheme
import com.example.voicecompanion.ui.viewmodel.CompanionViewModel

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: CompanionViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val runtime = (application as CompanionApp).runtime
        viewModel = ViewModelProvider(this, CompanionViewModel.Factory(runtime))[CompanionViewModel::class.java]
        setContent {
            CompanionTheme {
                Surface(Modifier.fillMaxSize()) {
                    CompanionScreen(viewModel, onStartVrEffect = {
                        // ponytail: finish()로 닫으면 Pleos 셸이 기어를 P로 돌릴 때 이 앱의 태스크를 제거하며 프로세스를 죽인다(실측: am_kill remove task).
                        // 그래서 태스크를 남기는 moveTaskToBack을 쓴다. 한계: Pleos에선 설정 화면이 오버레이 아래 그대로 떠 있고,
                        // EVS 같은 화면이 지나가면 설정이 다시 resume되어 오버레이가 사라진다. 서비스로 오버레이를 호스팅하는 결정 뒤에 다시 푼다.
                        moveTaskToBack(true)
                    })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setSettingsVisible(true)
    }

    override fun onPause() {
        viewModel.setSettingsVisible(false)
        super.onPause()
    }
}
