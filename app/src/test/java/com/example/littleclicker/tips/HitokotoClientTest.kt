package com.example.littleclicker.tips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HitokotoClientTest {
    @Test
    fun parsesQuoteAndSourceRatherThanAuthor() {
        assertEquals(
            "用代码书写山河。---一言开发者中心",
            HitokotoClient.parseTip("""{"hitokoto":" 用代码书写山河。 ","from":"一言开发者中心","from_who":"作者"}""")
        )
    }

    @Test
    fun missingSource_preservesDisplayFormat() {
        assertEquals("你好---佚名", HitokotoClient.parseTip("""{"hitokoto":"你好","from":null}"""))
    }

    @Test
    fun malformedResponses_doNotBecomeVisibleQuotes() {
        listOf("<html>502</html>", "null", "[]", "{}", """{"hitokoto":" "}""",
            """{"hitokoto":123}""", """{"hitokoto":{}}""").forEach {
            assertNull(HitokotoClient.parseTip(it))
        }
    }
}
