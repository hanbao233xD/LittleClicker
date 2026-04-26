package com.example.littleclicker.autoclick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AutoClickSerializationTest {

    @Test
    fun profileSerialization_roundTrip_preservesValues() {
        val profile = AutoClickProfile(
            id = "profile-a",
            name = "测试配置",
            points = listOf(
                AutoClickPoint(id = 1, x = 100, y = 200, delayMs = 300, touchDurationMs = 80, repeatCount = 2),
                AutoClickPoint(id = 2, x = 500, y = 900, delayMs = 50, touchDurationMs = 20, repeatCount = 1)
            ),
            cycleCount = 3,
            layoutLocked = true,
            runMode = AutoClickRunMode.LoopUntilStopped,
            clickRandomOffsetPx = 9,
            ntpServerHost = "time.google.com",
            scheduleRuleHms = "11:22:33",
            startAtMillis = 1_735_000_000_000,
            updatedAt = 1_735_100_000_000
        )

        val json = AutoClickRepository.profileToJson(profile)
        val restored = AutoClickRepository.profileFromJson(json)

        assertEquals(profile.id, restored.id)
        assertEquals(profile.name, restored.name)
        assertEquals(profile.points, restored.points)
        assertEquals(profile.cycleCount, restored.cycleCount)
        assertEquals(profile.layoutLocked, restored.layoutLocked)
        assertEquals(profile.runMode, restored.runMode)
        assertEquals(profile.clickRandomOffsetPx, restored.clickRandomOffsetPx)
        assertEquals(profile.ntpServerHost, restored.ntpServerHost)
        assertEquals(profile.scheduleRuleHms, restored.scheduleRuleHms)
        assertEquals(profile.startAtMillis, restored.startAtMillis)
    }

    @Test
    fun profileFromJson_missingNewFields_usesDefaults() {
        val legacyJson = """
            {
              "id": "legacy",
              "name": "旧配置",
              "cycleCount": 1,
              "points": [
                { "id": 1, "x": 100, "y": 200 }
              ]
            }
        """.trimIndent()

        val restored = AutoClickRepository.profileFromJson(legacyJson)

        assertFalse(restored.layoutLocked)
        assertEquals(DEFAULT_CLICK_RANDOM_OFFSET_PX, restored.clickRandomOffsetPx)
        assertEquals(DEFAULT_NTP_SERVER_HOST, restored.ntpServerHost)
        assertNull(restored.scheduleRuleHms)
    }

    @Test
    fun expandExecutionSteps_respectsPointRepeatOnly() {
        val profile = AutoClickProfile(
            points = listOf(
                AutoClickPoint(id = 1, x = 10, y = 20, delayMs = 100, touchDurationMs = 40, repeatCount = 2),
                AutoClickPoint(id = 2, x = 30, y = 40, delayMs = 200, touchDurationMs = 60, repeatCount = 1)
            ),
            cycleCount = 2
        )

        val steps = profile.expandExecutionSteps()

        assertEquals(3, steps.size)
        assertEquals(listOf(1, 1, 2), steps.map { it.pointId })
        assertEquals(100L, steps[0].delayMs)
        assertEquals(60L, steps[2].touchDurationMs)
    }

    @Test
    fun scheduleAtHms_whenPast_returnsFalse() {
        val now = Calendar.getInstance().apply {
            timeInMillis = AutoClickCoordinator.currentAlignedNowMillis()
        }
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val second = now.get(Calendar.SECOND)

        val success = AutoClickCoordinator.scheduleAtHms(hour, minute, second)

        assertFalse(success)
        assertEquals(
            String.format("%02d:%02d:%02d", hour, minute, second),
            AutoClickCoordinator.profile.value.scheduleRuleHms
        )
        assertNull(AutoClickCoordinator.profile.value.startAtMillis)
        AutoClickCoordinator.clearScheduleTime()
    }

    @Test
    fun scheduleAtHms_whenFuture_returnsTrue() {
        val nowMillis = AutoClickCoordinator.currentAlignedNowMillis()
        val baseToday = Calendar.getInstance().apply {
            timeInMillis = nowMillis
        }
        var targetHour = -1
        var targetMinute = -1
        var targetSecond = -1
        for (offset in 1..3_600) {
            val fromNow = Calendar.getInstance().apply {
                timeInMillis = nowMillis + offset * 1_000L
            }
            val candidate = Calendar.getInstance().apply {
                timeInMillis = baseToday.timeInMillis
                set(Calendar.HOUR_OF_DAY, fromNow.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, fromNow.get(Calendar.MINUTE))
                set(Calendar.SECOND, fromNow.get(Calendar.SECOND))
                set(Calendar.MILLISECOND, 0)
            }
            if (candidate.timeInMillis > nowMillis) {
                targetHour = fromNow.get(Calendar.HOUR_OF_DAY)
                targetMinute = fromNow.get(Calendar.MINUTE)
                targetSecond = fromNow.get(Calendar.SECOND)
                break
            }
        }
        assertTrue(targetHour >= 0)

        val success = AutoClickCoordinator.scheduleAtHms(targetHour, targetMinute, targetSecond)

        assertTrue(success)
        assertEquals(
            String.format("%02d:%02d:%02d", targetHour, targetMinute, targetSecond),
            AutoClickCoordinator.profile.value.scheduleRuleHms
        )
        assertNotNull(AutoClickCoordinator.profile.value.startAtMillis)
        assertEquals(AutoClickRunState.Scheduled, AutoClickCoordinator.runtime.value.state)
        AutoClickCoordinator.clearScheduleTime()
    }

    @Test
    fun startNow_whenRecordingActive_returnsFalse() {
        AutoClickCoordinator.reportExecutionState(AutoClickRunState.Idle, "test reset")
        AutoClickCoordinator.stopRecording()
        val startedRecording = AutoClickCoordinator.startRecording()
        assertTrue(startedRecording)

        val startedExecution = AutoClickCoordinator.startNow()

        assertFalse(startedExecution)
        assertEquals(AutoClickRunState.Failed, AutoClickCoordinator.runtime.value.state)
        assertEquals("录制中，禁止执行脚本", AutoClickCoordinator.runtime.value.message)
        AutoClickCoordinator.stopRecording()
    }

    @Test
    fun startRecording_whenExecutionRunning_returnsFalse() {
        AutoClickCoordinator.stopRecording()
        AutoClickCoordinator.reportExecutionState(AutoClickRunState.Running, "test running")

        val startedRecording = AutoClickCoordinator.startRecording()

        assertFalse(startedRecording)
        assertEquals(AutoClickRunState.Failed, AutoClickCoordinator.runtime.value.state)
        assertEquals("执行中不可录制，请先停止", AutoClickCoordinator.runtime.value.message)
        AutoClickCoordinator.reportExecutionState(AutoClickRunState.Idle, "test reset")
    }
}
