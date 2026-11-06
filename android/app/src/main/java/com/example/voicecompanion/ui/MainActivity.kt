package com.example.voicecompanion.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.voicecompanion.app.CompanionApp
import com.example.voicecompanion.app.CompanionRuntimeService
import com.example.voicecompanion.ui.theme.CompanionTheme
import com.example.voicecompanion.ui.viewmodel.CompanionViewModel

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: CompanionViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, CompanionRuntimeService::class.java).setAction(CompanionRuntimeService.ACTION_SETTINGS))
        val runtime = (application as CompanionApp).runtime
        viewModel = ViewModelProvider(this, CompanionViewModel.Factory(runtime))[CompanionViewModel::class.java]
        setContent {
            CompanionTheme {
                Surface(Modifier.fillMaxSize()) {
                    CompanionScreen(viewModel, onStartVrEffect = {
                        finish()
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
