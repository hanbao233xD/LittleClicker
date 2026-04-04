package com.example.littleclicker.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.example.littleclicker.ConfigManageActivity
import com.example.littleclicker.R
import com.example.littleclicker.autoclick.AutoClickActionType
import com.example.littleclicker.autoclick.AutoClickCoordinator
import com.example.littleclicker.autoclick.AutoClickPoint
import com.example.littleclicker.autoclick.AutoClickRecordingMode
import com.example.littleclicker.autoclick.AutoClickRunMode
import com.example.littleclicker.autoclick.TimeSyncState
import com.example.littleclicker.autoclick.displayName
import com.example.littleclicker.autoclick.usesScreenCoordinates
import com.example.littleclicker.autoclick.usesTouchDuration
import com.example.littleclicker.service.FloatingWindowService
import com.example.littleclicker.service.TimerFloatingWindowService
import com.example.littleclicker.update.AppNoticeInfo
import com.example.littleclicker.update.AppUpdateInfo
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.random.Random
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.extra.WindowDropdown
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class PermissionStatus(
    val title: String,
    val description: String,
    val granted: Boolean,
    val onClick: () -> Unit,
)

@Composable
internal fun AutoClickScreen(innerPadding: PaddingValues) {
    AutoClickScreen(
        innerPadding = innerPadding,
        updateInfo = null,
        noticeInfo = null
    )
}

