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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.littleclicker.autoclick.AutoClickCoordinator
import com.example.littleclicker.autoclick.AutoClickPoint
import com.example.littleclicker.autoclick.AutoClickRunState
import kotlin.math.roundToInt

class FloatingWindowService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null

    private var panelOffset by mutableStateOf(IntOffset(72, 180))
    private lateinit var viewTreeSavedStateOwner: OverlaySavedStateOwner

    override fun onCreate() {
        super.onCreate()
        AutoClickCoordinator.initialize(this)
        viewTreeSavedStateOwner = OverlaySavedStateOwner()
    }

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
        if (::viewTreeSavedStateOwner.isInitialized) {
            viewTreeSavedStateOwner.markDestroyed()
        }
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
            setViewTreeSavedStateRegistryOwner(viewTreeSavedStateOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MaterialTheme {
                    val profile by AutoClickCoordinator.profile.collectAsState()
                    val runtime by AutoClickCoordinator.runtime.collectAsState()
                    OverlayContent(
                        points = profile.points,
                        panelOffset = panelOffset,
                        runState = runtime.state,
                        runMessage = runtime.message,
                        onPanelMoved = { drag ->
                            panelOffset = IntOffset(panelOffset.x + drag.x, panelOffset.y + drag.y)
                        },
                        onTargetMoved = { id, drag ->
                            AutoClickCoordinator.movePointBy(id, drag.x, drag.y)
                        },
                        onAddTarget = {
                            AutoClickCoordinator.addPoint()
                            Toast.makeText(this@FloatingWindowService, "已添加点击点", Toast.LENGTH_SHORT).show()
                        },
                        onRemoveTarget = { id ->
                            AutoClickCoordinator.removePoint(id)
                        },
                        onToggleRun = {
                            val changed = when (runtime.state) {
                                AutoClickRunState.Running -> AutoClickCoordinator.pause()
                                AutoClickRunState.Paused -> AutoClickCoordinator.resume()
                                else -> AutoClickCoordinator.startNow()
                            }
                            if (!changed) {
                                Toast.makeText(
                                    this@FloatingWindowService,
                                    "操作失败，请先检查无障碍服务",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onSave = {
                            val result = AutoClickCoordinator.saveProfile()
                            val text = if (result.isSuccess) "自动点击配置已保存" else "保存失败：${result.exceptionOrNull()?.message}"
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
    }

    private fun removeOverlay() {
        val view = composeView ?: return
        windowManager.removeView(view)
        composeView = null
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

        fun startAutoClickOverlay(context: Context) = start(context)

        fun stopAutoClickOverlay(context: Context) = stop(context)
    }

    private inner class OverlaySavedStateOwner : SavedStateRegistryOwner {
        private val controller = SavedStateRegistryController.create(this)
        private val lifecycleRegistry = LifecycleRegistry(this)

        init {
            controller.performAttach()
            controller.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val savedStateRegistry: SavedStateRegistry
            get() = controller.savedStateRegistry

        fun markDestroyed() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
    }
}

@Composable
private fun OverlayContent(
    points: List<AutoClickPoint>,
    panelOffset: IntOffset,
    runState: AutoClickRunState,
    runMessage: String?,
    onPanelMoved: (IntOffset) -> Unit,
    onTargetMoved: (id: Int, drag: IntOffset) -> Unit,
    onAddTarget: () -> Unit,
    onRemoveTarget: (id: Int) -> Unit,
    onToggleRun: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        points.forEachIndexed { index, point ->
            TargetBubble(
                label = (index + 1).toString(),
                offset = IntOffset(point.x, point.y),
                onDrag = { onTargetMoved(point.id, it) },
                onRemove = { onRemoveTarget(point.id) }
            )
        }
        FloatingPanel(
            panelOffset = panelOffset,
            runState = runState,
            runMessage = runMessage,
            onDrag = onPanelMoved,
            onAddPoint = onAddTarget,
            onToggleRun = onToggleRun,
            onSave = onSave,
            onClose = onClose
        )
    }
}

@Composable
private fun FloatingPanel(
    panelOffset: IntOffset,
    runState: AutoClickRunState,
    runMessage: String?,
    onDrag: (IntOffset) -> Unit,
    onAddPoint: () -> Unit,
    onToggleRun: () -> Unit,
    onSave: () -> Unit,
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
        colors = CardDefaults.cardColors(containerColor = Color(0xEFFFFAF2))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "自动点击悬浮窗", color = Color(0xFF263238))
            Text(
                text = runMessage ?: runState.name,
                color = Color(0xFF455A64),
                style = MaterialTheme.typography.bodySmall
            )
            PanelActionButton(
                icon = Icons.Filled.AddCircleOutline,
                contentDescription = "添加点击点",
                onClick = onAddPoint
            )
            PanelActionButton(
                icon = if (runState == AutoClickRunState.Running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (runState == AutoClickRunState.Running) "暂停" else "开始/继续",
                onClick = onToggleRun
            )
            PanelActionButton(
                icon = Icons.Filled.Save,
                contentDescription = "保存",
                onClick = onSave
            )
            PanelActionButton(
                icon = Icons.Filled.Close,
                contentDescription = "关闭",
                onClick = onClose
            )
        }
    }
}

@Composable
private fun PanelActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(Color(0xFFF5F5F5), CircleShape)
            .border(1.dp, Color(0xFFE0E0E0), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color(0xFF37474F)
            )
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
