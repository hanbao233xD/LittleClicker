package com.example.littleclicker.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal data class AppUpdateInfo(
    val versionCode: Int,
    val downloadUrl: String,
    val changelog: String,
)

internal object AppUpdateChecker {
    private const val VERSION_ENDPOINT = "https://littlecold.cn/littleclicker/version.txt"

    suspend fun checkUpdate(localVersionCode: Int): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val remoteInfo = runCatching { fetchVersionText() }
            .getOrNull()
            ?.let(::parseVersionText)
            ?: return@withContext null

        if (remoteInfo.versionCode > localVersionCode) remoteInfo else null
    }

    private fun fetchVersionText(): String {
        val connection = (URL(VERSION_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            doInput = true
        }
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseVersionText(rawText: String): AppUpdateInfo? {
        val line = rawText
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.trimStart('\uFEFF')
            ?: return null
        val parts = line.split('|', limit = 3)
        if (parts.size < 3) return null

        val versionCode = parts[0].trim().toIntOrNull() ?: return null
        val downloadUrl = parts[1].trim()
        val changelog = parts[2]
            .trim()
            .decodeEscapedLineBreaks()
            .ifBlank { "暂无更新日志" }
        if (downloadUrl.isBlank()) return null

        return AppUpdateInfo(
            versionCode = versionCode,
            downloadUrl = downloadUrl,
            changelog = changelog,
        )
    }
}
