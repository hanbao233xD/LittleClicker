package com.example.littleclicker.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerFloatingWindowPreferencesTest {

    @Test
    fun normalizeFontSizeSp_roundsAndClampsToSupportedRange() {
        assertEquals(12f, TimerFloatingWindowPreferences.normalizeFontSizeSp(9.4f), 0f)
        assertEquals(19f, TimerFloatingWindowPreferences.normalizeFontSizeSp(18.6f), 0f)
        assertEquals(32f, TimerFloatingWindowPreferences.normalizeFontSizeSp(38.8f), 0f)
    }
}
