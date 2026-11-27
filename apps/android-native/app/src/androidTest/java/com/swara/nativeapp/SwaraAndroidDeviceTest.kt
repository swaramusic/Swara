package com.swara.nativeapp

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swara.nativeapp.model.Track
import com.swara.nativeapp.model.stringsFor
import com.swara.nativeapp.playback.SwaraPlaybackService
import com.swara.nativeapp.ui.SwaraViewModel
import com.swara.nativeapp.ui.createPlaylist
import com.swara.nativeapp.ui.deletePlaylist
import com.swara.nativeapp.ui.sanitizeRemoteArtworkUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwaraAndroidDeviceTest {
    @Test
    fun appContext_and_packageName_areValid() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.swara.nativeapp", ctx.packageName)
        assertTrue(stringsFor("id").home.isNotBlank())
        assertTrue(stringsFor("en").home.isNotBlank())
    }

    @Test
    fun playbackService_trackUri_usesSwaraScheme() {
        val uri = SwaraPlaybackService.trackUri("dQw4w9WgXcQ")
        assertEquals("swara", uri.scheme)
        assertEquals("dQw4w9WgXcQ", uri.host)
    }

    @Test
    fun viewModel_playlistLifecycle_and_likeToggle_workOnDevice() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val vm = SwaraViewModel(app)
        val initialCount = vm.ui.value.playlists.size

        val uniqueName = "AuditTestPlaylist_${System.currentTimeMillis()}"
        vm.createPlaylist(uniqueName)
        val created = vm.ui.value.playlists.firstOrNull { it.name == uniqueName }
        assertTrue("Playlist should be created", created != null)
        assertEquals(initialCount + 1, vm.ui.value.playlists.size)

        vm.deletePlaylist(created!!.id)
        assertEquals(initialCount, vm.ui.value.playlists.size)

        val testTrack = Track("audit_trk_1", "Audit Song", "Audit Artist", "")
        val wasLiked = vm.ui.value.likedTracks.any { it.id == testTrack.id }
        vm.toggleLike(testTrack)
        assertEquals(!wasLiked, vm.ui.value.likedTracks.any { it.id == testTrack.id })
        // Restore state
        vm.toggleLike(testTrack)
        val isEvilValid = sanitizeRemoteArtworkUrl("https://evil.test/a.jpg").isNotEmpty()
        assertFalse("Evil url must be rejected", isEvilValid)
    }
}
