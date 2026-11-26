package com.swara.nativeapp

import com.swara.nativeapp.data.YouTubeMusicClient
import com.swara.nativeapp.model.Track
import com.swara.nativeapp.model.romanizeLyricLine
import com.swara.nativeapp.ui.buildShuffledQueue
import com.swara.nativeapp.ui.sanitizeRemoteArtworkUrl
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
            spRegex.find("https://open.spotify.com/intl-id/playlist/37i9dQZF1DXcBWIGoYBM5M?si=123")?.groupValues?.get(1),
        )
        assertEquals(
            "PLDD6QT7xyzfI",
            ytRegex.find("https://music.youtube.com/playlist?list=PLDD6QT7xyzfI&si=abc")?.groupValues?.get(1),
        )
    }

    @Test
    fun buildShuffledQueue_keeps_current_track_first_and_preserves_all_items() {
        val tracks = (1..5).map { Track("id$it", "Song $it", "Artist $it", "") }
        val shuffled = buildShuffledQueue(tracks, 2)
        assertEquals(5, shuffled.size)
        assertEquals("id3", shuffled.first().id)
        assertEquals(tracks.map { it.id }.toSet(), shuffled.map { it.id }.toSet())
    }

    @Test
    fun buildShuffledQueue_with_single_track_returns_same_list() {
        val tracks = listOf(Track("id1", "Solo", "Artist", ""))
        val shuffled = buildShuffledQueue(tracks, 0)
        assertEquals(1, shuffled.size)
        assertEquals("id1", shuffled.first().id)
    }

    @Test
    fun buildShuffledQueue_with_index_zero_keeps_first_track() {
        val tracks = (1..4).map { Track("id$it", "Song $it", "Artist $it", "") }
        val shuffled = buildShuffledQueue(tracks, 0)
        assertEquals(4, shuffled.size)
        assertEquals("id1", shuffled.first().id)
    }

    @Test
    fun sanitizeRemoteArtworkUrl_allows_only_trusted_https_hosts() {
        val valid = "https://i.ytimg.com/vi/abc123/hqdefault.jpg"
        assertEquals(valid, sanitizeRemoteArtworkUrl(valid))
        assertEquals("", sanitizeRemoteArtworkUrl("http://i.ytimg.com/vi/abc123/hqdefault.jpg"))
        assertEquals("", sanitizeRemoteArtworkUrl("https://evil.example.com/a.jpg"))
        assertEquals("", sanitizeRemoteArtworkUrl("not a url"))
    }

    @Test
    fun sanitizeRemoteArtworkUrl_allows_all_trusted_hosts() {
        val trustedUrls = listOf(
            "https://i.ytimg.com/vi/abc/hq.jpg",
            "https://lh3.googleusercontent.com/abc",
            "https://yt3.googleusercontent.com/abc",
            "https://music.youtube.com/thumbnail/abc",
        )
        for (url in trustedUrls) {
            assertEquals("Expected $url to pass", url, sanitizeRemoteArtworkUrl(url))
        }
    }

    @Test
    fun playlist_url_regex_handles_edge_cases() {
        val spRegex = Regex("spotify\\.com/(?:intl-[^/]+/)?playlist/([A-Za-z0-9]+)")
        val ytRegex = Regex("[?&]list=([A-Za-z0-9_-]+)")
        // No match cases
        assertNull(spRegex.find("https://open.spotify.com/track/abc"))
        assertNull(ytRegex.find("https://music.youtube.com/watch?v=abc"))
        // Direct playlist without intl
        assertEquals(
            "ABC123",
            spRegex.find("https://open.spotify.com/playlist/ABC123")?.groupValues?.get(1),
        )
    }
}
