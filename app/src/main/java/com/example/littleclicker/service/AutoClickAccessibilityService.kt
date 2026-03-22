package com.example.littleclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.example.littleclicker.autoclick.AutoClickCoordinator
import com.example.littleclicker.autoclick.AutoClickProfile
import com.example.littleclicker.autoclick.AutoClickRunState
import com.example.littleclicker.autoclick.expandExecutionSteps
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
        val steps = profile.expandExecutionSteps()
        if (steps.isEmpty()) {
            throw IllegalStateException("没有可执行的点击步骤")
        }

        for (step in steps) {
            ensureNotCancelled()
            waitIfPaused()
            if (step.delayMs > 0) {
                delay(step.delayMs)
            }
            ensureNotCancelled()
            waitIfPaused()

            val dispatched = dispatchSingleTap(
                x = step.x,
                y = step.y,
                durationMs = step.touchDurationMs
            )
            if (!dispatched) {
                throw IllegalStateException("点击手势派发失败")
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

    companion object {
        @Volatile
        private var instance: AutoClickAccessibilityService? = null

        fun start(profile: AutoClickProfile): Boolean = instance?.startExecution(profile) ?: false

        fun pause(): Boolean = instance?.pauseExecution() ?: false

        fun resume(): Boolean = instance?.resumeExecution() ?: false

        fun stop(): Boolean = instance?.stopExecution() ?: false
    }
}