@Composable
internal fun AutoClickScreen(
    innerPadding: PaddingValues,
    updateInfo: AppUpdateInfo?,
    noticeInfo: AppNoticeInfo?,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshToken by remember { mutableIntStateOf(0) }

    val profile by AutoClickCoordinator.profile.collectAsState()
    val overlayEnabled by FloatingWindowService.overlayVisible.collectAsState()
    val timerOverlayEnabled by TimerFloatingWindowService.overlayVisible.collectAsState()
    val runtime by AutoClickCoordinator.runtime.collectAsState()
    val timeSync by AutoClickCoordinator.timeSync.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()
    val pageGradient = if (isDarkTheme) {
        listOf(Color(0xFF101219), Color(0xFF171B26))
    } else {
        listOf(Color(0xFFF5F6FA), Color(0xFFEFF3FF))
    }
    val cardContainerColor = MiuixTheme.colorScheme.surfaceContainer
    val successColor = if (isDarkTheme) Color(0xFF7AD7A1) else Color(0xFF1F8B4C)

    val nowAlignedMillis by produceState(initialValue = AutoClickCoordinator.currentAlignedNowMillis()) {
        while (true) {
            value = AutoClickCoordinator.currentAlignedNowMillis()
            delay(100L)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshToken++
                AutoClickCoordinator.initialize(context)
                AutoClickCoordinator.syncNtpTime(force = false)
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
    val pendingStatuses = statuses.filterNot { it.granted }
    val randomTip = remember(refreshToken) { context.loadRandomAutoClickTip() }
    val runModeItems = listOf("运行一次", "循环运行直至手动停止")
    val recordModeItems = listOf("仅录制", "录制时穿透到应用")
    val selectedRunModeIndex = when (profile.runMode) {
        AutoClickRunMode.RunOnce -> 0
        AutoClickRunMode.LoopUntilStopped -> 1
    }
    val selectedRecordModeIndex = when (profile.recordingMode) {
        AutoClickRecordingMode.RecordOnly -> 0
        AutoClickRecordingMode.RecordAndPassThrough -> 1
    }
    var loopIntervalDelayInput by remember(profile.loopIntervalDelayMs, profile.runMode) {
        mutableStateOf(profile.loopIntervalDelayMs.toString())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = pageGradient
                )
            )
            .padding(innerPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "定时点击器Ultra",
                style = MiuixTheme.textStyles.title1,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "你的抢购、任务助手。本软件永久免费，下载：https://littlecold.cn",
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )
        }

        if (noticeInfo != null) {
            item {
                ActionLinkCard(
                    title = "公告",
                    content = noticeInfo.content,
                    onClick = { openExternalLink(context, noticeInfo.link) }
                )
            }
        }

        if (updateInfo != null) {
            item {
                ActionLinkCard(
                    title = "检测到更新！点击下载",
                    content = updateInfo.changelog,
                    onClick = { openExternalLink(context, updateInfo.downloadUrl) }
                )
            }
        }

        item {
            if (pendingStatuses.isNotEmpty()) {
                Text(
                    text = "权限设置",
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    pendingStatuses.forEach { status ->
                        PermissionCard(
                            status = status,
                            isDarkTheme = isDarkTheme
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "权限设置",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "权限已就绪，准备开始点击",
                        color = successColor
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                colors = CardDefaults.defaultColors(
                    color = cardContainerColor,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("动作悬浮窗与运行方式")
                    Text(
                        text = "状态：${runtime.state} ${runtime.message ?: ""}",
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                    SuperSwitch(
                        checked = overlayEnabled,
                        onCheckedChange = { shouldEnable ->
                            if (shouldEnable) {
                                if (!ensureOverlayStartPermissions(context)) {
                                    return@SuperSwitch
                                }
                                FloatingWindowService.startAutoClickOverlay(context)
                                Toast.makeText(context, "悬浮窗已开启（拖动小白条移动位置），配置完记得保存哦", Toast.LENGTH_SHORT).show()
                            } else {
                                FloatingWindowService.stopAutoClickOverlay(context)
                                Toast.makeText(context, "动作悬浮窗已关闭", Toast.LENGTH_SHORT).show()
                            }
                        },
                        title = "动作悬浮窗开关",
                        summary = "用于编辑动作与录制"
                    )
                    val navigationEventOwner = rememberNavigationEventDispatcherOwner(parent = null)
                    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides navigationEventOwner) {
                        WindowDropdown(
                            items = runModeItems,
                            selectedIndex = selectedRunModeIndex,
                            title = "运行方式",
                            summary = "运行时点按音量下键可强制停止",
                            onSelectedIndexChange = { index ->
                                val mode = if (index == 0) {
                                    AutoClickRunMode.RunOnce
                                } else {
                                    AutoClickRunMode.LoopUntilStopped
                                }
                                AutoClickCoordinator.updateRunMode(mode)
                            }
                        )
                        if (profile.runMode == AutoClickRunMode.LoopUntilStopped) {
                            MiuixTextField(
                                value = loopIntervalDelayInput,
                                onValueChange = { rawValue ->
                                    val filtered = rawValue.filter { it.isDigit() }
                                    loopIntervalDelayInput = filtered
                                    val parsed = filtered.toLongOrNull() ?: return@MiuixTextField
                                    if (parsed == profile.loopIntervalDelayMs) return@MiuixTextField
                                    AutoClickCoordinator.updateLoopIntervalDelay(parsed)
                                    AutoClickCoordinator.saveProfile()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = "每次循环延迟(ms)",
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Text(
                                text = "决定每次循环间隔多久",
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                        }
                        WindowDropdown(
                            items = recordModeItems,
                            selectedIndex = selectedRecordModeIndex,
                            title = "录制方式",
                            summary = "穿透模式下：录制一步，自动模拟一步",
                            onSelectedIndexChange = { index ->
                                val mode = if (index == 0) {
                                    AutoClickRecordingMode.RecordOnly
                                } else {
                                    AutoClickRecordingMode.RecordAndPassThrough
                                }
                                AutoClickCoordinator.updateRecordingMode(mode)
                            }
                        )
                    }
                    Text(
                        text = "提示：$randomTip",
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }
            }
        }

        item {
            TimerCard(
                nowAlignedMillis = nowAlignedMillis,
                runtimeLabel = "${runtime.state} ${runtime.message ?: ""}".trim(),
                timeSync = timeSync,
                scheduleRuleHms = profile.scheduleRuleHms,
                timerOverlayEnabled = timerOverlayEnabled,
                onPickTime = {
                    showHmsPickerDialog(
                        context = context,
                        initialMillis = nowAlignedMillis,
                        onSelected = { hour, minute, second ->
                            val success = AutoClickCoordinator.scheduleAtHms(hour, minute, second)
                            val tip = if (success) {
                                "定时已设置：${String.format("%02d:%02d:%02d", hour, minute, second)}"
                            } else {
                                "设定时间已过期，请重新设置"
                            }
                            Toast.makeText(context, tip, Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                onToggleTimerOverlay = {
                    if (timerOverlayEnabled) {
                        TimerFloatingWindowService.stop(context)
                        Toast.makeText(context, "定时悬浮窗已关闭", Toast.LENGTH_SHORT).show()
                    } else {
                        if (!ensureOverlayStartPermissions(context)) {
                            return@TimerCard
                        }
                        TimerFloatingWindowService.start(context)
                        Toast.makeText(context, "定时悬浮窗已开启", Toast.LENGTH_SHORT).show()
                    }
                },
                onConfigNtp = {
                    showNtpServerConfigDialog(
                        context = context,
                        initialHost = profile.ntpServerHost,
                        onSave = { host ->
                            AutoClickCoordinator.updateNtpServer(host)
                            Toast.makeText(context, "NTP服务器已更新：$host", Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                isDarkTheme = isDarkTheme,
                cardContainerColor = cardContainerColor
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                colors = CardDefaults.defaultColors(
                    color = cardContainerColor,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("配置管理")
                    Text(
                        text = "${profile.name}（循环 ${profile.cycleCount} 次）",
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                    Button(
                        onClick = {
                            context.startActivity(Intent(context, ConfigManageActivity::class.java))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("打开配置管理")
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
                Text(text = "动作列表", fontWeight = FontWeight.Bold)
                Button(onClick = { showAddActionDialog(context) }) {
                    Text("添加动作")
                }
            }
        }

        if (profile.points.isEmpty()) {
            item {
                Text(
                    text = "暂无动作，可点击“添加动作”或在动作悬浮窗中添加。",
                    color = MiuixTheme.colorScheme.onBackgroundVariant
                )
            }
        } else {
            items(profile.points, key = { it.id }) { point ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                colors = CardDefaults.defaultColors(
                    color = cardContainerColor,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainer
                )
            ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("动作 #${point.id}", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "动作类型：${point.actionType.displayName}",
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                        if (point.actionType.usesScreenCoordinates) {
                            Text(
                                text = "中心坐标：(${point.x}, ${point.y})",
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                        }
                        Text(
                            text = if (point.actionType.usesTouchDuration) {
                                "延迟/触摸/重复：${point.delayMs}ms / ${point.touchDurationMs}ms / ${point.repeatCount}"
                            } else {
                                "延迟/重复：${point.delayMs}ms / ${point.repeatCount}"
                            },
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                        if (point.actionType == AutoClickActionType.Swipe) {
                            Text(
                                text = "滑动终点：(${point.endX ?: point.x + 200}, ${point.endY ?: point.y})",
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showPointEditDialog(context, point) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("编辑动作")
                            }
                            Button(
                                onClick = {
                                    AutoClickCoordinator.removePoint(point.id)
                                    Toast.makeText(context, "已删除动作 #${point.id}", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("删除动作")
                            }
                        }
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
private fun ActionLinkCard(
    title: String,
    content: String,
    onClick: () -> Unit,
) {
    val accentColor = MiuixTheme.colorScheme.primary
    val contentColor = Color.White
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        colors = CardDefaults.defaultColors(
            color = accentColor,
            contentColor = contentColor
        ),
        onClick = onClick,
        insideMargin = PaddingValues(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = content,
                color = contentColor.copy(alpha = 0.95f)
            )
        }
    }
}

@Composable
private fun TimerCard(
    nowAlignedMillis: Long,
    runtimeLabel: String,
    timeSync: TimeSyncState,
    scheduleRuleHms: String?,
    timerOverlayEnabled: Boolean,
    onPickTime: () -> Unit,
    onToggleTimerOverlay: () -> Unit,
    onConfigNtp: () -> Unit,
    isDarkTheme: Boolean,
    cardContainerColor: Color,
) {
    val nowHms = formatHms(nowAlignedMillis)
    val tenths = (nowAlignedMillis % 1000L + 1000L) % 1000L / 100L
    val syncLabel = if (timeSync.isSynced) {
        "NTP已校准，延迟${timeSync.delayMillis ?: 0}ms"
    } else {
        "NTP未校准，已回退本机时间"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        colors = CardDefaults.defaultColors(
            color = cardContainerColor,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("定时", fontWeight = FontWeight.Bold)
            Text(
                text = syncLabel,
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                colors = CardDefaults.defaultColors(
                    color = if (isDarkTheme) Color(0xFF252B36) else Color(0xFFF6F8FC),
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainer
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(154.dp)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    AdaptiveNowTimeText(
                        nowHms = nowHms,
                        tenths = tenths,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Button(
                onClick = onPickTime,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text("选择时间")
            }
            Button(
                onClick = onToggleTimerOverlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text(if (timerOverlayEnabled) "关闭定时悬浮窗" else "开启定时悬浮窗")
            }
            Button(
                onClick = onConfigNtp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text("配置ntp服务器")
            }
            Text(
                "设定时间：${scheduleRuleHms ?: "未设置"}",
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )
            Text(
                "运行状态：$runtimeLabel",
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )
        }
    }
}

@Composable
private fun AdaptiveNowTimeText(
    nowHms: String,
    tenths: Long,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value
        val fitSp = computeFitFontSp(widthDp = widthDp, heightDp = heightDp)
        val content = buildAnnotatedString {
            append(nowHms)
            pushStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFEAA4BE)))
            append(".$tenths")
            pop()
        }
        Text(
            text = content,
            fontSize = fitSp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun computeFitFontSp(widthDp: Float, heightDp: Float): TextUnit {
    val byWidth = widthDp / 6.0f
    val byHeight = heightDp * 0.76f
    return minOf(byWidth, byHeight).coerceIn(36f, 92f).sp
}

private fun openExternalLink(context: Context, link: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val opened = runCatching { context.startActivity(intent) }.isSuccess
    if (!opened) {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}

private fun showNtpServerConfigDialog(
    context: Context,
    initialHost: String,
    onSave: (String) -> Unit,
) {
    val input = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_TEXT
        setText(initialHost)
        setSelection(text?.length ?: 0)
        hint = "ntp.aliyun.com"
    }
    AlertDialog.Builder(context)
        .setTitle("配置NTP服务器")
        .setView(input)
        .setMessage("默认：ntp.aliyun.com")
        .setNegativeButton("取消", null)
        .setPositiveButton("保存") { _, _
            -> onSave(input.text.toString().trim().ifBlank { "ntp.aliyun.com" })
        }
        .show()
}

private fun showHmsPickerDialog(
    context: Context,
    initialMillis: Long,
    onSelected: (Int, Int, Int) -> Unit,
) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initialMillis }
    val hourPicker = NumberPicker(context).apply {
        minValue = 0
        maxValue = 23
        value = calendar.get(Calendar.HOUR_OF_DAY)
        setFormatter { String.format("%02d", it) }
    }
    val minutePicker = NumberPicker(context).apply {
        minValue = 0
        maxValue = 59
        value = calendar.get(Calendar.MINUTE)
        setFormatter { String.format("%02d", it) }
    }
    val secondPicker = NumberPicker(context).apply {
        minValue = 0
        maxValue = 59
        value = 0
        setFormatter { String.format("%02d", it) }
    }

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(40, 20, 40, 10)
        addView(hourPicker, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(minutePicker, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(secondPicker, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    AlertDialog.Builder(context)
        .setTitle("选择时间（hh:mm:ss）")
        .setView(container)
        .setNegativeButton("取消", null)
        .setPositiveButton("确认") { _, _ ->
            onSelected(hourPicker.value, minutePicker.value, secondPicker.value)
        }
        .show()
}

private fun showPointEditDialog(
    context: Context,
    point: AutoClickPoint,
) {
    val latestPoint = AutoClickCoordinator.profile.value.points.firstOrNull { it.id == point.id } ?: point
    val xInput = createNumberInput(context, latestPoint.x)
    val yInput = createNumberInput(context, latestPoint.y)
    val endXInput = createNumberInput(context, latestPoint.endX ?: (latestPoint.x + 200))
    val endYInput = createNumberInput(context, latestPoint.endY ?: latestPoint.y)
    val delayInput = createNumberInput(context, latestPoint.delayMs.toInt())
    val touchInput = createNumberInput(context, latestPoint.touchDurationMs.toInt())
    val repeatInput = createNumberInput(context, latestPoint.repeatCount)
    val isSwipeAction = latestPoint.actionType == AutoClickActionType.Swipe
    val usesScreenCoordinates = latestPoint.actionType.usesScreenCoordinates
    val usesTouchDuration = latestPoint.actionType.usesTouchDuration

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(40, 30, 40, 10)
        addView(buildField(context, "动作类型", createReadOnlyInput(context, latestPoint.actionType.displayName)))
        if (usesScreenCoordinates) {
            addView(buildField(context, "X 中心坐标", xInput))
            addView(buildField(context, "Y 中心坐标", yInput))
        }
        if (isSwipeAction) {
            addView(buildField(context, "滑动结束X", endXInput))
            addView(buildField(context, "滑动结束Y", endYInput))
        }
        addView(buildField(context, "点击延迟(ms)", delayInput))
        if (usesTouchDuration) {
            addView(buildField(context, "触摸时长(ms)", touchInput))
        }
        addView(buildField(context, "重复次数", repeatInput))
    }
    val scrollContainer = ScrollView(context).apply {
        isFillViewport = true
        addView(container)
    }

    AlertDialog.Builder(context)
        .setTitle("编辑动作 #${point.id}")
        .setView(scrollContainer)
        .setNegativeButton("取消", null)
        .setPositiveButton("保存") { _, _ ->
            val currentPoint = AutoClickCoordinator.profile.value.points.firstOrNull { it.id == point.id } ?: point
            val actionType = currentPoint.actionType
            val safeX = if (actionType.usesScreenCoordinates) {
                xInput.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: currentPoint.x
            } else {
                currentPoint.x
            }
            val safeY = if (actionType.usesScreenCoordinates) {
                yInput.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: currentPoint.y
            } else {
                currentPoint.y
            }
            val endX = if (actionType == AutoClickActionType.Swipe) {
                endXInput.text.toString().toIntOrNull()
            } else {
                null
            }
            val endY = if (actionType == AutoClickActionType.Swipe) {
                endYInput.text.toString().toIntOrNull()
            } else {
                null
            }
            val safeEndX = if (actionType == AutoClickActionType.Swipe) {
                (endX ?: currentPoint.endX ?: (safeX + 200)).coerceAtLeast(0)
            } else {
                null
            }
            val safeEndY = if (actionType == AutoClickActionType.Swipe) {
                (endY ?: currentPoint.endY ?: safeY).coerceAtLeast(0)
            } else {
                null
            }

            AutoClickCoordinator.updatePointConfig(
                pointId = point.id,
                x = safeX,
                y = safeY,
                actionType = actionType,
                endX = safeEndX,
                endY = safeEndY,
                delayMs = xInputToLong(delayInput, currentPoint.delayMs, min = 0L),
                touchDurationMs = if (actionType.usesTouchDuration) {
                    xInputToLong(touchInput, currentPoint.touchDurationMs, min = 1L)
                } else {
                    currentPoint.touchDurationMs
                },
                repeatCount = repeatInput.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: currentPoint.repeatCount
            )
            val saveResult = AutoClickCoordinator.saveProfile()
            val tip = if (saveResult.isSuccess) {
                "动作 #${point.id} 已更新并自动保存"
            } else {
                "动作 #${point.id} 已更新，自动保存失败：${saveResult.exceptionOrNull()?.message ?: "未知错误"}"
            }
            Toast.makeText(context, tip, Toast.LENGTH_SHORT).show()
        }
        .show()
}

private fun showAddActionDialog(context: Context) {
    val labels = arrayOf("点击", "滑动", "Home", "Back", "多任务")
    AlertDialog.Builder(context)
        .setTitle("添加动作")
        .setItems(labels) { _, which ->
            val type = when (which) {
                1 -> AutoClickActionType.Swipe
                2 -> AutoClickActionType.Home
                3 -> AutoClickActionType.Back
                4 -> AutoClickActionType.Recents
                else -> AutoClickActionType.Click
            }
            val point = AutoClickCoordinator.addAction(type)
            Toast.makeText(context, "已添加：${type.displayName} #${point.id}", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("取消", null)
        .show()
}

private fun xInputToLong(input: EditText, fallback: Long, min: Long): Long {
    return input.text.toString().toLongOrNull()?.coerceAtLeast(min) ?: fallback
}

private fun buildField(context: Context, title: String, input: EditText): LinearLayout {
    val titleView = EditText(context).apply {
        setText(title)
        isEnabled = false
        setTextColor(0xFF455A64.toInt())
        background = null
        isFocusable = false
        isClickable = false
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(titleView)
        addView(input)
    }
}

private fun createNumberInput(context: Context, defaultValue: Int): EditText {
    return EditText(context).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(defaultValue.toString())
    }
}

private fun createReadOnlyInput(context: Context, value: String): EditText {
    return EditText(context).apply {
        setText(value)
        isEnabled = false
        setTextColor(0xFF455A64.toInt())
        background = null
        isFocusable = false
        isClickable = false
    }
}

private fun Context.loadRandomAutoClickTip(): String {
    val fallback = "本软件永久免费，欢迎使用。"
    return runCatching {
        resources.openRawResource(R.raw.autoclick_tips).bufferedReader().useLines { lines ->
            lines
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toList()
        }
    }.getOrDefault(emptyList())
        .takeIf { it.isNotEmpty() }
        ?.let { tips -> tips[Random.nextInt(tips.size)] }
        ?: fallback
}

@Composable
private fun PermissionCard(
    status: PermissionStatus,
    isDarkTheme: Boolean,
) {
    val grantedBgColor = if (isDarkTheme) Color(0xFF1B2E24) else Color(0xFFE7F6EC)
    val defaultBgColor = MiuixTheme.colorScheme.surfaceContainer
    val grantedTextColor = if (isDarkTheme) Color(0xFF7AD7A1) else Color(0xFF1F8B4C)
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        colors = CardDefaults.defaultColors(
            color = if (status.granted) grantedBgColor else defaultBgColor,
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
                color = if (status.granted) grantedTextColor else MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
