package com.example.littleclicker.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal data class AppNoticeInfo(
    val imageUrl: String,
    val targetUrl: String,
    val imageFilePath: String,
)

internal data class AppNoticeRefreshResult(
    val noticeInfo: List<AppNoticeInfo>,
    val remoteFetched: Boolean,
)

internal object AppNoticeChecker {
    private const val NOTICE_ENDPOINT = "https://littlecold.cn/littleclicker/banner/config.txt"
    private const val NOTICE_DIR = "banner"
    private const val NOTICE_CONFIG_CACHE_FILE = "config_cache.txt"
    private const val NOTICE_IMAGE_CACHE_LEGACY = "banner_cache"
    private const val NOTICE_IMAGE_CACHE_PREFIX = "banner_cache_"

    internal data class BannerConfig(
        val imageUrl: String,
        val targetUrl: String,
    )

    suspend fun loadCachedNotice(context: Context): List<AppNoticeInfo> = withContext(Dispatchers.IO) {
        val configFile = noticeConfigCacheFile(context)
        if (!configFile.exists()) return@withContext emptyList()

        val bannerConfigs = runCatching { configFile.readText() }
            .getOrNull()
            ?.let(::parseBannerConfigs)
            .orEmpty()

        bannerConfigs.mapIndexedNotNull { index, bannerConfig ->
            bannerConfig.toNoticeInfo(findNoticeImageCacheFile(context, index))
        }
    }

    suspend fun refreshNotice(context: Context): AppNoticeRefreshResult = withContext(Dispatchers.IO) {
        val rawText = runCatching { fetchConfigText() }
            .getOrElse { return@withContext AppNoticeRefreshResult(noticeInfo = emptyList(), remoteFetched = false) }

        val bannerConfigs = parseBannerConfigs(rawText)
        if (bannerConfigs.isEmpty()) {
            clearNoticeCache(context)
            return@withContext AppNoticeRefreshResult(
                noticeInfo = emptyList(),
                remoteFetched = true,
            )
        }

        val imagePayloads = bannerConfigs.map { bannerConfig ->
            runCatching { fetchBinaryContent(bannerConfig.imageUrl) }.getOrElse {
                return@withContext AppNoticeRefreshResult(noticeInfo = emptyList(), remoteFetched = false)
            }
        }

        val imageFiles = saveBannerImageCaches(context, bannerConfigs, imagePayloads)
        saveNoticeConfigCache(context, rawText)

        AppNoticeRefreshResult(
            noticeInfo = bannerConfigs.mapIndexedNotNull { index, bannerConfig ->
                bannerConfig.toNoticeInfo(imageFiles.getOrNull(index))
            },
            remoteFetched = true,
        )
    }

    private fun fetchConfigText(): String {
        return fetchTextContent(NOTICE_ENDPOINT)
    }

    private fun fetchTextContent(endpoint: String): String {
        val connection = openConnection(endpoint)
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchBinaryContent(endpoint: String): ByteArray {
        val connection = openConnection(endpoint)
        return try {
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(endpoint: String): HttpURLConnection {
        return (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            doInput = true
        }
    }

    internal fun parseBannerConfigs(rawText: String): List<BannerConfig> {
        return rawText
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull(::parseBannerConfigLine)
            .toList()
    }

    private fun parseBannerConfigLine(rawLine: String): BannerConfig? {
        val line = rawLine.trimStart('\uFEFF')
        val parts = line.split('|', limit = 2)
        if (parts.size < 2) return null

        val imageUrl = parts[0].trim()
        val targetUrl = parts[1].trim()
        if (imageUrl.isBlank() || targetUrl.isBlank()) return null

        return BannerConfig(
            imageUrl = imageUrl,
            targetUrl = targetUrl,
        )
    }

    private fun saveNoticeConfigCache(context: Context, rawText: String) {
        val cacheFile = noticeConfigCacheFile(context)
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(rawText)
    }

    private fun saveBannerImageCaches(
        context: Context,
        bannerConfigs: List<BannerConfig>,
        imagePayloads: List<ByteArray>,
    ): List<File> {
        val cacheDir = noticeCacheDir(context)
        cacheDir.mkdirs()

        val savedFiles = bannerConfigs.mapIndexed { index, bannerConfig ->
            val cacheFile = noticeImageCacheFile(context, index, bannerConfig.imageUrl)
            cacheFile.writeBytes(imagePayloads[index])
            cacheFile
        }

        cacheDir.listFiles()
            ?.filter { it.isFile && isNoticeImageCacheFile(it) && savedFiles.none(savedFiles::contains) }
            ?.forEach { staleFile -> runCatching { staleFile.delete() } }

        return savedFiles
    }

    private fun clearNoticeCache(context: Context) {
        runCatching { noticeConfigCacheFile(context).delete() }
        noticeCacheDir(context)
            .listFiles()
            ?.filter { it.isFile && isNoticeImageCacheFile(it) }
            ?.forEach { cacheFile -> runCatching { cacheFile.delete() } }
    }

    private fun BannerConfig.toNoticeInfo(imageFile: File?): AppNoticeInfo? {
        if (imageFile == null || !imageFile.exists()) return null
        return AppNoticeInfo(
            imageUrl = imageUrl,
            targetUrl = targetUrl,
            imageFilePath = imageFile.absolutePath,
        )
    }

    private fun noticeCacheDir(context: Context): File {
        return File(context.filesDir, NOTICE_DIR)
    }

    private fun noticeConfigCacheFile(context: Context): File {
        return File(noticeCacheDir(context), NOTICE_CONFIG_CACHE_FILE)
    }

    private fun noticeImageCacheFile(context: Context, index: Int, imageUrl: String): File {
        val extension = imageUrl.substringAfterLast('.', missingDelimiterValue = "img")
            .substringBefore('?')
            .substringBefore('#')
            .ifBlank { "img" }
        return File(noticeCacheDir(context), "$NOTICE_IMAGE_CACHE_PREFIX$index.$extension")
    }

    private fun findNoticeImageCacheFile(context: Context, index: Int): File? {
        return noticeCacheDir(context)
            .listFiles()
            ?.firstOrNull { it.isFile && it.name.startsWith("$NOTICE_IMAGE_CACHE_PREFIX$index.") }
    }

    private fun isNoticeImageCacheFile(file: File): Boolean {
        return file.name.startsWith(NOTICE_IMAGE_CACHE_PREFIX) || file.nameWithoutExtension == NOTICE_IMAGE_CACHE_LEGACY
    }
}

internal fun String.decodeEscapedLineBreaks(): String {
    return this
        .replace("\\r\\n", "\n")
        .replace("\\n", "\n")
        .replace("\\r", "\n")
}
