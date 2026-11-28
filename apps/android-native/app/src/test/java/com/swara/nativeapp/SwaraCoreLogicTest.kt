package com.swara.nativeapp

import com.swara.nativeapp.data.SwaraDatabaseHelper
import com.swara.nativeapp.data.YouTubeMusicClient
import com.swara.nativeapp.model.Track
import com.swara.nativeapp.model.romanizeLyricLine
import com.swara.nativeapp.model.stringsFor
import com.swara.nativeapp.ui.SwaraBackupCodec
import com.swara.nativeapp.ui.SwaraUpdater
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
        val lrc =
            """
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
            spRegex.find(
                "https://open.spotify.com/intl-id/playlist/37i9dQZF1DXcBWIGoYBM5M?si=123"
            )?.groupValues?.get(1)
        )
        assertEquals(
            "PLDD6QT7xyzfI",
            ytRegex.find(
                "https://music.youtube.com/playlist?list=PLDD6QT7xyzfI&si=abc"
            )?.groupValues?.get(1)
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
        val trustedUrls =
            listOf(
                "https://i.ytimg.com/vi/abc/hq.jpg",
                "https://lh3.googleusercontent.com/abc",
                "https://yt3.googleusercontent.com/abc",
                "https://music.youtube.com/thumbnail/abc"
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
            spRegex.find("https://open.spotify.com/playlist/ABC123")?.groupValues?.get(1)
        )
    }

    @Test
    fun parseLrc_skips_metadata_empty_lines_and_handles_variable_fractions() {
        val client = YouTubeMusicClient()
        val lrc =
            """
            [ar:Test Artist]
            [ti:Test Song]
            [00:01] No fraction
            [00:02.5] One digit
            [00:03.05] Two digits
            [00:04.123] Three digits
            [00:05.00]   
            plain text without timestamp
            """.trimIndent()
        val lines = client.parseLrc(lrc)
        assertEquals(4, lines.size)
        assertEquals(1000L, lines[0].startMs)
        assertEquals("No fraction", lines[0].text)
        assertEquals(2500L, lines[1].startMs)
        assertEquals(3050L, lines[2].startMs)
        assertEquals(4123L, lines[3].startMs)
    }

    @Test
    fun romanize_katakana_and_mixed_text_works() {
        val kata = romanizeLyricLine("さくら")
        assertNotNull(kata)
        assertEquals("sakura", kata)

        val mixed = romanizeLyricLine("Hello さくら")
        assertNotNull(mixed)
        assertTrue(mixed!!.contains("Hello"))
        assertTrue(mixed.contains("sakura"))
    }

    @Test
    fun stringsFor_returns_valid_localization_and_falls_back_gracefully() {
        val idStrings = stringsFor("id")
        val enStrings = stringsFor("en")
        val fallback = stringsFor("xx_invalid")
        assertTrue(idStrings.home.isNotBlank())
        assertTrue(enStrings.home.isNotBlank())
        assertTrue(fallback.home.isNotBlank())
    }

    @Test
    fun lyrics_title_and_artist_cleaning_strips_noise() {
        val client = YouTubeMusicClient()
        assertEquals(
            "Berbeza Kasta",
            client.cleanTrackTitleForLyrics("Berbeza Kasta (Official Music Video) | Lirik"),
        )
        assertEquals(
            "Shape of You",
            client.cleanTrackTitleForLyrics("Shape of You - Official Audio"),
        )
        assertEquals(
            "Thomas Arya",
            client.cleanArtistForLyrics("Thomas Arya - Topic"),
        )
    }

    @Test
    fun updater_formats_release_notes_and_compares_versions() {
        assertTrue(SwaraUpdater.isVersionNewer("v1.0.2", "1.0.1"))
        assertTrue(!SwaraUpdater.isVersionNewer("1.0.2", "1.0.2"))
        val raw = "**Full Changelog**: https://github.com/swaramusic/Swara/compare/v1.0.1...v1.0.2"
        val formatted = SwaraUpdater.formatReleaseNotes(raw)
        assertTrue(!formatted.contains("**"))
        assertTrue(!formatted.contains("https://github.com"))
    }

    @Test
    fun sqlite_codec_and_backup_codec_preserve_tracks_and_playlists() {
        val tracks = listOf(Track("t1", "Song One", "Artist A", "https://i.ytimg.com/vi/t1/hq.jpg"))
        val encoded = SwaraDatabaseHelper.encodeTracks(tracks).toString()
        val decoded = SwaraDatabaseHelper.decodeTracks(encoded)
        assertEquals(1, decoded.size)
        assertEquals("t1", decoded[0].id)
        assertEquals("Song One", decoded[0].title)

        val backupJson = SwaraBackupCodec.buildBackupJson(
            playlistsRaw = "[]",
            likedRaw = encoded,
            recentRaw = "[]",
            followedRaw = "[\"Artist A\"]",
            excludedRaw = "[]",
            streakDays = 5,
            totalPlays = 42,
            totalSeconds = 3600,
            artistCountsRaw = "{\"Artist A\":10}",
            songCountsRaw = "{\"t1\":10}",
        )
        assertTrue(backupJson.contains("swara_backup_v1"))
        assertTrue(backupJson.contains("Song One"))
        assertTrue(backupJson.contains("\"total_plays\": 42"))
    }
}
