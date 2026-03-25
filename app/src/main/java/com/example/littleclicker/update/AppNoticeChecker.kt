package com.example.littleclicker.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal data class AppNoticeInfo(
    val link: String,
    val content: String,
)

internal object AppNoticeChecker {
    private const val NOTICE_ENDPOINT = "https://littlecold.cn/littleclicker/notice.txt"

    suspend fun fetchNotice(): AppNoticeInfo? = withContext(Dispatchers.IO) {
        runCatching { fetchNoticeText() }
            .getOrNull()
            ?.let(::parseNoticeText)
    }

    private fun fetchNoticeText(): String {
        val connection = (URL(NOTICE_ENDPOINT).openConnection() as HttpURLConnection).apply {
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

    private fun parseNoticeText(rawText: String): AppNoticeInfo? {
        val line = rawText
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.trimStart('\uFEFF')
            ?: return null
        val parts = line.split('|', limit = 2)
        if (parts.size < 2) return null

        val link = parts[0].trim()
        val content = parts[1].trim().ifBlank { "点击查看最新公告" }
        if (link.isBlank()) return null

        return AppNoticeInfo(
            link = link,
            content = content,
        )
    }
}
