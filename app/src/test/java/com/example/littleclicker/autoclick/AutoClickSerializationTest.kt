package com.example.littleclicker.autoclick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
            startAtMillis = 1_735_000_000_000,
            updatedAt = 1_735_100_000_000
        )

        val json = AutoClickRepository.profileToJson(profile)
        val restored = AutoClickRepository.profileFromJson(json)

        assertEquals(profile.id, restored.id)
        assertEquals(profile.name, restored.name)
        assertEquals(profile.points, restored.points)
        assertEquals(profile.cycleCount, restored.cycleCount)
        assertEquals(profile.startAtMillis, restored.startAtMillis)
    }

    @Test
    fun scriptDraftSerialization_roundTrip_allowsEmptyActions() {
        val draft = ScriptDraft(
            id = "draft_001",
            name = "空动作草稿",
            actions = emptyList(),
            createdAt = 1_735_100_000_000,
            updatedAt = 1_735_200_000_000
        )

        val json = AutoClickRepository.draftToJson(draft)
        val restored = AutoClickRepository.draftFromJson(json)

        assertEquals(draft.id, restored.id)
        assertEquals(draft.name, restored.name)
        assertTrue(restored.actions.isEmpty())
        assertEquals(draft.updatedAt, restored.updatedAt)
    }

    @Test
    fun expandExecutionSteps_respectsPointRepeatAndGlobalCycle() {
        val profile = AutoClickProfile(
            points = listOf(
                AutoClickPoint(id = 1, x = 10, y = 20, delayMs = 100, touchDurationMs = 40, repeatCount = 2),
                AutoClickPoint(id = 2, x = 30, y = 40, delayMs = 200, touchDurationMs = 60, repeatCount = 1)
            ),
            cycleCount = 2
        )

        val steps = profile.expandExecutionSteps()

        assertEquals(6, steps.size)
        assertEquals(listOf(1, 1, 2, 1, 1, 2), steps.map { it.pointId })
        assertEquals(100L, steps[0].delayMs)
        assertEquals(60L, steps[2].touchDurationMs)
    }
}
