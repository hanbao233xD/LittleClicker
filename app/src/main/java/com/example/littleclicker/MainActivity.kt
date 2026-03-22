package com.example.littleclicker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.littleclicker.service.AutoClickAccessibilityService
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import androidx.compose.ui.res.stringResource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MiuixTheme(colors = colors) {
                AppRoot()
            }
        }
    }
}

private enum class MainTab(val route: String, val title: String) {
    HOME("home", "首页"),
    SCRIPTS("scripts", "脚本管理"),
    ABOUT("about", "关于"),
}

@Composable
private fun AppRoot() {
    val navController = rememberNavController()
    val tabs = listOf(MainTab.HOME, MainTab.SCRIPTS, MainTab.ABOUT)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    val icon = when (tab) {
                        MainTab.HOME -> Icons.Default.Home
                        MainTab.SCRIPTS -> Icons.AutoMirrored.Filled.List
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
            startDestination = MainTab.HOME.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(MainTab.HOME.route) { HomeScreen() }
            composable(MainTab.SCRIPTS.route) { ScriptManageScreen() }
            composable(MainTab.ABOUT.route) { AboutScreen() }
        }
    }
}

private data class PermissionStatus(
    val title: String,
    val description: String,
    val granted: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshToken by remember { mutableStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshToken++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val statuses = remember(refreshToken) {
        listOf(
            PermissionStatus(
                title = "悬浮窗权限",
                description = "用于显示点击控制面板。",
                granted = Settings.canDrawOverlays(context),
                onClick = { openOverlaySettings(context) }
            ),
            PermissionStatus(
                title = "无障碍服务",
                description = "用于执行自动点击与手势。",
                granted = isAccessibilityServiceEnabled(context),
                onClick = { openAccessibilitySettings(context) }
            ),
            PermissionStatus(
                title = "忽略电池优化",
                description = "避免系统杀后台导致脚本中断。",
                granted = isIgnoringBatteryOptimizations(context),
                onClick = { openBatteryOptimizationSettings(context) }
            )
        )
    }
    val allGranted = statuses.all { it.granted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFF5F6FA), Color(0xFFEFF3FF))
                )
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "权限保姆级引导",
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "依次点击下方卡片并完成授权，全部完成后即可开启悬浮窗。",
            color = MiuixTheme.colorScheme.onBackgroundVariant
        )
        statuses.forEach { item ->
            PermissionCard(status = item)
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                Toast.makeText(context, "悬浮窗已准备就绪，可接入你的控制面板服务", Toast.LENGTH_SHORT).show()
            },
            enabled = allGranted,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("开启悬浮窗")
        }
    }
}

@Composable
private fun PermissionCard(status: PermissionStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        colors = CardDefaults.defaultColors(
            color = if (status.granted) Color(0xFFE7F6EC) else Color.White,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainer
        ),
        onClick = status.onClick,
        insideMargin = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(status.title, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(status.description, color = MiuixTheme.colorScheme.onBackgroundVariant)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (status.granted) "已授权" else "去授权",
                color = if (status.granted) Color(0xFF1F8B4C) else MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ScriptManageScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "脚本管理页面（可接入本地脚本列表、导入与开关）",
            color = MiuixTheme.colorScheme.onBackgroundVariant
        )
    }
}

@Composable
private fun AboutScreen() {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val version = BuildConfig.VERSION_NAME
    val items = listOf("检查更新", "隐私政策", "免责声明")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = appName,
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(20.dp))
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(text = appName, style = MiuixTheme.textStyles.title1, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "Version $version", color = MiuixTheme.colorScheme.onBackgroundVariant)
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onSurfaceContainer
            )
        ) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(items) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Toast.makeText(context, "$item 功能待接入", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item, fontWeight = FontWeight.Medium)
                        Text(">", color = MiuixTheme.colorScheme.onBackgroundVariant)
                    }
                }
            }
        }
    }
}

private fun openOverlaySettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun openBatteryOptimizationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
    } else {
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = ComponentName(context, AutoClickAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getInt(
        context.contentResolver,
        Settings.Secure.ACCESSIBILITY_ENABLED,
        0
    ) == 1
    if (!enabled) return false
    val settingValue = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return settingValue.split(':').any { it.equals(expectedComponentName, ignoreCase = true) }
}
