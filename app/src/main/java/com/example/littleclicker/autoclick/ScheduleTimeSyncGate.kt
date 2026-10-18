package com.example.littleclicker.autoclick

/** 每次设定独立记账，校时后时钟回拨也不会重复请求。 */
internal class ScheduleTimeSyncGate {
    private var requested = false

    fun shouldSync(remainingMillis: Long): Boolean {
        if (requested || remainingMillis <= 0L || remainingMillis > 60_000L) return false
        requested = true
        return true
    }
}
