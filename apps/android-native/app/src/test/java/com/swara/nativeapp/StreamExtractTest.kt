package com.swara.nativeapp

import com.swara.nativeapp.data.YouTubeMusicClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamExtractTest {
    @Test
    fun resolvesAudioWithoutLogin() = runBlocking {
        val yt = YouTubeMusicClient()
        val res1 = yt.resolveAudioUrl("dQw4w9WgXcQ")
        println("dQw4w9WgXcQ -> $res1")
        assertTrue("dQw4w9WgXcQ failed: ${res1.exceptionOrNull()?.message}", res1.isSuccess)

        val res2 = yt.resolveAudioUrl("xTvyyoF_LZY")
        println("xTvyyoF_LZY -> $res2")
        assertTrue("xTvyyoF_LZY failed: ${res2.exceptionOrNull()?.message}", res2.isSuccess)
    }
}
