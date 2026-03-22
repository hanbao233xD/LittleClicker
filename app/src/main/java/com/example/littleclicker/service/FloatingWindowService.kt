package com.example.littleclicker.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import kotlin.math.roundToInt

class FloatingWindowService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null

    private val targets = mutableStateListOf<TargetPoint>()
    private var panelOffset by mutableStateOf(IntOffset(72, 180))
    private var isPlaying by mutableStateOf(false)
    private var nextTargetId = 1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return Service.START_NOT_STICKY
            }
            ACTION_START, null -> showOverlayIfNeeded()
            else -> showOverlayIfNeeded()
        }
        return Service.START_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun showOverlayIfNeeded() {
        if (composeView != null) return
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MaterialTheme {
                    OverlayContent(
                        targets = targets,
                        panelOffset = panelOffset,
                        isPlaying = isPlaying,
                        onPanelMoved = { drag ->
                            panelOffset = IntOffset(panelOffset.x + drag.x, panelOffset.y + drag.y)
                        },
                        onTargetMoved = { id, drag ->
                            val index = targets.indexOfFirst { it.id == id }
                            if (index >= 0) {
                                val current = targets[index]
                                targets[index] = current.copy(
                                    offset = IntOffset(
                                        current.offset.x + drag.x,
                                        current.offset.y + drag.y
                                    )
                                )
                            }
                        },
                        onAddTarget = {
                            addTarget()
                            Toast.makeText(this@FloatingWindowService, "已添加靶标", Toast.LENGTH_SHORT).show()
                        },
                        onRemoveTarget = { id ->
                            targets.removeAll { it.id == id }
                        },
                        onRecord = {
                            Toast.makeText(this@FloatingWindowService, "录制功能待接入（P4）", Toast.LENGTH_SHORT).show()
                        },
                        onTogglePlay = {
                            isPlaying = !isPlaying
                            val text = if (isPlaying) "已开始播放（占位）" else "已暂停（占位）"
                            Toast.makeText(this@FloatingWindowService, text, Toast.LENGTH_SHORT).show()
                        },
                        onClose = { stopSelf() }
                    )
                }
            }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(composeView, layoutParams)
        if (targets.isEmpty()) {
            addTarget()
            addTarget()
        }
    }

    private fun removeOverlay() {
        val view = composeView ?: return
        windowManager.removeView(view)
        composeView = null
    }

    private fun addTarget() {
        val base = 220 + targets.size * 110
        targets.add(TargetPoint(id = nextTargetId++, offset = IntOffset(base, base)))
    }

    companion object {
        const val ACTION_START = "com.example.littleclicker.action.START_FLOATING_WINDOW"
        const val ACTION_STOP = "com.example.littleclicker.action.STOP_FLOATING_WINDOW"

        fun start(context: Context) {
            context.startService(Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_START
            })
        }

        fun stop(context: Context) {
            context.startService(Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }
}

private data class TargetPoint(
    val id: Int,
    val offset: IntOffset,
)

@Composable
private fun OverlayContent(
    targets: List<TargetPoint>,
    panelOffset: IntOffset,
    isPlaying: Boolean,
    onPanelMoved: (IntOffset) -> Unit,
    onTargetMoved: (id: Int, drag: IntOffset) -> Unit,
    onAddTarget: () -> Unit,
    onRemoveTarget: (id: Int) -> Unit,
    onRecord: () -> Unit,
    onTogglePlay: () -> Unit,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        targets.forEachIndexed { index, target ->
            TargetBubble(
                label = (index + 1).toString(),
                offset = target.offset,
                onDrag = { onTargetMoved(target.id, it) },
                onRemove = { onRemoveTarget(target.id) }
            )
        }
        FloatingPanel(
            panelOffset = panelOffset,
            isPlaying = isPlaying,
            onDrag = onPanelMoved,
            onRecord = onRecord,
            onTogglePlay = onTogglePlay,
            onSettings = onAddTarget,
            onClose = onClose
        )
    }
}

@Composable
private fun FloatingPanel(
    panelOffset: IntOffset,
    isPlaying: Boolean,
    onDrag: (IntOffset) -> Unit,
    onRecord: () -> Unit,
    onTogglePlay: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
) {
    Card(
        modifier = Modifier
            .offset { panelOffset }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(IntOffset(dragAmount.x.roundToInt(), dragAmount.y.roundToInt()))
                }
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xE6262A36))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "LittleClicker 控制面板", color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRecord) { Text("录制") }
                Button(onClick = onTogglePlay) { Text(if (isPlaying) "暂停" else "播放") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSettings) { Text("设置") }
                Button(onClick = onClose) { Text("关闭") }
            }
        }
    }
}

@Composable
private fun TargetBubble(
    label: String,
    offset: IntOffset,
    onDrag: (IntOffset) -> Unit,
    onRemove: () -> Unit,
) {
    Box(
        modifier = Modifier
            .offset { offset }
            .size(64.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(IntOffset(dragAmount.x.roundToInt(), dragAmount.y.roundToInt()))
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(56.dp)
                .background(Color(0xCC1976D2), CircleShape)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = label, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .background(Color(0xCCB00020), CircleShape)
                .border(1.dp, Color.White, CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Text("x", color = Color.White)
        }
    }
}
