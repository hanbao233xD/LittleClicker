package com.example.littleclicker.service

import android.app.AlertDialog
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.littleclicker.autoclick.AutoClickCoordinator
import com.example.littleclicker.autoclick.AutoClickPoint
import com.example.littleclicker.autoclick.AutoClickRunState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class FloatingWindowService : LifecycleService() {

    private lateinit var windowManager: WindowManager

    private var panelView: ComposeView? = null
    private var panelLayoutParams: WindowManager.LayoutParams? = null
    private var panelSize: IntSize = IntSize.Zero
    private var panelOffset: IntOffset = IntOffset(72, 180)

    private val pointViews = linkedMapOf<Int, PointOverlay>()
    private var profileCollectJob: Job? = null
    private var runtimeCollectJob: Job? = null
    private var pointEditDialog: AlertDialog? = null

    private val bubbleSizePx by lazy {
        (64f * resources.displayMetrics.density).roundToInt()
    }
    private val bubbleHalfPx: Int
        get() = bubbleSizePx / 2

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
        removeAllOverlays()
        pointEditDialog?.dismiss()
        pointEditDialog = null
        if (::viewTreeSavedStateOwner.isInitialized) {
            viewTreeSavedStateOwner.markDestroyed()
        }
        super.onDestroy()
    }

    private fun showOverlayIfNeeded() {
        if (panelView != null) return
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            _overlayVisible.value = false
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createPanelOverlay()
        syncPointOverlays(AutoClickCoordinator.profile.value.points)
        _overlayVisible.value = true

        profileCollectJob?.cancel()
        profileCollectJob = lifecycleScope.launch {
            AutoClickCoordinator.profile.collect { profile ->
                syncPointOverlays(profile.points)
            }
        }

        runtimeCollectJob?.cancel()
        runtimeCollectJob = lifecycleScope.launch {
            AutoClickCoordinator.runtime.collect { runtime ->
                val touchable = runtime.state != AutoClickRunState.Running
                setPointOverlaysTouchable(touchable)
            }
        }
    }

    private fun createPanelOverlay() {
        val view = createComposeView().apply {
            setContent {
                MaterialTheme {
                    val runtime by AutoClickCoordinator.runtime.collectAsState()
                    FloatingPanel(
                        runState = runtime.state,
                        runMessage = runtime.message,
                        onSizeChanged = { size ->
                            panelSize = size
                            updatePanelOffset(panelOffset)
                        },
                        onDrag = { drag ->
                            val desired = IntOffset(panelOffset.x + drag.x, panelOffset.y + drag.y)
                            updatePanelOffset(desired)
                        },
                        onAddPoint = {
                            AutoClickCoordinator.addPoint()
                            Toast.makeText(this@FloatingWindowService, "已添加点击点", Toast.LENGTH_SHORT).show()
                        },
                        onToggleRun = {
                            val changed = when (runtime.state) {
                                AutoClickRunState.Running, AutoClickRunState.Paused -> AutoClickCoordinator.stop()
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
                            val tip = if (result.isSuccess) {
                                "自动点击配置已保存"
                            } else {
                                "保存失败：${result.exceptionOrNull()?.message}"
                            }
                            Toast.makeText(this@FloatingWindowService, tip, Toast.LENGTH_SHORT).show()
                        },
                        onClose = { stopSelf() }
                    )
                }
            }
        }

        val params = createLayoutParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            x = panelOffset.x
            y = panelOffset.y
        }

        windowManager.addView(view, params)
        panelView = view
        panelLayoutParams = params
    }

    private fun syncPointOverlays(points: List<AutoClickPoint>) {
        val pointIds = points.map { it.id }.toSet()
        val removedIds = pointViews.keys.filter { it !in pointIds }
        removedIds.forEach { id ->
            val overlay = pointViews.remove(id) ?: return@forEach
            runCatching { windowManager.removeView(overlay.view) }
        }

        points.forEach { point ->
            val boundedCenter = clampPointCenter(IntOffset(point.x, point.y))
            if (boundedCenter.x != point.x || boundedCenter.y != point.y) {
                AutoClickCoordinator.setPointPosition(point.id, boundedCenter.x, boundedCenter.y)
            }

            val overlay = pointViews[point.id] ?: createPointOverlay(point.id, boundedCenter)
            val params = overlay.layoutParams
            val windowOffset = centerToWindowOffset(boundedCenter)
            if (params.x != windowOffset.x || params.y != windowOffset.y) {
                params.x = windowOffset.x
                params.y = windowOffset.y
                windowManager.updateViewLayout(overlay.view, params)
            }
        }
    }

    private fun createPointOverlay(pointId: Int, center: IntOffset): PointOverlay {
        val view = createComposeView().apply {
            setContent {
                MaterialTheme {
                    val profile by AutoClickCoordinator.profile.collectAsState()
                    val index = profile.points.indexOfFirst { it.id == pointId }
                    val point = profile.points.firstOrNull { it.id == pointId }
                    if (point != null && index >= 0) {
                        TargetBubble(
                            label = (index + 1).toString(),
                            onDrag = { drag ->
                                if (drag.x != 0 || drag.y != 0) {
                                    val currentPoint = AutoClickCoordinator.profile.value.points
                                        .firstOrNull { it.id == pointId }
                                    if (currentPoint != null) {
                                        val desired = IntOffset(
                                            currentPoint.x + drag.x,
                                            currentPoint.y + drag.y
                                        )
                                        val bounded = clampPointCenter(desired)
                                        AutoClickCoordinator.setPointPosition(
                                            currentPoint.id,
                                            bounded.x,
                                            bounded.y
                                        )
                                    }
                                }
                            },
                            onDragEnd = {
                                AutoClickCoordinator.saveProfile()
                            },
                            onLongPress = {
                                showPointEditDialog(point)
                            },
                            onRemove = {
                                AutoClickCoordinator.removePoint(point.id)
                            }
                        )
                    }
                }
            }
        }

        val params = createLayoutParams(
            width = bubbleSizePx,
            height = bubbleSizePx
        ).apply {
            val windowOffset = centerToWindowOffset(center)
            x = windowOffset.x
            y = windowOffset.y
        }
        windowManager.addView(view, params)

        return PointOverlay(view = view, layoutParams = params).also {
            pointViews[pointId] = it
        }
    }

    private fun updatePanelOffset(desiredOffset: IntOffset) {
        val bounded = clampPanel(desiredOffset)
        panelOffset = bounded

        val params = panelLayoutParams ?: return
        val view = panelView ?: return
        if (params.x != bounded.x || params.y != bounded.y) {
            params.x = bounded.x
            params.y = bounded.y
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun clampPanel(offset: IntOffset): IntOffset {
        val screen = getScreenSize()
        val maxX = (screen.width - panelSize.width).coerceAtLeast(0)
        val maxY = (screen.height - panelSize.height).coerceAtLeast(0)
        return IntOffset(
            x = offset.x.coerceIn(0, maxX),
            y = offset.y.coerceIn(0, maxY)
        )
    }

    private fun clampPointCenter(center: IntOffset): IntOffset {
        val screen = getScreenSize()
        val minX = bubbleHalfPx
        val minY = bubbleHalfPx
        val maxX = (screen.width - bubbleHalfPx).coerceAtLeast(minX)
        val maxY = (screen.height - bubbleHalfPx).coerceAtLeast(minY)
        return IntOffset(
            x = center.x.coerceIn(minX, maxX),
            y = center.y.coerceIn(minY, maxY)
        )
    }

    private fun centerToWindowOffset(center: IntOffset): IntOffset {
        return IntOffset(
            x = center.x - bubbleHalfPx,
            y = center.y - bubbleHalfPx
        )
    }

    private fun getScreenSize(): IntSize {
        val dm = resources.displayMetrics
        return IntSize(dm.widthPixels, dm.heightPixels)
    }

    private fun showPointEditDialog(point: AutoClickPoint) {
        pointEditDialog?.dismiss()

        val latestPoint = AutoClickCoordinator.profile.value.points
            .firstOrNull { it.id == point.id }
            ?: point

        val xInput = createNumberInput(latestPoint.x)
        val yInput = createNumberInput(latestPoint.y)
        val delayInput = createNumberInput(latestPoint.delayMs.toInt())
        val touchInput = createNumberInput(latestPoint.touchDurationMs.toInt())
        val repeatInput = createNumberInput(latestPoint.repeatCount)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 10)
            addView(buildField("X 中心坐标", xInput))
            addView(buildField("Y 中心坐标", yInput))
            addView(buildField("点击延迟(ms)", delayInput))
            addView(buildField("触摸时长(ms)", touchInput))
            addView(buildField("重复次数", repeatInput))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("编辑点击点 #${point.id}")
            .setView(container)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val currentPoint = AutoClickCoordinator.profile.value.points
                    .firstOrNull { it.id == point.id }
                    ?: point
                val x = xInput.text.toString().toIntOrNull() ?: currentPoint.x
                val y = yInput.text.toString().toIntOrNull() ?: currentPoint.y
                val delayMs = xInputToLong(delayInput, currentPoint.delayMs, min = 0L)
                val touchMs = xInputToLong(touchInput, currentPoint.touchDurationMs, min = 1L)
                val repeat = repeatInput.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: currentPoint.repeatCount
                val bounded = clampPointCenter(IntOffset(x, y))

                AutoClickCoordinator.updatePointConfig(
                    pointId = point.id,
                    x = bounded.x,
                    y = bounded.y,
                    delayMs = delayMs,
                    touchDurationMs = touchMs,
                    repeatCount = repeat
                )
                AutoClickCoordinator.saveProfile()
                Toast.makeText(this, "点击点 #${point.id} 已更新", Toast.LENGTH_SHORT).show()
            }
            .create()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }

        pointEditDialog = dialog
        dialog.show()
    }

    private fun xInputToLong(input: EditText, fallback: Long, min: Long): Long {
        return input.text.toString().toLongOrNull()?.coerceAtLeast(min) ?: fallback
    }

    private fun buildField(title: String, input: EditText): LinearLayout {
        val titleView = EditText(this).apply {
            setText(title)
            isEnabled = false
            setTextColor(0xFF455A64.toInt())
            background = null
            isFocusable = false
            isClickable = false
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleView)
            addView(input)
        }
    }

    private fun createNumberInput(defaultValue: Int): EditText {
        return EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(defaultValue.toString())
        }
    }

    private fun createLayoutParams(width: Int, height: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
        }
    }

    private fun createComposeView(): ComposeView {
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(viewTreeSavedStateOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        }
    }

    private fun removeAllOverlays() {
        profileCollectJob?.cancel()
        profileCollectJob = null
        runtimeCollectJob?.cancel()
        runtimeCollectJob = null

        panelView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
        panelLayoutParams = null

        pointViews.values.forEach { overlay ->
            runCatching { windowManager.removeView(overlay.view) }
        }
        pointViews.clear()
        _overlayVisible.value = false
    }

    private fun setPointOverlaysTouchable(touchable: Boolean) {
        val touchableFlag = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        pointViews.values.forEach { overlay ->
            val params = overlay.layoutParams
            val hasFlag = (params.flags and touchableFlag) != 0
            val needFlag = !touchable
            if (hasFlag == needFlag) return@forEach

            params.flags = if (needFlag) {
                params.flags or touchableFlag
            } else {
                params.flags and touchableFlag.inv()
            }
            runCatching { windowManager.updateViewLayout(overlay.view, params) }
        }
    }

    companion object {
        const val ACTION_START = "com.example.littleclicker.action.START_FLOATING_WINDOW"
        const val ACTION_STOP = "com.example.littleclicker.action.STOP_FLOATING_WINDOW"

        private val _overlayVisible = MutableStateFlow(false)
        val overlayVisible: StateFlow<Boolean> = _overlayVisible.asStateFlow()

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

private data class PointOverlay(
    val view: ComposeView,
    val layoutParams: WindowManager.LayoutParams,
)

@Composable
private fun FloatingPanel(
    runState: AutoClickRunState,
    runMessage: String?,
    onSizeChanged: (IntSize) -> Unit,
    onDrag: (IntOffset) -> Unit,
    onAddPoint: () -> Unit,
    onToggleRun: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Card(
        modifier = Modifier
            .onSizeChanged(onSizeChanged)
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
            Text(
                text = "拖动面板/点击点可定位；长按点击点可编辑",
                color = Color(0xFF607D8B),
                style = MaterialTheme.typography.bodySmall
            )
            PanelActionButton(
                icon = Icons.Filled.AddCircleOutline,
                contentDescription = "添加点击点",
                onClick = onAddPoint
            )
            PanelActionButton(
                icon = if (runState == AutoClickRunState.Running || runState == AutoClickRunState.Paused) {
                    Icons.Filled.Stop
                } else {
                    Icons.Filled.PlayArrow
                },
                contentDescription = if (runState == AutoClickRunState.Running || runState == AutoClickRunState.Paused) {
                    "停止"
                } else {
                    "开始"
                },
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
private fun TargetBubble(
    label: String,
    onDrag: (IntOffset) -> Unit,
    onDragEnd: () -> Unit,
    onLongPress: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = onDragEnd,
                ) { change, dragAmount ->
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
