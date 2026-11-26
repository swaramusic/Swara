package com.swara.nativeapp

import com.swara.nativeapp.data.YouTubeMusicClient
import com.swara.nativeapp.model.romanizeLyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwaraCoreLogicTest {
    @Test
    fun romanize_hangul_and_kana_work_and_ascii_returns_null() {
        assertNull(romanizeLyricLine("Hello world"))
        val ko = romanizeLyricLine("사랑해")
        assertNotNull(ko)
        assertTrue(ko!!.isNotBlank())
        val jp = romanizeLyricLine("ありがとう")
        assertNotNull(jp)
        assertEquals("arigatou", jp)
    }

    @Test
    fun parseLrc_sorts_and_parses_timestamps() {
        val client = YouTubeMusicClient()
        val lrc = """
            [00:12.50] Second line
            [00:05.10] First line
            [01:02.000] Third line
        """.trimIndent()
        val lines = client.parseLrc(lrc)
        assertEquals(3, lines.size)
        assertEquals(5100L, lines[0].startMs)
        assertEquals("First line", lines[0].text)
        assertEquals(12500L, lines[1].startMs)
        assertEquals(62000L, lines[2].startMs)
    }

    @Test
    fun playlist_url_regex_matches_spotify_and_youtube() {
        val spRegex = Regex("spotify\\.com/(?:intl-[^/]+/)?playlist/([A-Za-z0-9]+)")
        val ytRegex = Regex("[?&]list=([A-Za-z0-9_-]+)")
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            spRegex.find("https://open.spotify.com/intl-id/playlist/37i9dQZF1DXcBWIGoYBM5M?si=123")?.groupValues?.get(1)
        )
        assertEquals(
            "PLDD6QT7xyzfI",
            ytRegex.find("https://music.youtube.com/playlist?list=PLDD6QT7xyzfI&si=abc")?.groupValues?.get(1)
        )
    }
}
