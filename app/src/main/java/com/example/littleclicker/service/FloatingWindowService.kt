package com.example.littleclicker.service

import android.app.AlertDialog
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.littleclicker.autoclick.AutoClickActionType
import com.example.littleclicker.autoclick.AutoClickPoint
import com.example.littleclicker.autoclick.AutoClickRecordingMode
import com.example.littleclicker.autoclick.AutoClickRunState
import com.example.littleclicker.autoclick.displayName
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val FLOATING_PANEL_SCALE_FACTOR = 1f
private const val POINT_BUBBLE_SCALE_FACTOR = 0.75f

class FloatingWindowService : LifecycleService() {

    private lateinit var windowManager: WindowManager

    private var panelView: ComposeView? = null
    private var panelLayoutParams: WindowManager.LayoutParams? = null
    private var recordCaptureView: ComposeView? = null
    private var recordCaptureLayoutParams: WindowManager.LayoutParams? = null
    private var panelSize: IntSize = IntSize.Zero
    private var panelOffset: IntOffset = IntOffset(72, 180)

    private val pointViews = linkedMapOf<Int, PointOverlayGroup>()
    private val viewHosts = mutableMapOf<ComposeView, OverlayHost>()
    private var profileCollectJob: Job? = null
    private var runtimeCollectJob: Job? = null
    private var recordingCollectJob: Job? = null
    private var recordCapturePassthroughJob: Job? = null
    private var pointEditDialog: AlertDialog? = null
    private var pointEditOverlayLowered: Boolean = false
    @Volatile
    private var ignoreRecordInputUntilMillis: Long = 0L

