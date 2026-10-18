package com.example.littleclicker.autoclick

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTimeSyncGateTest {
    @Test
    fun longSchedule_syncsAtOneMinuteOnlyOnceEvenIfClockMovesBack() {
        val gate = ScheduleTimeSyncGate()
        assertFalse(gate.shouldSync(3_600_000L))
        assertFalse(gate.shouldSync(60_001L))
        assertTrue(gate.shouldSync(60_000L))
        assertFalse(gate.shouldSync(59_970L))
        assertFalse(gate.shouldSync(90_000L))
        assertFalse(gate.shouldSync(60_000L))
    }

    @Test
    fun shortOrRestoredSchedule_syncsImmediatelyButExpiredScheduleDoesNot() {
        assertTrue(ScheduleTimeSyncGate().shouldSync(15_000L))
        assertTrue(ScheduleTimeSyncGate().shouldSync(1L))
        assertFalse(ScheduleTimeSyncGate().shouldSync(0L))
        assertFalse(ScheduleTimeSyncGate().shouldSync(-1L))
    }

    @Test
    fun replacingSchedule_getsIndependentSyncAttempt() {
        val old = ScheduleTimeSyncGate()
        assertTrue(old.shouldSync(10_000L))
        assertFalse(old.shouldSync(9_000L))
        assertTrue(ScheduleTimeSyncGate().shouldSync(60_000L))
    }
}
