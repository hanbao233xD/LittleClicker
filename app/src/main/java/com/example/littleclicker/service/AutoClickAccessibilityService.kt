package com.example.littleclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
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

    private var runnerJob: Job? = null
    @Volatile
    private var paused = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event handling is required for the auto-click executor.
    }

    override fun onInterrupt() {
        stopExecution()
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

                val dispatched = when (point.actionType) {
                    AutoClickActionType.Click -> {
                        dispatchSingleTap(
                            x = point.x,
                            y = point.y,
                            durationMs = point.touchDurationMs.coerceAtLeast(1L)
                        )
                    }

                    AutoClickActionType.Swipe -> {
                        val endX = point.endX ?: (point.x + 200)
                        val endY = point.endY ?: point.y
                        dispatchSwipe(
                            startX = point.x,
                            startY = point.y,
                            endX = endX,
                            endY = endY,
                            durationMs = point.touchDurationMs.coerceAtLeast(1L)
                        )
                    }
                }
                if (!dispatched) {
                    throw IllegalStateException("动作手势派发失败")
                }

                ensureNotCancelled()
                waitIfPaused()
                val delayMs = point.delayMs.coerceAtLeast(0L)
                if (delayMs > 0) {
                    delay(delayMs)
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
    ): Boolean {
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
                            continuation.resume(true)
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                },
                null
            )

            if (!started && continuation.isActive) {
                continuation.resume(false)
            }
        }
    }

    private suspend fun dispatchSwipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long,
    ): Boolean {
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
                            continuation.resume(true)
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                },
                null
            )

            if (!started && continuation.isActive) {
                continuation.resume(false)
            }
        }
    }

    companion object {
        @Volatile
        private var instance: AutoClickAccessibilityService? = null

        fun start(profile: AutoClickProfile): Boolean = instance?.startExecution(profile) ?: false

        fun pause(): Boolean = instance?.pauseExecution() ?: false

        fun resume(): Boolean = instance?.resumeExecution() ?: false

        fun stop(): Boolean = instance?.stopExecution() ?: false
    }
}
