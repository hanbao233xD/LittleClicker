package com.example.littleclicker.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class UiHelpersFormatTest {

    @Test
    fun formatHms_formatsExpectedValue() {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            assertEquals("00:00:00", formatHms(0L))
            assertEquals("12:34:56", formatHms(45_296_000L))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun formatHmsWithTenths_formatsExpectedValue() {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            assertEquals("00:00:00.0", formatHmsWithTenths(0L))
            assertEquals("00:00:12.3", formatHmsWithTenths(12_345L))
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
