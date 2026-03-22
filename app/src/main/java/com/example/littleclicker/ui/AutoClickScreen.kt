package com.example.littleclicker.ui

import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.littleclicker.autoclick.AutoClickCoordinator
import com.example.littleclicker.autoclick.AutoClickRunState
import com.example.littleclicker.service.FloatingWindowService
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class PermissionStatus(
    val title: String,
    val description: String,
    val granted: Boolean,
    val onClick: () -> Unit,
)

@Composable
internal fun AutoClickScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshToken by remember { mutableStateOf(0) }

    val profile by AutoClickCoordinator.profile.collectAsState()
    val runtime by AutoClickCoordinator.runtime.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshToken++
                AutoClickCoordinator.initialize(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val statuses = remember(refreshToken) {
        listOf(
            PermissionStatus(
                title = "悬浮窗权限",
                description = "用于显示可拖动点击点面板。",
                granted = Settings.canDrawOverlays(context),
                onClick = { openOverlaySettings(context) }
            ),
            PermissionStatus(
                title = "无障碍服务",
                description = "用于执行自动点击手势。",
                granted = isAccessibilityServiceEnabled(context),
                onClick = { openAccessibilitySettings(context) }
            ),
            PermissionStatus(
                title = "忽略电池优化",
                description = "降低后台被杀导致任务中断的概率。",
                granted = isIgnoringBatteryOptimizations(context),
                onClick = { openBatteryOptimizationSettings(context) }
            )
        )
    }
    val allGranted = statuses.all { it.granted }
    val expiredSchedule = profile.startAtMillis?.let { it <= System.currentTimeMillis() } == true

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFF5F6FA), Color(0xFFEFF3FF))
                )
            )
            .padding(innerPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "自动点击",
                style = MiuixTheme.textStyles.title1,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "默认进入自动点击模式。脚本功能已拆分到脚本管理页。",
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                statuses.forEach { status ->
                    PermissionCard(status = status)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                colors = CardDefaults.defaultColors(
                    color = Color.White,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("配置名称")
                    OutlinedTextField(
                        value = profile.name,
                        onValueChange = { AutoClickCoordinator.updateProfileName(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { M3Text("自动点击配置名") }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("全局循环次数：${profile.cycleCount}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallActionButton(text = "-") {
                                AutoClickCoordinator.updateCycleCount(profile.cycleCount - 1)
                            }
                            SmallActionButton(text = "+") {
                                AutoClickCoordinator.updateCycleCount(profile.cycleCount + 1)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                colors = CardDefaults.defaultColors(
                    color = Color.White,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("定时开始")
                    Text(
                        text = profile.startAtMillis?.let { formatDateTime(it) } ?: "未设置",
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                showDateTimePicker(
                                    context = context,
                                    initialMillis = profile.startAtMillis,
                                    onSelected = { millis ->
                                        val success = AutoClickCoordinator.scheduleAt(millis)
                                        val tip = if (success) "定时已设置" else "定时失败，请重新选择时间"
                                        Toast.makeText(context, tip, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        ) {
                            Text("选择时间")
                        }
                        Button(onClick = { AutoClickCoordinator.clearScheduleTime() }) {
                            Text("清除定时")
                        }
                    }
                    if (expiredSchedule) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("定时已过期", color = Color(0xFFB00020))
                            Button(onClick = {
                                val started = AutoClickCoordinator.startNow()
                                if (!started) {
                                    Toast.makeText(context, "立即开始失败，请检查无障碍服务", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Text("立即开始")
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                colors = CardDefaults.defaultColors(
                    color = Color.White,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("运行控制")
                    Text(
                        text = "状态：${runtime.state} ${runtime.message ?: ""}",
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                    Button(
                        onClick = {
                            if (!Settings.canDrawOverlays(context)) {
                                openOverlaySettings(context)
                                Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            FloatingWindowService.startAutoClickOverlay(context)
                            Toast.makeText(context, "自动点击悬浮窗已启动", Toast.LENGTH_SHORT).show()
                        },
                        enabled = allGranted,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("开启自动点击悬浮窗")
                    }
                    Button(
                        onClick = {
                            val started = AutoClickCoordinator.startNow()
                            if (!started) {
                                Toast.makeText(context, "开始失败，请检查无障碍服务", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = allGranted,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("立即开始")
                    }
                    Button(
                        onClick = {
                            val success = if (runtime.state == AutoClickRunState.Paused) {
                                AutoClickCoordinator.resume()
                            } else {
                                AutoClickCoordinator.pause()
                            }
                            if (!success) {
                                Toast.makeText(context, "当前状态不可切换", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = runtime.state == AutoClickRunState.Running ||
                            runtime.state == AutoClickRunState.Paused,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (runtime.state == AutoClickRunState.Paused) "继续" else "暂停")
                    }
                    Button(
                        onClick = {
                            val stopped = AutoClickCoordinator.stop()
                            if (!stopped) {
                                Toast.makeText(context, "当前没有运行中的任务", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("停止")
                    }
                    Button(
                        onClick = {
                            val result = AutoClickCoordinator.saveProfile()
                            val tip = if (result.isSuccess) {
                                "自动点击配置已保存"
                            } else {
                                "保存失败：${result.exceptionOrNull()?.message}"
                            }
                            Toast.makeText(context, tip, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存配置")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "点击点列表", fontWeight = FontWeight.Bold)
                Button(onClick = { AutoClickCoordinator.addPoint() }) {
                    Text("添加点击点")
                }
            }
        }

        if (profile.points.isEmpty()) {
            item {
                Text(
                    text = "暂无点击点，可点击“添加点击点”或在悬浮窗中添加。",
                    color = MiuixTheme.colorScheme.onBackgroundVariant
                )
            }
        } else {
            items(profile.points, key = { it.id }) { point ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    colors = CardDefaults.defaultColors(
                        color = Color.White,
                        contentColor = MiuixTheme.colorScheme.onSurfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("点击点 #${point.id}", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "中心坐标：(${point.x}, ${point.y})",
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                        Text(
                            text = "延迟/触摸/重复：${point.delayMs}ms / ${point.touchDurationMs}ms / ${point.repeatCount}",
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                        Text(
                            text = "编辑方式：长按悬浮窗中的对应点击点",
                            color = Color(0xFF1D6ED8)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SmallActionButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .height(34.dp)
            .width(42.dp)
    ) {
        Text(text)
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
