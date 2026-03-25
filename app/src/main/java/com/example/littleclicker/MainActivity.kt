package com.example.littleclicker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.littleclicker.autoclick.AutoClickCoordinator
import com.example.littleclicker.update.AppNoticeChecker
import com.example.littleclicker.update.AppNoticeInfo
import com.example.littleclicker.update.AppUpdateChecker
import com.example.littleclicker.update.AppUpdateInfo
import com.example.littleclicker.ui.AboutScreen
import com.example.littleclicker.ui.AutoClickScreen
import androidx.core.content.ContextCompat
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class MainActivity : ComponentActivity() {
    private var hasRequestedStoragePermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MiuixTheme(colors = colors) {
                AppRoot()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || hasRequestedStoragePermission) return

        hasRequestedStoragePermission = true
        window.decorView.postDelayed(
            { requestStoragePermissionsIfNeeded() },
            500L
        )
    }

    private fun requestStoragePermissionsIfNeeded() {
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES
            )
            else -> buildList {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
        }

        val missingPermissions = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                REQUEST_STORAGE_PERMISSIONS
            )
        }
    }

    companion object {
        private const val REQUEST_STORAGE_PERMISSIONS = 10001
    }
}

private enum class MainTab(val route: String, val title: String) {
    AUTO_CLICK("auto_click", "自动点击"),
    ABOUT("about", "关于"),
}

@Composable
private fun AppRoot() {
    val navController = rememberNavController()
    val context = LocalContext.current
    var noticeInfo by remember { mutableStateOf<AppNoticeInfo?>(null) }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    val tabs = listOf(MainTab.AUTO_CLICK, MainTab.ABOUT)
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry.value?.destination

    LaunchedEffect(Unit) {
        AutoClickCoordinator.initialize(context)
        coroutineScope {
            val updateTask = async { AppUpdateChecker.checkUpdate(localVersionCode = BuildConfig.VERSION_CODE) }
            val noticeTask = async { AppNoticeChecker.fetchNotice() }
            updateInfo = updateTask.await()
            noticeInfo = noticeTask.await()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    val icon = when (tab) {
                        MainTab.AUTO_CLICK -> Icons.Default.Home
                        MainTab.ABOUT -> Icons.Default.Info
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = icon,
                        label = tab.title,
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainTab.AUTO_CLICK.route,
            modifier = Modifier
        ) {
            composable(MainTab.AUTO_CLICK.route) {
                AutoClickScreen(
                    innerPadding = innerPadding,
                    updateInfo = updateInfo,
                    noticeInfo = noticeInfo,
                )
            }
            composable(MainTab.ABOUT.route) {
                AboutScreen(innerPadding)
            }
        }
    }
}
