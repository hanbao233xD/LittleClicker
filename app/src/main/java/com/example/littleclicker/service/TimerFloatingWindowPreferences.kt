package com.example.littleclicker.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/** 独立保存定时悬浮窗的展示偏好，不写入点击配置。 */
object TimerFloatingWindowPreferences {
    const val MIN_FONT_SIZE_SP = 12f
    const val MAX_FONT_SIZE_SP = 32f
    const val DEFAULT_FONT_SIZE_SP = 16f

    private const val PREFERENCES_FILE_NAME = "timer_floating_window_preferences"
    private const val KEY_FONT_SIZE_SP = "font_size_sp"

    private val initializationLock = Any()
    private var initialized = false
    private val _fontSizeSp = MutableStateFlow(DEFAULT_FONT_SIZE_SP)
    val fontSizeSp: StateFlow<Float> = _fontSizeSp.asStateFlow()

    fun initialize(context: Context) {
        if (initialized) return

        synchronized(initializationLock) {
            if (initialized) return

            val preferences = context.applicationContext.getSharedPreferences(
                PREFERENCES_FILE_NAME,
                Context.MODE_PRIVATE
            )
            _fontSizeSp.value = normalizeFontSizeSp(
                preferences.getFloat(KEY_FONT_SIZE_SP, DEFAULT_FONT_SIZE_SP)
            )
            initialized = true
        }
    }

    fun updateFontSizeSp(context: Context, fontSizeSp: Float) {
        initialize(context)
        val normalizedSize = normalizeFontSizeSp(fontSizeSp)
        if (_fontSizeSp.value == normalizedSize) return

        _fontSizeSp.value = normalizedSize
        context.applicationContext.getSharedPreferences(
            PREFERENCES_FILE_NAME,
            Context.MODE_PRIVATE
        ).edit().putFloat(KEY_FONT_SIZE_SP, normalizedSize).apply()
    }

    internal fun normalizeFontSizeSp(fontSizeSp: Float): Float {
        return fontSizeSp.roundToInt().toFloat().coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
    }
}
