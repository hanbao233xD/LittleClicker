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
import android.widget.Toast
import com.example.littleclicker.service.AutoClickAccessibilityService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

internal data class RootAccessibilityEnableResult(
    val success: Boolean,
    val message: String,
)

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

internal fun ensureOverlayStartPermissions(context: Context): Boolean {
    if (!Settings.canDrawOverlays(context)) {
        openOverlaySettings(context)
        Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
        return false
    }
    if (!isAccessibilityServiceEnabled(context)) {
        openAccessibilitySettings(context)
        Toast.makeText(context, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
        return false
    }
    return true
}

internal fun requestRootAndEnableAccessibility(context: Context): RootAccessibilityEnableResult {
    if (isAccessibilityServiceEnabled(context)) {
        return RootAccessibilityEnableResult(
            success = true,
            message = "无障碍已开启"
        )
    }

    val hasRoot = runSuCommand("id", timeoutMs = 15_000L)?.let { result ->
        result.exitCode == 0 && (result.stdout + result.stderr).contains("uid=0")
    } == true
    if (!hasRoot) {
        return RootAccessibilityEnableResult(
            success = false,
            message = "未获取到ROOT权限，请手动开启无障碍"
        )
    }

    val expectedComponentName = ComponentName(context, AutoClickAccessibilityService::class.java).flattenToString()
    val currentEnabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()
        .split(':')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val mergedServices = (currentEnabledServices + expectedComponentName)
        .distinctBy { it.lowercase(Locale.getDefault()) }
        .joinToString(":")

    val putServicesResult = runSuCommand(
        command = "settings put secure enabled_accessibility_services $mergedServices",
        timeoutMs = 15_000L
    )
    if (putServicesResult == null || putServicesResult.exitCode != 0) {
        return RootAccessibilityEnableResult(
            success = false,
            message = "ROOT写入无障碍服务列表失败，请手动开启"
        )
    }

    val enableResult = runSuCommand(
        command = "settings put secure accessibility_enabled 1",
        timeoutMs = 15_000L
    )
    if (enableResult == null || enableResult.exitCode != 0) {
        return RootAccessibilityEnableResult(
            success = false,
            message = "ROOT开启无障碍失败，请手动开启"
        )
    }

    return if (isAccessibilityServiceEnabled(context)) {
        RootAccessibilityEnableResult(
            success = true,
            message = "ROOT授权成功，已自动开启无障碍"
        )
    } else {
        RootAccessibilityEnableResult(
            success = false,
            message = "ROOT命令已执行，但无障碍未生效，请手动开启"
        )
    }
}

private data class SuCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

private fun runSuCommand(command: String, timeoutMs: Long): SuCommandResult? {
    val process = runCatching {
        ProcessBuilder("su", "-c", command).start()
    }.getOrNull() ?: return null

    val finished = runCatching {
        process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
    }.getOrDefault(false)
    if (!finished) {
        process.destroyForcibly()
        return null
    }

    val stdout = runCatching {
        process.inputStream.bufferedReader().use { it.readText() }
    }.getOrDefault("")
    val stderr = runCatching {
        process.errorStream.bufferedReader().use { it.readText() }
    }.getOrDefault("")
    return SuCommandResult(
        exitCode = process.exitValue(),
        stdout = stdout,
        stderr = stderr
    )
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

internal fun formatHms(timeMillis: Long): String {
    val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return format.format(timeMillis)
}

internal fun formatHmsWithTenths(timeMillis: Long): String {
    val base = formatHms(timeMillis)
    val tenths = ((timeMillis % 1000L + 1000L) % 1000L) / 100L
    return "$base.$tenths"
}
