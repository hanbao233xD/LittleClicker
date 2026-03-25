package com.example.littleclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
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
    @Volatile
    private var paused = false
    @Volatile
    private var stopRequested = false

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
        if (instance === this) {
            instance = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startExecution(profile: AutoClickProfile): Boolean {
        synchronized(lock) {
            if (runnerJob?.isActive == true) {
                return false
            }
            paused = false
            stopRequested = false
            runnerJob = serviceScope.launch {
                try {
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
        val job = synchronized(lock) {
            val activeJob = runnerJob
            if (activeJob == null) {
                return false
            }
            runnerJob = null
            paused = false
            stopRequested = true
            activeJob
        }
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

                val dispatchResult = when (point.actionType) {
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
                }
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

    companion object {
        private const val START_TRIGGER_DELAY_MS = 100L

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
