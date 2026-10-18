package com.example.littleclicker.tips

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal object HitokotoClient {
    private const val ENDPOINT = "https://international.v1.hitokoto.cn/"

    suspend fun fetchTip(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { parseTip(it.readText()) }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    internal fun parseTip(json: String): String? = runCatching {
        val payload = JsonParser.parseString(json).asJsonObject
        fun textField(name: String): String? {
            val value = payload.get(name) ?: return null
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
            return value.asString.trim().takeIf { it.isNotEmpty() }
        }
        val text = textField("hitokoto") ?: return@runCatching null
        val source = textField("from") ?: "佚名"
        "$text---$source"
    }.getOrNull()
}
