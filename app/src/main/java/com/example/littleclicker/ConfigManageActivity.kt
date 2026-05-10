package com.example.littleclicker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.littleclicker.autoclick.AutoClickCoordinator
import com.example.littleclicker.ui.ConfigManageScreen
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class ConfigManageActivity : ComponentActivity() {
    private var importUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AutoClickCoordinator.initialize(this)
        importUri = extractImportUri(intent)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MiuixTheme(colors = colors) {
                ConfigManageScreen(
                    onBack = { finish() },
                    importUri = importUri
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importUri = extractImportUri(intent)
    }

    private fun extractImportUri(intent: Intent?): Uri? {
        if (intent == null) return null
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_EDIT) {
            return intent.data
        }
        return null
    }
}
