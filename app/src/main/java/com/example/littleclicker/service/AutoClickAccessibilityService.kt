package com.example.littleclicker.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class AutoClickAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Placeholder for auto-click script execution.
    }

    override fun onInterrupt() {
        // Required callback.
    }
}
