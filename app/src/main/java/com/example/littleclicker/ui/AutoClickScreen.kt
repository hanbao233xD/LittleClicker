package com.example.littleclicker.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.example.littleclicker.ActionManageActivity
import com.example.littleclicker.ConfigManageActivity
import com.example.littleclicker.R
import com.example.littleclicker.autoclick.AutoClickCoordinator
import com.example.littleclicker.autoclick.AutoClickRecordingMode
import com.example.littleclicker.autoclick.TimeSyncState
import com.example.littleclicker.service.FloatingWindowService
import com.example.littleclicker.service.TimerFloatingWindowService
import com.example.littleclicker.update.AppNoticeInfo
import com.example.littleclicker.update.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.random.Random
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
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
    val allPermissionsReady = pendingStatuses.isEmpty()
    val randomTip = remember(refreshToken) { context.loadRandomAutoClickTip() }
    val coroutineScope = rememberCoroutineScope()
    val recordModeItems = listOf("仅录制", "录制时穿透到应用")
    val selectedRecordModeIndex = when (profile.recordingMode) {
        AutoClickRecordingMode.RecordOnly -> 0
        AutoClickRecordingMode.RecordAndPassThrough -> 1
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
            val density = LocalDensity.current
            val titleFontSize = MiuixTheme.textStyles.title1.fontSize
            val titleIconSize = with(density) { titleFontSize.toDp() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AndroidView(
                    modifier = Modifier.size(titleIconSize),
                    factory = { viewContext ->
                        ImageView(viewContext).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            val appIcon = runCatching {
                                viewContext.packageManager.getApplicationIcon(viewContext.packageName)
                            }.getOrNull()
                            if (appIcon != null) {
                                setImageDrawable(appIcon)
                            } else {
                                setImageResource(R.mipmap.ic_launcher)
                            }
                            contentDescription = "应用图标"
                        }
                    }
                )
                Text(
                    text = "定时点击器Ultra",
                    style = MiuixTheme.textStyles.title1,
                    fontWeight = FontWeight.Bold
                )
            }

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
            val accessibilityEnabled = statuses.any { it.title == "无障碍服务" && it.granted }
            PermissionStatusSummaryCard(
                allReady = allPermissionsReady,
                isDarkTheme = isDarkTheme,
                accessibilityEnabled = accessibilityEnabled,
                onRootClick = {
                    coroutineScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            requestRootAndEnableAccessibility(context)
                        }
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        if (result.success) {
                            refreshToken++
                        }
                    }
                }
            )
            if (!allPermissionsReady) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    pendingStatuses.forEach { status ->
                        PermissionCard(
                            status = status,
                            isDarkTheme = isDarkTheme
                        )
                    }
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
                    Text("点击配置")
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
                                Toast.makeText(context, "悬浮窗已开启（拖动小白条移动位置）", Toast.LENGTH_SHORT).show()
                            } else {
                                FloatingWindowService.stopAutoClickOverlay(context)
                                Toast.makeText(context, "动作悬浮窗已关闭", Toast.LENGTH_SHORT).show()
                            }
                        },
                        title = "悬浮窗开关",
                        summary = "从这里开始探索"
                    )
                    Button(
                        onClick = {
                            context.startActivity(Intent(context, ActionManageActivity::class.java))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("参数设置")
                    }
                    val navigationEventOwner = rememberNavigationEventDispatcherOwner(parent = null)
                    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides navigationEventOwner) {
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
                    Button(
                        onClick = {
                            context.startActivity(Intent(context, ConfigManageActivity::class.java))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("配置管理")
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
                            val scheduleResult = AutoClickCoordinator.scheduleAtHms(hour, minute, second)
                            val hmsLabel = String.format("%02d:%02d:%02d", hour, minute, second)
                            val baseTip = if (scheduleResult.rolledToNextDay) {
                                "设定时间已过期，已自动顺延到明天：$hmsLabel"
                            } else {
                                "定时已设置：$hmsLabel"
                            }
                            val tip = if (!timerOverlayEnabled) {
                                if (ensureOverlayStartPermissions(context)) {
                                    TimerFloatingWindowService.start(context)
                                    "$baseTip，已自动开启定时悬浮窗"
                                } else {
                                    "$baseTip，请先完成权限后再开启定时悬浮窗"
                                }
                            } else {
                                "$baseTip，定时悬浮窗已开启"
                            }
                            Toast.makeText(context, tip, Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                onToggleTimerOverlay = { shouldEnable ->
                    if (!shouldEnable) {
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
    onToggleTimerOverlay: (Boolean) -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("定时悬浮窗开关", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "显示当前时间与设定时间",
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Switch(
                    checked = timerOverlayEnabled,
                    onCheckedChange = { shouldEnable -> onToggleTimerOverlay(shouldEnable) }
                )
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
private fun PermissionStatusSummaryCard(
    allReady: Boolean,
    isDarkTheme: Boolean,
    accessibilityEnabled: Boolean,
    onRootClick: () -> Unit,
) {
    val successBg = if (isDarkTheme) Color(0xFF1C3327) else Color(0xFFE9F8EE)
    val errorBg = if (isDarkTheme) Color(0xFF3A2023) else Color(0xFFFDEBEC)
    val successTint = if (isDarkTheme) Color(0xFF90E3B3) else Color(0xFF1F8B4C)
    val errorTint = if (isDarkTheme) Color(0xFFFFA7A7) else Color(0xFFC62828)
    val message = if (allReady) "权限已就绪" else "权限未就绪"

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        colors = CardDefaults.defaultColors(
            color = if (allReady) successBg else errorBg,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainer
        ),
        insideMargin = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (allReady) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                contentDescription = if (allReady) "成功" else "错误",
                tint = if (allReady) successTint else errorTint
            )
            SmallTitle(
                modifier = Modifier.weight(1f),
                text = message,
                insideMargin = PaddingValues(0.dp),
                textColor = if (allReady) successTint else errorTint
            )
            if (!accessibilityEnabled) {
                Text(
                    text = "ROOT激活无障碍",
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onRootClick)
                )
            }
        }
    }
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
