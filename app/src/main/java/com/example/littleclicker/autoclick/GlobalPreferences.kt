package com.example.littleclicker.autoclick

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 应用级偏好独立存储，不随点击配置导入、复制或切换。 */
object GlobalPreferences {
    private const val PREFERENCES_FILE = "global_preferences"
    private const val KEY_CLEAR_SCHEDULE_ON_RESTART = "clear_schedule_on_restart"
    private var initialized = false
    private val _clearScheduleOnRestart = MutableStateFlow(true)
    val clearScheduleOnRestart = _clearScheduleOnRestart.asStateFlow()

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        _clearScheduleOnRestart.value = context.applicationContext
            .getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_CLEAR_SCHEDULE_ON_RESTART, true)
        initialized = true
    }

    @Synchronized
    fun updateClearScheduleOnRestart(context: Context, enabled: Boolean) {
        initialize(context)
        context.applicationContext.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CLEAR_SCHEDULE_ON_RESTART, enabled).apply()
        _clearScheduleOnRestart.value = enabled
    }
}
