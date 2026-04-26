package com.example.littleclicker.ui

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.littleclicker.autoclick.AutoClickActionType
import com.example.littleclicker.autoclick.AutoClickCoordinator
import com.example.littleclicker.autoclick.AutoClickPoint
import com.example.littleclicker.autoclick.displayName
import com.example.littleclicker.autoclick.usesScreenCoordinates
import com.example.littleclicker.autoclick.usesTouchDuration
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ActionManageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val profile by AutoClickCoordinator.profile.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()
    val pageGradient = if (isDarkTheme) {
        listOf(Color(0xFF101219), Color(0xFF171B26))
    } else {
        listOf(Color(0xFFF7F9FF), Color(0xFFF1F5FF))
    }
    val topBarColor = pageGradient.first()
    val cardContainerColor = MiuixTheme.colorScheme.surfaceContainer
    val deleteTint = if (isDarkTheme) Color(0xFFFFA7A7) else Color(0xFFC62828)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                AutoClickCoordinator.initialize(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "动作管理",
                largeTitle = "动作管理",
                color = topBarColor,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = pageGradient
                    )
                )
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "可在这里添加、编辑和删除动作。动作悬浮窗中的改动也会同步到此处。",
                    color = MiuixTheme.colorScheme.onBackgroundVariant
                )
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "动作 #${point.id}",
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { showPointEditDialog(context, point) }) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "编辑动作"
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            AutoClickCoordinator.removePoint(point.id)
                                            Toast.makeText(context, "已删除动作 #${point.id}", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "删除动作",
                                            tint = deleteTint
                                        )
                                    }
                                }
                            }
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
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
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