    private val bubbleSizePx by lazy {
        (76f * POINT_BUBBLE_SCALE_FACTOR * resources.displayMetrics.density).roundToInt()
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
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                persistProfileBeforeClose(showFailureToast = false)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START, null -> showOverlayIfNeeded()
            else -> showOverlayIfNeeded()
        }
        return START_STICKY
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
        createRecordCaptureOverlay()
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
                val touchable = runtime.state != AutoClickRunState.Running &&
                    runtime.state != AutoClickRunState.Paused &&
                    !AutoClickCoordinator.recording.value.isRecording
                setPointOverlaysTouchable(touchable)
            }
        }

        recordingCollectJob?.cancel()
        recordingCollectJob = lifecycleScope.launch {
            AutoClickCoordinator.recording.collect { recording ->
                if (!recording.isRecording) {
                    recordCapturePassthroughJob?.cancel()
                    recordCapturePassthroughJob = null
                }
                val shouldCaptureInput = recording.isRecording && !isRecordCaptureTemporarilyPassthrough()
                setRecordCaptureTouchable(shouldCaptureInput)
                val touchable = !recording.isRecording &&
                    AutoClickCoordinator.runtime.value.state != AutoClickRunState.Running &&
                    AutoClickCoordinator.runtime.value.state != AutoClickRunState.Paused
                setPointOverlaysTouchable(touchable)
            }
        }
    }

    private fun createPanelOverlay() {
        val view = createComposeView().apply {
            setContent {
                val isDarkTheme = isSystemInDarkTheme()
                MaterialTheme(colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()) {
                    val runtime by AutoClickCoordinator.runtime.collectAsState()
                    val recording by AutoClickCoordinator.recording.collectAsState()
                    FloatingPanel(
                        runState = runtime.state,
                        runMessage = runtime.message,
                        isRecording = recording.isRecording,
                        onSizeChanged = { size ->
                            panelSize = size
                            updatePanelOffset(panelOffset)
                        },
                        onDrag = { drag ->
                            val desired = IntOffset(panelOffset.x + drag.x, panelOffset.y + drag.y)
                            updatePanelOffset(desired)
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
                        onToggleRecord = {
                            val changed = if (recording.isRecording) {
                                AutoClickCoordinator.stopRecording()
                            } else {
                                AutoClickCoordinator.startRecording()
                            }
                            if (!changed) {
                                Toast.makeText(this@FloatingWindowService, "当前状态无法切换录制", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onAddAction = {
                            showAddActionDialog()
                        },
                        onDeleteLatest = {
                            val removed = AutoClickCoordinator.removeLatestPoint()
                            val tip = if (removed == null) {
                                "没有可删除的动作"
                            } else {
                                "已删除最新动作：${removed.actionType.displayName}"
                            }
                            Toast.makeText(this@FloatingWindowService, tip, Toast.LENGTH_SHORT).show()
                        },
                        onEditPoint = { point ->
                            showPointEditDialog(point)
                        },
                        onDeletePoint = { point ->
                            AutoClickCoordinator.removePoint(point.id)
                            Toast.makeText(this@FloatingWindowService, "已删除动作 #${point.id}", Toast.LENGTH_SHORT).show()
                        },
                        onClosePanel = {
                            val saved = persistProfileBeforeClose(showFailureToast = true)
                            val tip = if (saved) {
                                "动作悬浮窗已关闭"
                            } else {
                                "动作悬浮窗已关闭（本次保存失败）"
                            }
                            Toast.makeText(this@FloatingWindowService, tip, Toast.LENGTH_SHORT).show()
                            stopSelf()
                        },
                        isDarkTheme = isDarkTheme,
                        scaleFactor = FLOATING_PANEL_SCALE_FACTOR
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

        addManagedOverlayView(view, params)
        panelView = view
        panelLayoutParams = params
        syncOverlayCoordinateOffset(view, params)
    }

    private fun syncPointOverlays(points: List<AutoClickPoint>) {
        val pointIds = points.map { it.id }.toSet()
        val removedIds = pointViews.keys.filter { it !in pointIds }
        removedIds.forEach { id ->
            removePointOverlayGroup(pointViews.remove(id))
        }

        points.forEach { point ->
            val boundedCenter = clampPointCenter(IntOffset(point.x, point.y))
            if (point.actionType == AutoClickActionType.Swipe) {
                val rawEnd = IntOffset(
                    x = point.endX ?: (point.x + 200),
                    y = point.endY ?: point.y
                )
                val boundedEnd = clampPointCenter(rawEnd)
                if (boundedCenter.x != point.x ||
                    boundedCenter.y != point.y ||
                    boundedEnd.x != rawEnd.x ||
                    boundedEnd.y != rawEnd.y
                ) {
                    AutoClickCoordinator.updatePointConfig(
                        pointId = point.id,
                        x = boundedCenter.x,
                        y = boundedCenter.y,
                        actionType = AutoClickActionType.Swipe,
                        endX = boundedEnd.x,
                        endY = boundedEnd.y
                    )
                }
                syncSwipePointOverlay(pointId = point.id, start = boundedCenter, end = boundedEnd)
            } else {
                if (boundedCenter.x != point.x || boundedCenter.y != point.y) {
                    AutoClickCoordinator.updatePointConfig(
                        pointId = point.id,
                        x = boundedCenter.x,
                        y = boundedCenter.y,
                        actionType = AutoClickActionType.Click
                    )
                }
                syncClickPointOverlay(pointId = point.id, center = boundedCenter)
            }
        }
    }

    private fun syncClickPointOverlay(pointId: Int, center: IntOffset) {
        val existing = pointViews[pointId]
        if (existing !is ClickPointOverlayGroup) {
            removePointOverlayGroup(existing)
            pointViews[pointId] = createClickPointOverlay(pointId, center)
            return
        }
        updateOverlayWindow(
            overlay = existing.bubble,
            x = centerToWindowOffset(center).x,
            y = centerToWindowOffset(center).y,
            width = bubbleSizePx,
            height = bubbleSizePx
        )
    }

    private fun syncSwipePointOverlay(pointId: Int, start: IntOffset, end: IntOffset) {
        val existing = pointViews[pointId]
        if (existing !is SwipePointOverlayGroup) {
            removePointOverlayGroup(existing)
            pointViews[pointId] = createSwipePointOverlay(pointId, start, end)
            return
        }

        updateOverlayWindow(
            overlay = existing.startBubble,
            x = centerToWindowOffset(start).x,
            y = centerToWindowOffset(start).y,
            width = bubbleSizePx,
            height = bubbleSizePx
        )
        updateOverlayWindow(
            overlay = existing.endBubble,
            x = centerToWindowOffset(end).x,
            y = centerToWindowOffset(end).y,
            width = bubbleSizePx,
            height = bubbleSizePx
        )

        val lineSpec = buildSwipeLineSpec(start = start, end = end)
        updateOverlayWindow(
            overlay = existing.line,
            x = lineSpec.x,
            y = lineSpec.y,
            width = lineSpec.width,
            height = lineSpec.height
        )
        bindSwipeLineContent(existing.line.view, lineSpec)
    }

    private fun createClickPointOverlay(pointId: Int, center: IntOffset): ClickPointOverlayGroup {
        val view = createComposeView().apply {
            setContent {
                val isDarkTheme = isSystemInDarkTheme()
                MaterialTheme(colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()) {
                    val profile by AutoClickCoordinator.profile.collectAsState()
                    val index = profile.points.indexOfFirst { it.id == pointId }
                    val point = profile.points.firstOrNull { it.id == pointId }
                    if (point != null && index >= 0) {
                        TargetBubble(
                            label = "${index + 1}",
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
                                        AutoClickCoordinator.updatePointConfig(
                                            pointId = currentPoint.id,
                                            x = bounded.x,
                                            y = bounded.y,
                                            actionType = AutoClickActionType.Click
                                        )
                                    }
                                }
                            },
                            onDragEnd = {},
                            onLongPress = {
                                showPointEditDialog(point)
                            },
                            onRemove = {
                                AutoClickCoordinator.removePoint(point.id)
                            },
                            isDarkTheme = isDarkTheme,
                            scaleFactor = POINT_BUBBLE_SCALE_FACTOR
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
            applyPointOverlayTouchableFlag(this, shouldPointOverlaysBeTouchableNow())
        }
        val overlay = PointOverlay(view = view, layoutParams = params)
        addManagedOverlayView(view, params)
        return ClickPointOverlayGroup(bubble = overlay)
    }

    private fun createSwipePointOverlay(pointId: Int, start: IntOffset, end: IntOffset): SwipePointOverlayGroup {
        val startView = createComposeView().apply {
            setContent {
                val isDarkTheme = isSystemInDarkTheme()
                MaterialTheme(colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()) {
                    val profile by AutoClickCoordinator.profile.collectAsState()
                    val index = profile.points.indexOfFirst { it.id == pointId }
                    val point = profile.points.firstOrNull { it.id == pointId }
                    if (point != null && index >= 0 && point.actionType == AutoClickActionType.Swipe) {
                        TargetBubble(
                            label = "${index + 1}起",
                            onDrag = { drag ->
                                if (drag.x == 0 && drag.y == 0) return@TargetBubble
                                val latest = AutoClickCoordinator.profile.value.points
                                    .firstOrNull { it.id == pointId } ?: return@TargetBubble
                                val desired = IntOffset(latest.x + drag.x, latest.y + drag.y)
                                val bounded = clampPointCenter(desired)
                                AutoClickCoordinator.updatePointConfig(
                                    pointId = latest.id,
                                    x = bounded.x,
                                    y = bounded.y,
                                    actionType = AutoClickActionType.Swipe
                                )
                            },
                            onDragEnd = {},
                            onLongPress = {
                                showPointEditDialog(point)
                            },
                            onRemove = {
                                AutoClickCoordinator.removePoint(point.id)
                            },
                            isDarkTheme = isDarkTheme,
                            scaleFactor = POINT_BUBBLE_SCALE_FACTOR
                        )
                    }
                }
            }
        }

        val startParams = createLayoutParams(
            width = bubbleSizePx,
            height = bubbleSizePx
        ).apply {
            val windowOffset = centerToWindowOffset(start)
            x = windowOffset.x
            y = windowOffset.y
            applyPointOverlayTouchableFlag(this, shouldPointOverlaysBeTouchableNow())
        }
        addManagedOverlayView(startView, startParams)
        val startOverlay = PointOverlay(view = startView, layoutParams = startParams)

        val endView = createComposeView().apply {
            setContent {
                val isDarkTheme = isSystemInDarkTheme()
                MaterialTheme(colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()) {
                    val profile by AutoClickCoordinator.profile.collectAsState()
                    val index = profile.points.indexOfFirst { it.id == pointId }
                    val point = profile.points.firstOrNull { it.id == pointId }
                    if (point != null && index >= 0 && point.actionType == AutoClickActionType.Swipe) {
                        TargetBubble(
                            label = "${index + 1}终",
                            onDrag = { drag ->
                                if (drag.x == 0 && drag.y == 0) return@TargetBubble
                                val latest = AutoClickCoordinator.profile.value.points
                                    .firstOrNull { it.id == pointId } ?: return@TargetBubble
                                val currentEnd = IntOffset(
                                    latest.endX ?: (latest.x + 200),
                                    latest.endY ?: latest.y
                                )
                                val desired = IntOffset(currentEnd.x + drag.x, currentEnd.y + drag.y)
                                val bounded = clampPointCenter(desired)
                                AutoClickCoordinator.updatePointConfig(
                                    pointId = latest.id,
                                    actionType = AutoClickActionType.Swipe,
                                    endX = bounded.x,
                                    endY = bounded.y
                                )
                            },
                            onDragEnd = {},
                            onLongPress = {
                                showPointEditDialog(point)
                            },
                            onRemove = {
                                AutoClickCoordinator.removePoint(point.id)
                            },
                            isDarkTheme = isDarkTheme,
                            scaleFactor = POINT_BUBBLE_SCALE_FACTOR
                        )
                    }
                }
            }
        }

        val endParams = createLayoutParams(
            width = bubbleSizePx,
            height = bubbleSizePx
        ).apply {
            val windowOffset = centerToWindowOffset(end)
            x = windowOffset.x
            y = windowOffset.y
            applyPointOverlayTouchableFlag(this, shouldPointOverlaysBeTouchableNow())
        }
        addManagedOverlayView(endView, endParams)
        val endOverlay = PointOverlay(view = endView, layoutParams = endParams)

        val lineSpec = buildSwipeLineSpec(start = start, end = end)
        val lineView = createComposeView()
        bindSwipeLineContent(lineView, lineSpec)
        val lineParams = createLayoutParams(
            width = lineSpec.width,
            height = lineSpec.height
        ).apply {
            x = lineSpec.x
            y = lineSpec.y
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        addManagedOverlayView(lineView, lineParams)
        val lineOverlay = PointOverlay(
            view = lineView,
            layoutParams = lineParams,
            alwaysNotTouchable = true
        )
        runCatching {
            removeManagedOverlayView(startView)
            removeManagedOverlayView(endView)
            addManagedOverlayView(startView, startParams)
            addManagedOverlayView(endView, endParams)
        }

        return SwipePointOverlayGroup(
            startBubble = startOverlay,
            endBubble = endOverlay,
            line = lineOverlay
        )
    }

    private fun updateOverlayWindow(
        overlay: PointOverlay,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        val params = overlay.layoutParams
        if (params.x == x && params.y == y && params.width == width && params.height == height) {
            return
        }
        params.x = x
        params.y = y
        params.width = width
        params.height = height
        updateManagedOverlayView(overlay.view, params)
    }

    private fun removePointOverlayGroup(group: PointOverlayGroup?) {
        group?.overlays()?.forEach { overlay ->
            removeManagedOverlayView(overlay.view)
        }
    }

    private fun bindSwipeLineContent(view: ComposeView, spec: SwipeLineSpec) {
        view.setContent {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    color = Color(0xFF3A86FF),
                    start = spec.startOffset,
                    end = spec.endOffset,
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }

    private fun buildSwipeLineSpec(start: IntOffset, end: IntOffset): SwipeLineSpec {
        val density = resources.displayMetrics.density
        val padding = max(2, (2f * density).roundToInt())
        val x = (min(start.x, end.x) - padding).coerceAtLeast(0)
        val y = (min(start.y, end.y) - padding).coerceAtLeast(0)
        val width = (kotlin.math.abs(start.x - end.x).coerceAtLeast(1) + padding * 2)
        val height = (kotlin.math.abs(start.y - end.y).coerceAtLeast(1) + padding * 2)
        return SwipeLineSpec(
            x = x,
            y = y,
            width = width,
            height = height,
            startOffset = Offset((start.x - x).toFloat(), (start.y - y).toFloat()),
            endOffset = Offset((end.x - x).toFloat(), (end.y - y).toFloat())
        )
    }

    private fun updatePanelOffset(desiredOffset: IntOffset) {
        val bounded = clampPanel(desiredOffset)
        panelOffset = bounded

        val params = panelLayoutParams ?: return
        val view = panelView ?: return
        if (params.x != bounded.x || params.y != bounded.y) {
            params.x = bounded.x
            params.y = bounded.y
            updateManagedOverlayView(view, params)
            syncOverlayCoordinateOffset(view, params)
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

    private fun syncOverlayCoordinateOffset(
        view: ComposeView,
        params: WindowManager.LayoutParams,
    ) {
        view.post {
            val location = IntArray(2)
            runCatching { view.getLocationOnScreen(location) }
                .onSuccess {
                    AutoClickCoordinator.updateOverlayCoordinateOffset(
                        offsetX = location[0] - params.x,
                        offsetY = location[1] - params.y
                    )
                }
        }
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
        val endXInput = createNumberInput(latestPoint.endX ?: latestPoint.x + 200)
        val endYInput = createNumberInput(latestPoint.endY ?: latestPoint.y)
        val delayInput = createNumberInput(latestPoint.delayMs.toInt())
        val touchInput = createNumberInput(latestPoint.touchDurationMs.toInt())
        val repeatInput = createNumberInput(latestPoint.repeatCount)
        val isSwipeAction = latestPoint.actionType == AutoClickActionType.Swipe

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 10)
            addView(buildField("动作类型", createReadOnlyInput(latestPoint.actionType.displayName)))
            addView(buildField("X 中心坐标", xInput))
            addView(buildField("Y 中心坐标", yInput))
            if (isSwipeAction) {
                addView(buildField("滑动结束X", endXInput))
                addView(buildField("滑动结束Y", endYInput))
            }
            addView(buildField("点击延迟(ms)", delayInput))
            addView(buildField("触摸时长(ms)", touchInput))
            addView(buildField("重复次数", repeatInput))
        }
        val scrollContainer = ScrollView(this).apply {
            isFillViewport = true
            addView(container)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("编辑点击点 #${point.id}")
            .setView(scrollContainer)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val currentPoint = AutoClickCoordinator.profile.value.points
                    .firstOrNull { it.id == point.id }
                    ?: point
                val x = xInput.text.toString().toIntOrNull() ?: currentPoint.x
                val y = yInput.text.toString().toIntOrNull() ?: currentPoint.y
                val actionType = currentPoint.actionType
                val endX = if (actionType == AutoClickActionType.Swipe) endXInput.text.toString().toIntOrNull() else null
                val endY = if (actionType == AutoClickActionType.Swipe) endYInput.text.toString().toIntOrNull() else null
                val delayMs = xInputToLong(delayInput, currentPoint.delayMs, min = 0L)
                val touchMs = xInputToLong(touchInput, currentPoint.touchDurationMs, min = 1L)
                val repeat = repeatInput.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: currentPoint.repeatCount
                val bounded = clampPointCenter(IntOffset(x, y))
                val boundedEnd = if (actionType == AutoClickActionType.Swipe) {
                    val rawEnd = IntOffset(
                        (endX ?: currentPoint.endX ?: (bounded.x + 200)).coerceAtLeast(0),
                        (endY ?: currentPoint.endY ?: bounded.y).coerceAtLeast(0)
                    )
                    clampPointCenter(rawEnd)
                } else {
                    null
                }

                AutoClickCoordinator.updatePointConfig(
                    pointId = point.id,
                    x = bounded.x,
                    y = bounded.y,
                    actionType = actionType,
                    endX = boundedEnd?.x,
                    endY = boundedEnd?.y,
                    delayMs = delayMs,
                    touchDurationMs = touchMs,
                    repeatCount = repeat
                )
                val saveResult = AutoClickCoordinator.saveProfile()
                val tip = if (saveResult.isSuccess) {
                    "动作 #${point.id} 已更新并自动保存"
                } else {
                    "动作 #${point.id} 已更新，自动保存失败：${saveResult.exceptionOrNull()?.message ?: "未知错误"}"
                }
                Toast.makeText(this, tip, Toast.LENGTH_SHORT).show()
            }
            .create()

        lowerOverlaysForPointEditDialog()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }

        dialog.setOnDismissListener {
            if (pointEditDialog === dialog) {
                pointEditDialog = null
            }
            restoreOverlaysAfterPointEditDialog()
        }

        pointEditDialog = dialog
        runCatching { dialog.show() }
            .onFailure {
                pointEditDialog = null
                restoreOverlaysAfterPointEditDialog()
                Toast.makeText(this, "打开编辑框失败，请重试", Toast.LENGTH_SHORT).show()
            }
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

    private fun createReadOnlyInput(value: String): EditText {
        return EditText(this).apply {
            setText(value)
            isEnabled = false
            setTextColor(0xFF455A64.toInt())
            background = null
            isFocusable = false
            isClickable = false
        }
    }

    private fun showAddActionDialog() {
        val labels = arrayOf("点击", "滑动")
        val dialog = AlertDialog.Builder(this)
            .setTitle("添加动作")
            .setItems(labels) { _, which ->
                val type = when (which) {
                    1 -> AutoClickActionType.Swipe
                    else -> AutoClickActionType.Click
                }
                val point = AutoClickCoordinator.addAction(type)
                Toast.makeText(this, "已添加：${type.displayName} #${point.id}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .create()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        dialog.show()
    }

    private fun createRecordCaptureOverlay() {
        if (recordCaptureView != null) return
        val view = createComposeView().apply {
            setContent {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val start = down.position
                                val downUptime = down.uptimeMillis
                                var last = start
                                var upUptime = downUptime
                                var moved = false

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                        ?: break
                                    if (change.positionChanged()) {
                                        moved = true
                                        last = change.position
                                    }
                                    if (change.changedToUpIgnoreConsumed()) {
                                        last = change.position
                                        upUptime = change.uptimeMillis
                                        break
                                    }
                                    if (!change.pressed) {
                                        break
                                    }
                                }
                                if (shouldIgnoreRecordedInput()) return@awaitEachGesture

                                val rawDuration = (upUptime - downUptime).coerceAtLeast(1L)
                                val actionType = if (isSwipeGesture(start, last, moved, rawDuration)) {
                                    AutoClickActionType.Swipe
                                } else {
                                    AutoClickActionType.Click
                                }
                                val replayDuration = when (actionType) {
                                    AutoClickActionType.Click -> RECORDED_TAP_DURATION_MS
                                    AutoClickActionType.Swipe -> rawDuration.coerceAtLeast(RECORDED_SWIPE_MIN_DURATION_MS)
                                }
                                val recorded = AutoClickCoordinator.addRecordedAction(
                                    actionType = actionType,
                                    startX = start.x.roundToInt(),
                                    startY = start.y.roundToInt(),
                                    endX = if (actionType == AutoClickActionType.Swipe) last.x.roundToInt() else null,
                                    endY = if (actionType == AutoClickActionType.Swipe) last.y.roundToInt() else null,
                                    touchDurationMs = replayDuration
                                )
                                if (recorded != null) {
                                    val count = AutoClickCoordinator.recording.value.recordedCount
                                    Toast.makeText(
                                        this@FloatingWindowService,
                                        "已录制 $count.${recorded.actionType.displayName}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    if (AutoClickCoordinator.profile.value.recordingMode == AutoClickRecordingMode.RecordAndPassThrough) {
                                        val replayDelayMs = when (recorded.actionType) {
                                            AutoClickActionType.Click -> RECORDED_CLICK_REPLAY_DELAY_MS
                                            AutoClickActionType.Swipe -> RECORDED_SWIPE_REPLAY_DELAY_MS
                                        }
                                        val replayed = AutoClickAccessibilityService.replayRecordedAction(
                                            point = recorded,
                                            triggerDelayMs = replayDelayMs
                                        )
                                        if (replayed) {
                                            armRecordReplayPassThroughWindow(
                                                triggerDelayMs = replayDelayMs,
                                                touchDurationMs = recorded.touchDurationMs
                                            )
                                        } else {
                                            Toast.makeText(
                                                this@FloatingWindowService,
                                                "动作已录制，自动模拟失败（请检查无障碍）",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        }
                )
            }
        }

        val params = createLayoutParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT
        ).apply {
            x = 0
            y = 0
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        addManagedOverlayView(view, params)
        recordCaptureView = view
        recordCaptureLayoutParams = params
    }

    private fun isSwipeGesture(start: Offset, end: Offset, moved: Boolean, durationMs: Long): Boolean {
        if (!moved) return false
        val dx = (end.x - start.x).toDouble()
        val dy = (end.y - start.y).toDouble()
        val distance = hypot(dx, dy)
        return distance >= RECORDED_SWIPE_MIN_DISTANCE_PX || (distance >= RECORDED_SWIPE_QUICK_DISTANCE_PX && durationMs <= RECORDED_SWIPE_QUICK_DURATION_MS)
    }

    private fun setRecordCaptureTouchable(touchable: Boolean) {
        val view = recordCaptureView ?: return
        val params = recordCaptureLayoutParams ?: return
        val touchableFlag = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val hasFlag = (params.flags and touchableFlag) != 0
        val needFlag = !touchable
        if (hasFlag == needFlag) return
        params.flags = if (needFlag) {
            params.flags or touchableFlag
        } else {
            params.flags and touchableFlag.inv()
        }
        updateManagedOverlayView(view, params)
    }

    private fun shouldIgnoreRecordedInput(): Boolean {
        return SystemClock.uptimeMillis() <= ignoreRecordInputUntilMillis
    }

    private fun isRecordCaptureTemporarilyPassthrough(): Boolean {
        return SystemClock.uptimeMillis() <= ignoreRecordInputUntilMillis
    }

    private fun armRecordReplayPassThroughWindow(triggerDelayMs: Long, touchDurationMs: Long) {
        val now = SystemClock.uptimeMillis()
        val until = now +
            triggerDelayMs.coerceAtLeast(0L) +
            touchDurationMs.coerceAtLeast(1L) +
            RECORD_CAPTURE_IGNORE_EXTRA_MS
        ignoreRecordInputUntilMillis = max(ignoreRecordInputUntilMillis, until)

        setRecordCaptureTouchable(false)
        recordCapturePassthroughJob?.cancel()
        recordCapturePassthroughJob = lifecycleScope.launch {
            while (isActive) {
                val remaining = ignoreRecordInputUntilMillis - SystemClock.uptimeMillis()
                if (remaining <= 0L) {
                    break
                }
                delay(remaining)
            }
            val stillRecording = AutoClickCoordinator.recording.value.isRecording
            if (stillRecording) {
                setRecordCaptureTouchable(true)
            }
        }
    }

    private fun createLayoutParams(width: Int, height: Int): WindowManager.LayoutParams {
        val type = resolveOverlayWindowType()

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

    private fun resolveOverlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (AutoClickAccessibilityService.isConnected()) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            }
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun addManagedOverlayView(
        view: ComposeView,
        params: WindowManager.LayoutParams,
    ): Boolean {
        val useAccessibilityOverlay = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            params.type == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        if (useAccessibilityOverlay && AutoClickAccessibilityService.addOverlayView(view, params)) {
            viewHosts[view] = OverlayHost.Accessibility
            return true
        }

        if (useAccessibilityOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }

        return runCatching {
            windowManager.addView(view, params)
            viewHosts[view] = OverlayHost.SystemAlert
            true
        }.getOrElse {
            viewHosts.remove(view)
            false
        }
    }

    private fun updateManagedOverlayView(
        view: ComposeView,
        params: WindowManager.LayoutParams,
    ) {
        when (viewHosts[view]) {
            OverlayHost.Accessibility -> {
                AutoClickAccessibilityService.updateOverlayView(view, params)
            }

            OverlayHost.SystemAlert -> {
                runCatching { windowManager.updateViewLayout(view, params) }
            }

            null -> {
                if (!AutoClickAccessibilityService.updateOverlayView(view, params)) {
                    runCatching { windowManager.updateViewLayout(view, params) }
                }
            }
        }
    }

    private fun removeManagedOverlayView(view: ComposeView) {
        when (viewHosts.remove(view)) {
            OverlayHost.Accessibility -> {
                AutoClickAccessibilityService.removeOverlayView(view)
            }

            OverlayHost.SystemAlert -> {
                runCatching { windowManager.removeView(view) }
            }

            null -> {
                if (!AutoClickAccessibilityService.removeOverlayView(view)) {
                    runCatching { windowManager.removeView(view) }
                }
            }
        }
    }

    private fun createComposeView(): ComposeView {
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(viewTreeSavedStateOwner)
            setViewTreeSavedStateRegistryOwner(viewTreeSavedStateOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        }
    }

    private fun removeAllOverlays() {
        profileCollectJob?.cancel()
        profileCollectJob = null
        runtimeCollectJob?.cancel()
        runtimeCollectJob = null
        recordingCollectJob?.cancel()
        recordingCollectJob = null
        recordCapturePassthroughJob?.cancel()
        recordCapturePassthroughJob = null

        panelView?.let { removeManagedOverlayView(it) }
        panelView = null
        panelLayoutParams = null

        recordCaptureView?.let { removeManagedOverlayView(it) }
        recordCaptureView = null
        recordCaptureLayoutParams = null

        pointViews.values.forEach { group ->
            group.overlays().forEach { overlay ->
                removeManagedOverlayView(overlay.view)
            }
        }
        pointViews.clear()
        viewHosts.clear()
        ignoreRecordInputUntilMillis = 0L
        pointEditOverlayLowered = false
        AutoClickCoordinator.stopRecording()
        _overlayVisible.value = false
    }

    private fun shouldPointOverlaysBeTouchableNow(): Boolean {
        val runtimeState = AutoClickCoordinator.runtime.value.state
        val recording = AutoClickCoordinator.recording.value.isRecording
        return !recording && runtimeState != AutoClickRunState.Running && runtimeState != AutoClickRunState.Paused
    }

    private fun applyPointOverlayTouchableFlag(
        params: WindowManager.LayoutParams,
        touchable: Boolean,
    ) {
        val touchableFlag = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        params.flags = if (touchable) {
            params.flags and touchableFlag.inv()
        } else {
            params.flags or touchableFlag
        }
    }

    private fun setPointOverlaysTouchable(touchable: Boolean) {
        val touchableFlag = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        pointViews.values.forEach { group ->
            group.overlays().forEach { overlay ->
                val params = overlay.layoutParams
                val hasFlag = (params.flags and touchableFlag) != 0
                val needFlag = pointEditOverlayLowered || !touchable || overlay.alwaysNotTouchable
                if (hasFlag == needFlag) return@forEach

                params.flags = if (needFlag) {
                    params.flags or touchableFlag
                } else {
                    params.flags and touchableFlag.inv()
                }
                updateManagedOverlayView(overlay.view, params)
            }
        }
    }

    private fun lowerOverlaysForPointEditDialog() {
        if (pointEditOverlayLowered) return
        pointEditOverlayLowered = true
        setPanelOverlayTouchable(false)
        setPointOverlaysTouchable(false)
        setPanelOverlayAlpha(EDIT_DIALOG_OVERLAY_ALPHA)
        setPointOverlaysAlpha(EDIT_DIALOG_OVERLAY_ALPHA)
    }

    private fun restoreOverlaysAfterPointEditDialog() {
        if (!pointEditOverlayLowered) return
        pointEditOverlayLowered = false
        setPanelOverlayAlpha(1f)
        setPointOverlaysAlpha(1f)
        setPanelOverlayTouchable(shouldPanelOverlayBeTouchableNow())
        setPointOverlaysTouchable(shouldPointOverlaysBeTouchableNow())
    }

    private fun shouldPanelOverlayBeTouchableNow(): Boolean {
        val runtimeState = AutoClickCoordinator.runtime.value.state
        val recording = AutoClickCoordinator.recording.value.isRecording
        return !recording && runtimeState != AutoClickRunState.Running && runtimeState != AutoClickRunState.Paused
    }

    private fun setPanelOverlayTouchable(touchable: Boolean) {
        val view = panelView ?: return
        val params = panelLayoutParams ?: return
        val touchableFlag = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val hasFlag = (params.flags and touchableFlag) != 0
        val needFlag = !touchable
        if (hasFlag == needFlag) return
        params.flags = if (needFlag) {
            params.flags or touchableFlag
        } else {
            params.flags and touchableFlag.inv()
        }
        updateManagedOverlayView(view, params)
    }

    private fun setPanelOverlayAlpha(alpha: Float) {
        val view = panelView ?: return
        val params = panelLayoutParams ?: return
        val safeAlpha = alpha.coerceIn(0f, 1f)
        if (params.alpha == safeAlpha) return
        params.alpha = safeAlpha
        updateManagedOverlayView(view, params)
    }

    private fun setPointOverlaysAlpha(alpha: Float) {
        val safeAlpha = alpha.coerceIn(0f, 1f)
        pointViews.values.forEach { group ->
            group.overlays().forEach { overlay ->
                val params = overlay.layoutParams
                if (params.alpha == safeAlpha) return@forEach
                params.alpha = safeAlpha
                updateManagedOverlayView(overlay.view, params)
            }
        }
    }

    private fun persistProfileBeforeClose(showFailureToast: Boolean): Boolean {
        val result = AutoClickCoordinator.saveProfile()
        if (showFailureToast && result.isFailure) {
            Toast.makeText(
                this,
                "自动保存失败：${result.exceptionOrNull()?.message ?: "未知错误"}",
                Toast.LENGTH_SHORT
            ).show()
        }
        return result.isSuccess
    }

    companion object {
        const val ACTION_START = "com.example.littleclicker.action.START_FLOATING_WINDOW"
        const val ACTION_STOP = "com.example.littleclicker.action.STOP_FLOATING_WINDOW"
        private const val RECORDED_TAP_DURATION_MS = 50L
        private const val RECORDED_SWIPE_MIN_DURATION_MS = 40L
        private const val RECORDED_CLICK_REPLAY_DELAY_MS = 80L
        private const val RECORDED_SWIPE_REPLAY_DELAY_MS = 200L
        private const val RECORDED_SWIPE_MIN_DISTANCE_PX = 12.0
        private const val RECORDED_SWIPE_QUICK_DISTANCE_PX = 8.0
        private const val RECORDED_SWIPE_QUICK_DURATION_MS = 120L
        private const val RECORD_CAPTURE_IGNORE_EXTRA_MS = 180L
        private const val EDIT_DIALOG_OVERLAY_ALPHA = 0f

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
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
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
    val alwaysNotTouchable: Boolean = false,
)

private enum class OverlayHost {
    Accessibility,
    SystemAlert,
}

private sealed interface PointOverlayGroup {
    fun overlays(): List<PointOverlay>
}

private data class ClickPointOverlayGroup(
    val bubble: PointOverlay,
) : PointOverlayGroup {
    override fun overlays(): List<PointOverlay> = listOf(bubble)
}

private data class SwipePointOverlayGroup(
    val startBubble: PointOverlay,
    val endBubble: PointOverlay,
    val line: PointOverlay,
) : PointOverlayGroup {
    override fun overlays(): List<PointOverlay> = listOf(line, startBubble, endBubble)
}

private data class SwipeLineSpec(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val startOffset: Offset,
    val endOffset: Offset,
)

@Composable
private fun FloatingPanel(
    runState: AutoClickRunState,
    runMessage: String?,
    isRecording: Boolean,
    onSizeChanged: (IntSize) -> Unit,
    onDrag: (IntOffset) -> Unit,
    onToggleRun: () -> Unit,
    onToggleRecord: () -> Unit,
    onAddAction: () -> Unit,
    onDeleteLatest: () -> Unit,
    onEditPoint: (AutoClickPoint) -> Unit,
    onDeletePoint: (AutoClickPoint) -> Unit,
    onClosePanel: () -> Unit,
    isDarkTheme: Boolean,
    scaleFactor: Float,
) {
    val panelContainerColor = if (isDarkTheme) Color(0xE62A2D34) else Color(0xEFFFFAF2)
    val panelHandleColor = if (isDarkTheme) Color(0xFF3A404D) else Color(0xFFE0E0E0)
    val panelCorner = scaledDp(16.dp, scaleFactor)
    val panelPadding = scaledDp(10.dp, scaleFactor)
    val panelSpacing = scaledDp(8.dp, scaleFactor)
    val sideButtonSpacing = scaledDp(8.dp, scaleFactor)
    val handleHeight = scaledDp(16.dp, scaleFactor)
    val handleCorner = scaledDp(8.dp, scaleFactor)
    val panelWidth = scaledDp(68.dp, scaleFactor)

    Card(
        modifier = Modifier
            .width(panelWidth)
            .onSizeChanged(onSizeChanged),
        shape = RoundedCornerShape(panelCorner),
        colors = CardDefaults.cardColors(containerColor = panelContainerColor)
    ) {
        Column(
            modifier = Modifier.padding(panelPadding),
            verticalArrangement = Arrangement.spacedBy(panelSpacing)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(handleHeight)
                    .background(panelHandleColor, RoundedCornerShape(handleCorner))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(IntOffset(dragAmount.x.roundToInt(), dragAmount.y.roundToInt()))
                        }
                    }
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(sideButtonSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PanelActionButton(
                    icon = if (runState == AutoClickRunState.Running || runState == AutoClickRunState.Paused) {
                        Icons.Filled.Stop
                    } else {
                        Icons.Filled.PlayArrow
                    },
                    contentDescription = if (runState == AutoClickRunState.Running || runState == AutoClickRunState.Paused) {
                        "运行中，点击停止"
                    } else {
                        "运行"
                    },
                    onClick = onToggleRun,
                    triggerOnPressDown = runState == AutoClickRunState.Running || runState == AutoClickRunState.Paused,
                    isDarkTheme = isDarkTheme,
                    scaleFactor = scaleFactor
                )
                PanelActionButton(
                    label = if (isRecording) "停" else "录",
                    contentDescription = if (isRecording) "停止录制" else "录制",
                    onClick = onToggleRecord,
                    isDarkTheme = isDarkTheme,
                    scaleFactor = scaleFactor
                )
                PanelActionButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "添加动作",
                    onClick = onAddAction,
                    isDarkTheme = isDarkTheme,
                    scaleFactor = scaleFactor
                )
                PanelActionButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "删除最新动作",
                    onClick = onDeleteLatest,
                    isDarkTheme = isDarkTheme,
                    scaleFactor = scaleFactor
                )
                PanelActionButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "关闭悬浮窗",
                    onClick = onClosePanel,
                    isDarkTheme = isDarkTheme,
                    scaleFactor = scaleFactor
                )
            }
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
    isDarkTheme: Boolean,
    scaleFactor: Float,
    showRemove: Boolean = false,
) {
    val bubbleFillColor = if (isDarkTheme) Color(0xCC4D84FF) else Color(0xCC1976D2)
    val bubbleBorderColor = if (isDarkTheme) Color(0xFFDDE7FF) else Color.White
    val removeFillColor = if (isDarkTheme) Color(0xCCE35D5B) else Color(0xCCB00020)
    val containerSize = scaledDp(76.dp, scaleFactor)
    val centerSize = scaledDp(68.dp, scaleFactor)
    val removeSize = scaledDp(20.dp, scaleFactor)
    val centerBorder = scaledDp(2.dp, scaleFactor).coerceAtLeast(1.dp)
    val removeBorder = scaledDp(1.dp, scaleFactor).coerceAtLeast(1.dp)

    Box(
        modifier = Modifier
            .size(containerSize)
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
                .size(centerSize)
                .background(bubbleFillColor, CircleShape)
                .border(centerBorder, bubbleBorderColor, CircleShape)
                .padding(horizontal = scaledDp(1.dp, scaleFactor)),
            contentAlignment = Alignment.Center
        ) {
            AutoResizeSingleLineText(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 42.sp),
                minFontSize = 5.sp,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Clip
            )
        }
        if (showRemove) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(removeSize)
                    .background(removeFillColor, CircleShape)
                    .border(removeBorder, bubbleBorderColor, CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Text("x", color = Color.White)
            }
        }
    }
}

@Composable
private fun ActionItemRow(
    label: String,
    actionType: AutoClickActionType,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isDarkTheme: Boolean,
    scaleFactor: Float,
) {
    val rowBgColor = if (isDarkTheme) Color(0xFF242B37) else Color(0xFFF7FAFC)
    val rowTextColor = if (isDarkTheme) Color(0xFFE6EDF8) else Color(0xFF263238)
    val iconTintColor = if (isDarkTheme) Color(0xFF9FB2CC) else Color(0xFF455A64)
    val buttonSize = scaledDp(28.dp, scaleFactor).coerceAtLeast(14.dp)
    val iconSize = scaledDp(16.dp, scaleFactor).coerceAtLeast(10.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBgColor, RoundedCornerShape(scaledDp(10.dp, scaleFactor)))
            .padding(horizontal = scaledDp(8.dp, scaleFactor), vertical = scaledDp(6.dp, scaleFactor)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(scaledDp(6.dp, scaleFactor))
        ) {
            Icon(
                imageVector = if (actionType == AutoClickActionType.Click) {
                    Icons.Filled.AddCircleOutline
                } else {
                    Icons.Filled.PlayArrow
                },
                contentDescription = null,
                tint = iconTintColor,
                modifier = Modifier.size(iconSize)
            )
            AutoResizeSingleLineText(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = rowTextColor
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(scaledDp(2.dp, scaleFactor))) {
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "编辑",
                    tint = if (isDarkTheme) Color(0xFF73B7FF) else Color(0xFF1976D2),
                    modifier = Modifier.size(iconSize)
                )
            }
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = if (isDarkTheme) Color(0xFFFF8A80) else Color(0xFFB00020),
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
private fun PanelActionButton(
    icon: ImageVector? = null,
    label: String? = null,
    contentDescription: String,
    onClick: () -> Unit,
    triggerOnPressDown: Boolean = false,
    isDarkTheme: Boolean,
    scaleFactor: Float,
) {
    val backgroundColor = if (isDarkTheme) Color(0xFF2D3542) else Color(0xFFF5F5F5)
    val borderColor = if (isDarkTheme) Color(0xFF4A5568) else Color(0xFFE0E0E0)
    val contentColor = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF37474F)
    val buttonSize = scaledDp(42.dp, scaleFactor)
    val iconSize = scaledDp(22.dp, scaleFactor).coerceAtLeast(12.dp)

    Box(
        modifier = Modifier
            .size(buttonSize)
            .background(backgroundColor, CircleShape)
            .border(1.dp, borderColor, CircleShape)
            .then(
                if (triggerOnPressDown) {
                    Modifier.pointerInput(onClick) {
                        detectTapGestures(
                            onPress = {
                                onClick()
                                tryAwaitRelease()
                            }
                        )
                    }
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (label != null) {
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Icon(
                imageVector = icon ?: Icons.Filled.PlayArrow,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun AutoResizeSingleLineText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
    style: TextStyle,
    minFontSize: TextUnit = 9.sp,
    textAlign: TextAlign = TextAlign.Start,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val baseFontSize = if (style.fontSize != TextUnit.Unspecified) style.fontSize else 14.sp
    val currentFontSize = remember(text, baseFontSize, minFontSize) { mutableStateOf(baseFontSize) }
    val readyToDraw = remember(text, baseFontSize, minFontSize) { mutableStateOf(false) }

    Text(
        text = text,
        modifier = modifier.drawWithContent {
            if (readyToDraw.value) {
                drawContent()
            }
        },
        color = color,
        style = style.copy(fontSize = currentFontSize.value),
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        overflow = overflow,
        onTextLayout = { textLayoutResult ->
            if (readyToDraw.value) {
                return@Text
            }
            if (textLayoutResult.hasVisualOverflow && currentFontSize.value > minFontSize) {
                val nextSize = (currentFontSize.value.value - 0.25f).coerceAtLeast(minFontSize.value)
                currentFontSize.value = nextSize.sp
                return@Text
            }
            readyToDraw.value = true
        }
    )
}

private fun scaledDp(size: Dp, scaleFactor: Float): Dp {
    return (size.value * scaleFactor).dp
}
