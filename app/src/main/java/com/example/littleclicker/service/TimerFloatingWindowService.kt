package com.example.littleclicker.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.littleclicker.autoclick.AutoClickCoordinator
import com.example.littleclicker.ui.formatHmsWithTenths
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class TimerFloatingWindowService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var overlaySize: IntSize = IntSize.Zero
    private var overlayOffset: IntOffset = IntOffset(64, 64)
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
        return START_STICKY
    }

    override fun onDestroy() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        overlayLayoutParams = null
        if (::viewTreeSavedStateOwner.isInitialized) {
            viewTreeSavedStateOwner.markDestroyed()
        }
        _overlayVisible.value = false
        super.onDestroy()
    }

    private fun showOverlayIfNeeded() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            _overlayVisible.value = false
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = createComposeView().apply {
            setContent {
                MiuixTheme(colors = lightColorScheme()) {
                    val profile by AutoClickCoordinator.profile.collectAsState()
                    val runtime by AutoClickCoordinator.runtime.collectAsState()
                    val nowMillis by produceState(initialValue = AutoClickCoordinator.currentAlignedNowMillis()) {
                        while (true) {
                            value = AutoClickCoordinator.currentAlignedNowMillis()
                            delay(100L)
                        }
                    }
                    val line = buildString {
                        append(formatHmsWithTenths(nowMillis))
                        append("  设定时间:")
                        append(profile.scheduleRuleHms ?: "--:--:--")
                        append("  运行状态:")
                        append(runtime.state)
                    }

                    Box(
                        modifier = Modifier
                            .onSizeChanged { size ->
                                overlaySize = size
                                updateOverlayOffset(overlayOffset)
                            }
                            .widthIn(min = 320.dp, max = 680.dp)
                            .background(Color(0xA6434343), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0x80BDBDBD), RoundedCornerShape(12.dp))
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val desired = IntOffset(
                                        x = overlayOffset.x + dragAmount.x.roundToInt(),
                                        y = overlayOffset.y + dragAmount.y.roundToInt()
                                    )
                                    updateOverlayOffset(desired)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = line,
                            color = Color.White
                        )
                    }
                }
            }
        }

        val params = createLayoutParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            x = overlayOffset.x
            y = overlayOffset.y
        }
        windowManager.addView(view, params)
        overlayView = view
        overlayLayoutParams = params
        _overlayVisible.value = true
    }

    private fun updateOverlayOffset(desired: IntOffset) {
        val bounded = clampOffset(desired)
        overlayOffset = bounded
        val params = overlayLayoutParams ?: return
        val view = overlayView ?: return
        if (params.x != bounded.x || params.y != bounded.y) {
            params.x = bounded.x
            params.y = bounded.y
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun clampOffset(offset: IntOffset): IntOffset {
        val dm = resources.displayMetrics
        val maxX = (dm.widthPixels - overlaySize.width).coerceAtLeast(0)
        val maxY = (dm.heightPixels - overlaySize.height).coerceAtLeast(0)
        return IntOffset(
            x = offset.x.coerceIn(0, maxX),
            y = offset.y.coerceIn(0, maxY)
        )
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
            setViewTreeLifecycleOwner(viewTreeSavedStateOwner)
            setViewTreeSavedStateRegistryOwner(viewTreeSavedStateOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        }
    }

    companion object {
        const val ACTION_START = "com.example.littleclicker.action.START_TIMER_FLOATING_WINDOW"
        const val ACTION_STOP = "com.example.littleclicker.action.STOP_TIMER_FLOATING_WINDOW"

        private val _overlayVisible = MutableStateFlow(false)
        val overlayVisible: StateFlow<Boolean> = _overlayVisible.asStateFlow()

        fun start(context: Context) {
            context.startService(Intent(context, TimerFloatingWindowService::class.java).apply {
                action = ACTION_START
            })
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TimerFloatingWindowService::class.java).apply {
                action = ACTION_STOP
            })
        }
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
