package com.example.littleclicker.autoclick

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SchedulePersistenceTest {
    @Test
    fun savedSchedule_survivesReloadAndStartupCleanupClearsAllProfiles() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(appContext.cacheDir, "schedule-test-${System.nanoTime()}")
        val context = object : ContextWrapper(appContext) {
            override fun getFilesDir(): File = directory
        }
        try {
            val target = System.currentTimeMillis() + 3_600_000L
            val profile = AutoClickProfile(
                id = "schedule-test", scheduleRuleHms = "12:00:00", startAtMillis = target,
                scheduleAdvanceMs = 100L,
                points = listOf(AutoClickPoint(id = 1, x = 10, y = 20))
            )
            AutoClickRepository.saveProfile(context, profile)
            AutoClickRepository.saveProfile(context, profile.copy(id = "other"), makeActive = false)
            val reloaded = AutoClickRepository.loadActiveProfile(context)
            assertEquals(target, reloaded.startAtMillis)
            assertEquals("12:00:00", reloaded.scheduleRuleHms)
            AutoClickRepository.clearAllScheduleTimes(context)
            AutoClickRepository.listProfiles(context).forEach {
                assertNull(it.startAtMillis)
                assertNull(it.scheduleRuleHms)
                assertEquals(profile.points, it.points)
                assertEquals(100L, it.scheduleAdvanceMs)
            }
            assertEquals(profile.id, AutoClickRepository.getActiveProfileId(context))
        } finally {
            directory.deleteRecursively()
        }
    }
}
