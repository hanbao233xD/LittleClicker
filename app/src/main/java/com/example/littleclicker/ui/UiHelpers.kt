package com.example.littleclicker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.example.littleclicker.service.AutoClickAccessibilityService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal fun openOverlaySettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

internal fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

internal fun openBatteryOptimizationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
    } else {
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

internal fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

internal fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = ComponentName(context, AutoClickAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getInt(
        context.contentResolver,
        Settings.Secure.ACCESSIBILITY_ENABLED,
        0
    ) == 1
    if (!enabled) return false
    val settingValue = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return settingValue.split(':').any { it.equals(expectedComponentName, ignoreCase = true) }
}

internal fun showDateTimePicker(
    context: Context,
    initialMillis: Long?,
    onSelected: (Long) -> Unit,
) {
    val now = Calendar.getInstance()
    val base = Calendar.getInstance().apply {
        if (initialMillis != null) {
            timeInMillis = initialMillis
        }
    }

    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    val selected = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        set(Calendar.HOUR_OF_DAY, hourOfDay)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onSelected(selected.timeInMillis)
                },
                base.get(Calendar.HOUR_OF_DAY),
                base.get(Calendar.MINUTE),
                true
            ).show()
        },
        base.get(Calendar.YEAR),
        base.get(Calendar.MONTH),
        base.get(Calendar.DAY_OF_MONTH)
    ).apply {
        datePicker.minDate = now.timeInMillis
    }.show()
}

internal fun formatDateTime(timeMillis: Long): String {
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return format.format(timeMillis)
}
