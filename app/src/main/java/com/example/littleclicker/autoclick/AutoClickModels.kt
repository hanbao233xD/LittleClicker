package com.example.littleclicker.autoclick

data class AutoClickPoint(
    val id: Int,
    val x: Int,
    val y: Int,
    val actionType: AutoClickActionType = AutoClickActionType.Click,
    val endX: Int? = null,
    val endY: Int? = null,
    val delayMs: Long = 200L,
    val touchDurationMs: Long = 50L,
    val repeatCount: Int = 1,
    val targetText: String = "",
    val textFindRetryCount: Int = DEFAULT_TEXT_FIND_RETRY_COUNT,
    val textFindRetryDelayMs: Long = DEFAULT_TEXT_FIND_RETRY_DELAY_MS,
    val continuousRetry: Boolean = false,
)

const val DEFAULT_NTP_SERVER_HOST = "ntp.aliyun.com"
const val DEFAULT_LOOP_INTERVAL_DELAY_MS = 200L
const val DEFAULT_CLICK_RANDOM_OFFSET_PX = 6
const val DEFAULT_RANDOM_DELAY_MS = 10L
const val DEFAULT_SCHEDULE_ADVANCE_MS = 0L
const val DEFAULT_TEXT_FIND_RETRY_COUNT = 3
const val DEFAULT_TEXT_FIND_RETRY_DELAY_MS = 500L

data class AutoClickProfile(
    val id: String = "default",
    val name: String = "默认自动点击配置",
    val points: List<AutoClickPoint> = emptyList(),
    val cycleCount: Int = 1,
    val layoutLocked: Boolean = false,
    val panelCollapsed: Boolean = false,
    val runMode: AutoClickRunMode = AutoClickRunMode.RunOnce,
    val loopIntervalDelayMs: Long = DEFAULT_LOOP_INTERVAL_DELAY_MS,
    val clickRandomOffsetPx: Int = DEFAULT_CLICK_RANDOM_OFFSET_PX,
    val randomDelayMs: Long = DEFAULT_RANDOM_DELAY_MS,
    val recordingMode: AutoClickRecordingMode = AutoClickRecordingMode.RecordAndPassThrough,
    val ntpServerHost: String = DEFAULT_NTP_SERVER_HOST,
    val scheduleAdvanceMs: Long = DEFAULT_SCHEDULE_ADVANCE_MS,
    val scheduleRuleHms: String? = null,
    val startAtMillis: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class AutoClickActionType {
    Click,
    Swipe,
    Home,
    Back,
    Recents,
    TextClick,
}

val AutoClickActionType.displayName: String
    get() = when (this) {
        AutoClickActionType.Click -> "点击"
        AutoClickActionType.Swipe -> "滑动"
        AutoClickActionType.Home -> "Home"
        AutoClickActionType.Back -> "Back"
        AutoClickActionType.Recents -> "多任务"
        AutoClickActionType.TextClick -> "识别文字点击"
    }

val AutoClickActionType.usesScreenCoordinates: Boolean
    get() = when (this) {
        AutoClickActionType.Click, AutoClickActionType.Swipe -> true
        AutoClickActionType.Home, AutoClickActionType.Back, AutoClickActionType.Recents -> false
        AutoClickActionType.TextClick -> false
    }

val AutoClickActionType.usesTouchDuration: Boolean
    get() = usesScreenCoordinates

enum class AutoClickRunState {
    Idle,
    Scheduled,
    Running,
    Paused,
    Completed,
    Failed,
}

enum class AutoClickRunMode {
    RunOnce,
    LoopUntilStopped,
}

enum class AutoClickRecordingMode {
    RecordOnly,
    RecordAndPassThrough,
}

val AutoClickRecordingMode.displayName: String
    get() = when (this) {
        AutoClickRecordingMode.RecordOnly -> "仅录制"
        AutoClickRecordingMode.RecordAndPassThrough -> "录制时穿透到应用"
    }

data class AutoClickRuntime(
    val state: AutoClickRunState = AutoClickRunState.Idle,
    val message: String? = null,
    val scheduledAtMillis: Long? = null,
)

data class TimeSyncState(
    val serverHost: String = DEFAULT_NTP_SERVER_HOST,
    val isSynced: Boolean = false,
    val offsetMillis: Long = 0L,
    val delayMillis: Long? = null,
    val fallbackToDeviceTime: Boolean = true,
    val errorMessage: String? = null,
)

data class AutoClickRecordingState(
    val isRecording: Boolean = false,
    val recordedCount: Int = 0,
    val lastTapAtMillis: Long? = null,
)

data class AutoClickStep(
    val pointId: Int,
    val x: Int,
    val y: Int,
    val actionType: AutoClickActionType,
    val endX: Int?,
    val endY: Int?,
    val delayMs: Long,
    val touchDurationMs: Long,
)

fun AutoClickProfile.expandExecutionSteps(): List<AutoClickStep> {
    if (points.isEmpty()) return emptyList()

    val steps = mutableListOf<AutoClickStep>()
    points.forEach { point ->
        val safeRepeatCount = point.repeatCount.coerceAtLeast(1)
        repeat(safeRepeatCount) {
            steps += AutoClickStep(
                pointId = point.id,
                x = point.x,
                y = point.y,
                actionType = point.actionType,
                endX = point.endX,
                endY = point.endY,
                delayMs = point.delayMs.coerceAtLeast(0L),
                touchDurationMs = point.touchDurationMs.coerceAtLeast(1L)
            )
        }
    }
    return steps
}
