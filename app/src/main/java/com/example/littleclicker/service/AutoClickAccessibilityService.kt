package com.example.littleclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast
import com.example.littleclicker.R
import com.example.littleclicker.autoclick.AutoClickActionType
import com.example.littleclicker.autoclick.AutoClickCoordinator
import com.example.littleclicker.autoclick.AutoClickPoint
import com.example.littleclicker.autoclick.AutoClickProfile
import com.example.littleclicker.autoclick.AutoClickRunMode
import com.example.littleclicker.autoclick.AutoClickRunState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.random.Random

class AutoClickAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Any()
    private val overlayWindowManager: WindowManager by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }

    private var runnerJob: Job? = null
    private var runtimeHintView: TextView? = null
    @Volatile
    private var paused = false
    @Volatile
    private var stopRequested = false
    @Volatile
    private var executionToken: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo?.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event handling is required for the auto-click executor.
    }

    override fun onInterrupt() {
        stopExecution()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.onKeyEvent(event)
        }
        val executionActive = synchronized(lock) { runnerJob?.isActive == true }
        if (!executionActive) {
            return super.onKeyEvent(event)
        }
        stopExecution()
        AutoClickCoordinator.reportExecutionState(
            state = AutoClickRunState.Idle,
            message = "检测到音量下键，已强制停止"
        )
        Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show()
        return true
    }

    override fun onDestroy() {
        stopExecution()
        hideRuntimeHintOverlay(force = true)
        if (instance === this) {
            instance = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startExecution(profile: AutoClickProfile): Boolean {
        val currentToken: Long
        synchronized(lock) {
            if (runnerJob?.isActive == true) {
                return false
            }
            paused = false
            stopRequested = false
            executionToken += 1L
            currentToken = executionToken
            runnerJob = serviceScope.launch {
                try {
                    showRuntimeHintOverlay(currentToken)
                    AutoClickCoordinator.reportExecutionState(
                        state = AutoClickRunState.Running,
                        message = "自动点击执行中"
                    )
                    executeProfile(profile)
                    AutoClickCoordinator.reportExecutionState(
                        state = AutoClickRunState.Completed,
                        message = "自动点击执行完成"
                    )
                } catch (cancel: CancellationException) {
                    AutoClickCoordinator.reportExecutionState(
                        state = AutoClickRunState.Idle,
                        message = "自动点击已停止"
                    )
                    throw cancel
                } catch (error: Throwable) {
                    AutoClickCoordinator.reportExecutionState(
                        state = AutoClickRunState.Failed,
                        message = error.message ?: "执行失败"
                    )
                } finally {
                    hideRuntimeHintOverlay(token = currentToken)
                    synchronized(lock) {
                        runnerJob = null
                        paused = false
                        stopRequested = false
                    }
                }
            }
            return true
        }
    }

    private fun pauseExecution(): Boolean {
        synchronized(lock) {
            val active = runnerJob?.isActive == true
            if (!active || paused) return false
            paused = true
            AutoClickCoordinator.reportExecutionState(
                state = AutoClickRunState.Paused,
                message = "自动点击已暂停"
            )
            return true
        }
    }

    private fun resumeExecution(): Boolean {
        synchronized(lock) {
            val active = runnerJob?.isActive == true
            if (!active || !paused) return false
            paused = false
            AutoClickCoordinator.reportExecutionState(
                state = AutoClickRunState.Running,
                message = "自动点击继续执行"
            )
            return true
        }
    }

    private fun stopExecution(): Boolean {
        val currentToken: Long
        val job = synchronized(lock) {
            val activeJob = runnerJob
            if (activeJob == null) {
                return false
            }
            runnerJob = null
            paused = false
            stopRequested = true
            currentToken = executionToken
            activeJob
        }
        hideRuntimeHintOverlay(token = currentToken)
        job.cancel()
        AutoClickCoordinator.reportExecutionState(
            state = AutoClickRunState.Idle,
            message = "自动点击已停止"
        )
        return true
    }

    private fun hasActiveRunner(): Boolean {
        return synchronized(lock) { runnerJob?.isActive == true }
    }

    private suspend fun executeProfile(profile: AutoClickProfile) {
        if (profile.points.isEmpty()) {
            throw IllegalStateException("没有可执行的点击步骤")
        }

        ensureNotCancelled()
        waitIfPaused()
        delay(START_TRIGGER_DELAY_MS)
        ensureNotCancelled()
        waitIfPaused()

        when (profile.runMode) {
            AutoClickRunMode.RunOnce -> {
                executePointSequence(
                    points = profile.points,
                    clickRandomOffsetPx = profile.clickRandomOffsetPx,
                    randomDelayMs = profile.randomDelayMs
                )
            }
            AutoClickRunMode.LoopUntilStopped -> {
                while (currentCoroutineContext().isActive) {
                    executePointSequence(
                        points = profile.points,
                        clickRandomOffsetPx = profile.clickRandomOffsetPx,
                        randomDelayMs = profile.randomDelayMs
                    )
                    ensureNotCancelled()
                    waitIfPaused()
                    val loopDelayMs = profile.loopIntervalDelayMs.coerceAtLeast(0L)
                    if (loopDelayMs > 0L) {
                        delay(loopDelayMs)
                    }
                }
            }
        }
    }

    private suspend fun executePointSequence(
        points: List<AutoClickPoint>,
        clickRandomOffsetPx: Int,
        randomDelayMs: Long,
    ) {
        for (point in points) {
            val safeRepeatCount = point.repeatCount.coerceAtLeast(1)
            repeat(safeRepeatCount) {
                ensureNotCancelled()
                waitIfPaused()

                val baseDelayMs = point.delayMs.coerceAtLeast(0L)
                val actualRandomDelay = if (randomDelayMs > 0L) {
                    Random.nextLong(-randomDelayMs, randomDelayMs + 1)
                } else {
                    0L
                }
                val delayMs = (baseDelayMs + actualRandomDelay).coerceAtLeast(0L)
                if (delayMs > 0L) {
                    delay(delayMs)
                }

                ensureNotCancelled()
                waitIfPaused()

                if (point.actionType == AutoClickActionType.TextClick) {
                    executeTextClickWithRetry(point, clickRandomOffsetPx)
                    return@repeat
                }

                val dispatchResult = dispatchPoint(
                    point = point,
                    clickRandomOffsetPx = clickRandomOffsetPx
                )
                when (dispatchResult) {
                    GestureDispatchResult.Completed -> Unit
                    GestureDispatchResult.Cancelled -> {
                        if (stopRequested || !currentCoroutineContext().isActive) {
                            throw CancellationException("Gesture cancelled by stop request")
                        }
                        return@repeat
                    }
                    GestureDispatchResult.FailedToStart -> {
                        if (stopRequested || !currentCoroutineContext().isActive) {
                            throw CancellationException("Gesture cancelled by stop request")
                        }
                        throw IllegalStateException("动作手势派发失败")
                    }
                }
            }
        }
    }

    private suspend fun executeTextClickWithRetry(
        point: AutoClickPoint,
        clickRandomOffsetPx: Int,
    ) {
        val maxRetries = if (point.continuousRetry) Int.MAX_VALUE else point.textFindRetryCount.coerceAtLeast(1)
        val retryDelay = point.textFindRetryDelayMs.coerceAtLeast(0L)

        repeat(maxRetries) { attempt ->
            ensureNotCancelled()
            waitIfPaused()

            val dispatchResult = dispatchPoint(point, clickRandomOffsetPx)
            when (dispatchResult) {
                GestureDispatchResult.Completed -> return
                GestureDispatchResult.Cancelled -> {
                    if (stopRequested || !currentCoroutineContext().isActive) {
                        throw CancellationException("Gesture cancelled by stop request")
                    }
                    return
                }
                GestureDispatchResult.FailedToStart -> {
                    if (!point.continuousRetry && attempt >= maxRetries - 1) return
                    if (retryDelay > 0L) {
                        delay(retryDelay)
                    }
                }
            }
        }
    }

    private suspend fun dispatchPoint(
        point: AutoClickPoint,
        clickRandomOffsetPx: Int = 0,
    ): GestureDispatchResult {
        return when (point.actionType) {
            AutoClickActionType.Click -> {
                val tapX = AutoClickCoordinator.toScreenCoordinateX(point.x)
                val tapY = AutoClickCoordinator.toScreenCoordinateY(point.y)
                val offsetTap = applyRandomClickOffset(
                    x = tapX,
                    y = tapY,
                    randomOffsetPx = clickRandomOffsetPx
                )
                dispatchSingleTap(
                    x = offsetTap.first,
                    y = offsetTap.second,
                    durationMs = point.touchDurationMs.coerceAtLeast(1L)
                )
            }

            AutoClickActionType.Swipe -> {
                val startX = AutoClickCoordinator.toScreenCoordinateX(point.x)
                val startY = AutoClickCoordinator.toScreenCoordinateY(point.y)
                val endX = AutoClickCoordinator.toScreenCoordinateX(point.endX ?: (point.x + 200))
                val endY = AutoClickCoordinator.toScreenCoordinateY(point.endY ?: point.y)
                dispatchSwipe(
                    startX = startX,
                    startY = startY,
                    endX = endX,
                    endY = endY,
                    durationMs = point.touchDurationMs.coerceAtLeast(1L)
                )
            }

            AutoClickActionType.Home -> dispatchGlobalAction(GLOBAL_ACTION_HOME)
            AutoClickActionType.Back -> dispatchGlobalAction(GLOBAL_ACTION_BACK)
            AutoClickActionType.Recents -> dispatchGlobalAction(GLOBAL_ACTION_RECENTS)
            AutoClickActionType.TextClick -> {
                val center = findNodeCenterByText(point.targetText)
                if (center != null) {
                    val offsetTap = applyRandomClickOffset(
                        x = center.first,
                        y = center.second,
                        randomOffsetPx = clickRandomOffsetPx
                    )
                    dispatchSingleTap(
                        x = offsetTap.first,
                        y = offsetTap.second,
                        durationMs = point.touchDurationMs.coerceAtLeast(1L)
                    )
                } else {
                    GestureDispatchResult.FailedToStart
                }
            }
        }
    }

    private fun dispatchGlobalAction(globalAction: Int): GestureDispatchResult {
        val started = performGlobalAction(globalAction)
        return if (started) {
            GestureDispatchResult.Completed
        } else {
            GestureDispatchResult.FailedToStart
        }
    }

    private fun findNodeCenterByText(targetText: String): Pair<Int, Int>? {
        if (targetText.isBlank()) return null
        val root = rootInActiveWindow ?: return null
        try {
            val systemNodes = try {
                root.findAccessibilityNodeInfosByText(targetText)
            } catch (_: Exception) {
                null
            }
            val candidates = mutableListOf<Triple<Int, Int, Boolean>>()
            if (!systemNodes.isNullOrEmpty()) {
                for (node in systemNodes) {
                    try {
                        val rect = android.graphics.Rect()
                        node.getBoundsInScreen(rect)
                        if (rect.width() > 0 && rect.height() > 0) {
                            val clickable = node.isClickable || findClickableAncestorCenter(node) != null
                            candidates.add(Triple(rect.centerX(), rect.centerY(), clickable))
                        }
                    } catch (_: Exception) {
                        continue
                    }
                }
            }
            if (candidates.isEmpty()) {
                collectNodeCentersByText(root, targetText, candidates)
            }
            if (candidates.isEmpty()) return null
            val best = candidates.firstOrNull { it.third } ?: candidates.first()
            return best.first to best.second
        } finally {
            try { root.recycle() } catch (_: Exception) {}
        }
    }

    private fun findClickableAncestorCenter(node: AccessibilityNodeInfo): Pair<Int, Int>? {
        var current = try { node.parent } catch (_: Exception) { null }
        var depth = 0
        while (current != null && depth < 20) {
            try {
                if (current.isClickable) {
                    val rect = android.graphics.Rect()
                    current.getBoundsInScreen(rect)
                    if (rect.width() > 0 && rect.height() > 0) {
                        return rect.centerX() to rect.centerY()
                    }
                }
            } catch (_: Exception) {
                break
            }
            val next = try { current.parent } catch (_: Exception) { null }
            current = next
            depth++
        }
        return null
    }

    private fun collectNodeCentersByText(
        node: AccessibilityNodeInfo,
        targetText: String,
        result: MutableList<Triple<Int, Int, Boolean>>,
    ) {
        try {
            val nodeText = try { node.text?.toString().orEmpty() } catch (_: Exception) { "" }
            val contentDesc = try { node.contentDescription?.toString().orEmpty() } catch (_: Exception) { "" }
            val hintText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try { node.hintText?.toString().orEmpty() } catch (_: Exception) { "" }
            } else ""
            val stateDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try { node.stateDescription?.toString().orEmpty() } catch (_: Exception) { "" }
            } else ""

            val allTexts = listOf(nodeText, contentDesc, hintText, stateDesc)
            if (allTexts.any { it.contains(targetText, ignoreCase = true) }) {
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    val clickable = try { node.isClickable } catch (_: Exception) { false }
                    result.add(Triple(rect.centerX(), rect.centerY(), clickable))
                }
            }

            val childCount = try { node.childCount } catch (_: Exception) { 0 }
            for (i in 0 until childCount) {
                val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
                collectNodeCentersByText(child, targetText, result)
            }
        } catch (_: Exception) {
        }
    }

    private fun replayRecordedAction(
        point: AutoClickPoint,
        triggerDelayMs: Long,
        allowWhenRecording: Boolean,
    ): Boolean {
        if (!allowWhenRecording && AutoClickCoordinator.recording.value.isRecording) {
            return false
        }
        val canReplay = synchronized(lock) { runnerJob?.isActive != true }
        if (!canReplay) return false
        serviceScope.launch {
            delay(triggerDelayMs.coerceAtLeast(0L))
            val randomOffsetPx = AutoClickCoordinator.profile.value.clickRandomOffsetPx
            dispatchPoint(point = point, clickRandomOffsetPx = randomOffsetPx)
        }
        return true
    }

    private fun applyRandomClickOffset(
        x: Int,
        y: Int,
        randomOffsetPx: Int,
    ): Pair<Int, Int> {
        val safeOffset = randomOffsetPx.coerceAtLeast(0)
        if (safeOffset <= 0) return x to y

        val dx = Random.nextInt(from = -safeOffset, until = safeOffset + 1)
        val dy = Random.nextInt(from = -safeOffset, until = safeOffset + 1)
        val width = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val height = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val boundedX = (x + dx).coerceIn(0, width - 1)
        val boundedY = (y + dy).coerceIn(0, height - 1)
        return boundedX to boundedY
    }

    private suspend fun waitIfPaused() {
        while (paused && currentCoroutineContext().isActive) {
            delay(60L)
        }
    }

    private suspend fun ensureNotCancelled() {
        if (!currentCoroutineContext().isActive) {
            throw CancellationException("Task cancelled")
        }
    }

    private suspend fun dispatchSingleTap(
        x: Int,
        y: Int,
        durationMs: Long,
    ): GestureDispatchResult {
        return suspendCancellableCoroutine { continuation ->
            val path = Path().apply {
                moveTo(x.toFloat(), y.toFloat())
            }

            val stroke = GestureDescription.StrokeDescription(
                path,
                0L,
                durationMs.coerceAtLeast(1L)
            )
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val started = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) {
                            continuation.resume(GestureDispatchResult.Completed)
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) {
                            continuation.resume(GestureDispatchResult.Cancelled)
                        }
                    }
                },
                null
            )

            if (!started && continuation.isActive) {
                continuation.resume(GestureDispatchResult.FailedToStart)
            }
        }
    }

    private suspend fun dispatchSwipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long,
    ): GestureDispatchResult {
        return suspendCancellableCoroutine { continuation ->
            val path = Path().apply {
                moveTo(startX.toFloat(), startY.toFloat())
                lineTo(endX.toFloat(), endY.toFloat())
            }
            val stroke = GestureDescription.StrokeDescription(
                path,
                0L,
                durationMs.coerceAtLeast(1L)
            )
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val started = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) {
                            continuation.resume(GestureDispatchResult.Completed)
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) {
                            continuation.resume(GestureDispatchResult.Cancelled)
                        }
                    }
                },
                null
            )

            if (!started && continuation.isActive) {
                continuation.resume(GestureDispatchResult.FailedToStart)
            }
        }
    }

    private fun addOverlayViewInternal(view: View, params: WindowManager.LayoutParams): Boolean {
        return runCatching {
            if (view.parent != null) return@runCatching true
            overlayWindowManager.addView(view, params)
            true
        }.getOrElse { false }
    }

    private fun updateOverlayViewInternal(view: View, params: WindowManager.LayoutParams): Boolean {
        return runCatching {
            overlayWindowManager.updateViewLayout(view, params)
            true
        }.getOrElse { false }
    }

    private fun removeOverlayViewInternal(view: View): Boolean {
        return runCatching {
            overlayWindowManager.removeView(view)
            true
        }.getOrElse { false }
    }

    private fun showRuntimeHintOverlay(token: Long) {
        if (!isTokenCurrent(token)) return
        if (runtimeHintView != null) return

        val density = resources.displayMetrics.density
        val hintView = TextView(this).apply {
            text = getString(R.string.autoclick_force_stop_hint)
            textSize = 12f
            setTextColor(Color.argb(170, 255, 0, 0))
            setBackgroundColor(Color.TRANSPARENT)
            gravity = Gravity.CENTER
            setPadding(
                (8 * density).toInt(),
                (2 * density).toInt(),
                (8 * density).toInt(),
                (2 * density).toInt()
            )
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            y = (20 * density).toInt()
        }

        if (!isTokenCurrent(token)) return
        val added = addOverlayViewInternal(hintView, params)
        if (added) {
            runtimeHintView = hintView
        }
    }

    private fun hideRuntimeHintOverlay(token: Long? = null, force: Boolean = false) {
        if (!force && token != null && !isTokenCurrent(token)) return
        val view = runtimeHintView ?: return
        removeOverlayViewInternal(view)
        runtimeHintView = null
    }

    private fun isTokenCurrent(token: Long): Boolean {
        return synchronized(lock) { executionToken == token }
    }

    companion object {
        private const val START_TRIGGER_DELAY_MS = 100L
        private const val RECORD_REPLAY_TRIGGER_DELAY_MS = 80L

        @Volatile
        private var instance: AutoClickAccessibilityService? = null

        fun isConnected(): Boolean = instance != null

        fun isExecuting(): Boolean = instance?.hasActiveRunner() == true

        fun addOverlayView(view: View, params: WindowManager.LayoutParams): Boolean {
            return instance?.addOverlayViewInternal(view, params) ?: false
        }

        fun updateOverlayView(view: View, params: WindowManager.LayoutParams): Boolean {
            return instance?.updateOverlayViewInternal(view, params) ?: false
        }

        fun removeOverlayView(view: View): Boolean {
            return instance?.removeOverlayViewInternal(view) ?: false
        }

        fun replayRecordedAction(
            point: AutoClickPoint,
            triggerDelayMs: Long = RECORD_REPLAY_TRIGGER_DELAY_MS,
            allowWhenRecording: Boolean = false,
        ): Boolean {
            return instance?.replayRecordedAction(point, triggerDelayMs, allowWhenRecording) ?: false
        }

        fun start(profile: AutoClickProfile): Boolean = instance?.startExecution(profile) ?: false

        fun pause(): Boolean = instance?.pauseExecution() ?: false

        fun resume(): Boolean = instance?.resumeExecution() ?: false

        fun stop(): Boolean = instance?.stopExecution() ?: false
    }
}

private enum class GestureDispatchResult {
    Completed,
    Cancelled,
    FailedToStart,
}
