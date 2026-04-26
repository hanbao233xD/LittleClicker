package com.example.littleclicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.littleclicker.autoclick.AutoClickCoordinator
import com.example.littleclicker.ui.ActionManageScreen
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class ActionManageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AutoClickCoordinator.initialize(this)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MiuixTheme(colors = colors) {
                ActionManageScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}
