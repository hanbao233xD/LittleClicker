package com.example.littleclicker.update

import org.junit.Assert.assertEquals
import org.junit.Test

class EscapedLineBreaksTest {
    @Test
    fun `decodeEscapedLineBreaks converts escaped lf to real line break`() {
        val raw = "第一行\\n第二行\\n第三行"

        val decoded = raw.decodeEscapedLineBreaks()

        assertEquals("第一行\n第二行\n第三行", decoded)
    }

    @Test
    fun `decodeEscapedLineBreaks converts escaped crlf to real line break`() {
        val raw = "A\\r\\nB\\r\\nC"

        val decoded = raw.decodeEscapedLineBreaks()

        assertEquals("A\nB\nC", decoded)
    }
}
