package com.example.littleclicker.update

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNoticeCheckerTest {
    @Test
    fun `parseBannerConfigs parses multiple image and target urls`() {
        val config = AppNoticeChecker.parseBannerConfigs(
            """
            https://littlecold.cn/littleclicker/banner/littleapi.png|https://api.littlecold.cn
            https://littlecold.cn/littleclicker/banner/second.png|https://littlecold.cn
            """.trimIndent()
        )

        assertEquals(2, config.size)
        assertEquals("https://littlecold.cn/littleclicker/banner/littleapi.png", config[0].imageUrl)
        assertEquals("https://api.littlecold.cn", config[0].targetUrl)
        assertEquals("https://littlecold.cn/littleclicker/banner/second.png", config[1].imageUrl)
        assertEquals("https://littlecold.cn", config[1].targetUrl)
    }

    @Test
    fun `parseBannerConfigs skips invalid rows`() {
        val config = AppNoticeChecker.parseBannerConfigs(
            """
            |https://api.littlecold.cn
            https://littlecold.cn/littleclicker/banner/littleapi.png|https://api.littlecold.cn
            invalid-row
            """.trimIndent()
        )

        assertEquals(1, config.size)
        assertEquals("https://littlecold.cn/littleclicker/banner/littleapi.png", config[0].imageUrl)
    }
}
