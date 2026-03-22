package com.example.littleclicker.autoclick

data class AutoClickPoint(
    val id: Int,
    val x: Int,
    val y: Int,
    val delayMs: Long = 200L,
    val touchDurationMs: Long = 50L,
    val repeatCount: Int = 1,
)

data class AutoClickProfile(
    val id: String = "default",
    val name: String = "默认自动点击配置",
    val points: List<AutoClickPoint> = emptyList(),
    val cycleCount: Int = 1,
    val startAtMillis: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class ScriptDraft(
    val id: String,
    val name: String,
    val actions: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
)

enum class AutoClickRunState {
    Idle,
    Scheduled,
    Running,
    Paused,
    Completed,
    Failed,
}

data class AutoClickRuntime(
    val state: AutoClickRunState = AutoClickRunState.Idle,
    val message: String? = null,
    val scheduledAtMillis: Long? = null,
)

data class AutoClickStep(
    val pointId: Int,
    val x: Int,
    val y: Int,
    val delayMs: Long,
    val touchDurationMs: Long,
)

fun AutoClickProfile.expandExecutionSteps(): List<AutoClickStep> {
    if (points.isEmpty()) return emptyList()

    val safeCycleCount = cycleCount.coerceAtLeast(1)
    val steps = mutableListOf<AutoClickStep>()
    repeat(safeCycleCount) {
        points.forEach { point ->
            val safeRepeatCount = point.repeatCount.coerceAtLeast(1)
            repeat(safeRepeatCount) {
                steps += AutoClickStep(
                    pointId = point.id,
                    x = point.x,
                    y = point.y,
                    delayMs = point.delayMs.coerceAtLeast(0L),
                    touchDurationMs = point.touchDurationMs.coerceAtLeast(1L)
                )
            }
        }
    }
    return steps
}
