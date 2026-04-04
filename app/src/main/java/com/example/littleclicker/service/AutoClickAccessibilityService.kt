package com.example.littleclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
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

    private suspend fun executeProfile(profile: AutoClickProfile) {
        if (profile.points.isEmpty()) {
            throw IllegalStateException("没有可执行的点击步骤")
        }

        ensureNotCancelled()
        waitIfPaused()
        delay(START_TRIGGER_DELAY_MS)
        ensureNotCancelled()
        waitIfPaused()

        val safeCycles = profile.cycleCount.coerceAtLeast(1)
        when (profile.runMode) {
            AutoClickRunMode.RunOnce -> {
                repeat(safeCycles) {
                    executePointSequence(profile.points)
                }
            }
            AutoClickRunMode.LoopUntilStopped -> {
                while (currentCoroutineContext().isActive) {
                    repeat(safeCycles) {
                        executePointSequence(profile.points)
                    }
                }
            }
        }
    }

    private suspend fun executePointSequence(points: List<AutoClickPoint>) {
        for (point in points) {
            val safeRepeatCount = point.repeatCount.coerceAtLeast(1)
            repeat(safeRepeatCount) {
                ensureNotCancelled()
                waitIfPaused()

                val delayMs = point.delayMs.coerceAtLeast(0L)
                if (delayMs > 0L) {
                    delay(delayMs)
                }

                ensureNotCancelled()
                waitIfPaused()

                val dispatchResult = dispatchPoint(point)
                when (dispatchResult) {
                    GestureDispatchResult.Completed -> Unit
                    GestureDispatchResult.Cancelled -> {
                        if (stopRequested || !currentCoroutineContext().isActive) {
                            throw CancellationException("Gesture cancelled by stop request")
                        }
                        // 用户触摸导致手势被系统取消时，仅跳过当前动作，不终止整轮任务。
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

    private suspend fun dispatchPoint(point: AutoClickPoint): GestureDispatchResult {
        return when (point.actionType) {
            AutoClickActionType.Click -> {
                val tapX = AutoClickCoordinator.toScreenCoordinateX(point.x)
                val tapY = AutoClickCoordinator.toScreenCoordinateY(point.y)
                dispatchSingleTap(
                    x = tapX,
                    y = tapY,
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

    private fun replayRecordedAction(point: AutoClickPoint, triggerDelayMs: Long): Boolean {
        val canReplay = synchronized(lock) { runnerJob?.isActive != true }
        if (!canReplay) return false
        serviceScope.launch {
            delay(triggerDelayMs.coerceAtLeast(0L))
            dispatchPoint(point)
        }
        return true
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
        ): Boolean {
            return instance?.replayRecordedAction(point, triggerDelayMs) ?: false
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
