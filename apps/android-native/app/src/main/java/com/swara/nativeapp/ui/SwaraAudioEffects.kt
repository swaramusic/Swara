package com.swara.nativeapp.ui

import androidx.media3.common.PlaybackParameters
import com.swara.nativeapp.model.BUILTIN_EQ_PRESETS
import com.swara.nativeapp.playback.SwaraPlaybackService
import kotlinx.coroutines.flow.update

fun SwaraViewModel.setSoundStyle(style: String) {
    val (rate, rev) =
        when (style) {
            "Sped up" -> 1.25f to 0
            "Slowed + reverb" -> 0.85f to 58
            "Nightcore" -> 1.35f to 15
            "Lo-fi" -> 0.94f to 38
            "8D" -> 1.00f to 65
            "Konser" -> 1.00f to 92
            "Bass Heavy" -> 1.00f to 10
            "Vaporwave" -> 0.78f to 75
            "Vokal / Podcast" -> 1.00f to 0
            else -> 1.00f to 0
        }
    controller?.setPlaybackParameters(PlaybackParameters(rate, rate))
    SwaraPlaybackService.applyRoomReverb(rev)
    SwaraPlaybackService.setSpatial8D(style == "8D")
    val styleEq =
        when (style) {
            "Lo-fi" -> listOf(4f, 3f, 0f, -2f, -4f)
            "Nightcore" -> listOf(2f, 1f, 0f, 3f, 5f)
            "8D" -> listOf(3f, 1f, -1f, 2f, 4f)
            "Konser" -> listOf(4f, 2f, 1f, 3f, 4f)
            "Bass Heavy" -> listOf(7f, 5f, 0f, 1f, 2f)
            "Vaporwave" -> listOf(4f, 3f, 0f, -2f, -3f)
            "Vokal / Podcast" -> listOf(-2f, 0f, 5f, 4f, 2f)
            else -> _ui.value.eqBands
        }
    SwaraPlaybackService.applyEqualizerBands(styleEq)
    _ui.update { it.copy(soundStyle = style, speed = rate, pitch = rate, reverbPct = rev) }
}

fun SwaraViewModel.setVinylRate(rate: Float) {
    val r = rate.coerceIn(0.6f, 1.6f)
    controller?.setPlaybackParameters(PlaybackParameters(r, r))
    _ui.update { it.copy(soundStyle = "Custom", speed = r, pitch = r) }
}

fun SwaraViewModel.setRoomReverb(percent: Int) {
    val p = percent.coerceIn(0, 100)
    SwaraPlaybackService.applyRoomReverb(p)
    _ui.update { it.copy(reverbPct = p) }
}

fun SwaraViewModel.resetSoundStyle() {
    setSoundStyle("Normal")
}

fun SwaraViewModel.setPlaybackParams(speed: Float, pitch: Float) {
    val s = speed.coerceIn(0.5f, 2.0f)
    val p = pitch.coerceIn(0.5f, 2.0f)
    controller?.setPlaybackParameters(PlaybackParameters(s, p))
    _ui.update { it.copy(soundStyle = "Custom", speed = s, pitch = p) }
}

fun SwaraViewModel.setPlayerVolume(vol: Float) {
    val v = vol.coerceIn(0f, 1f)
    controller?.volume = v
    _ui.update { it.copy(volume = v) }
}

fun SwaraViewModel.selectEqPreset(presetId: String) {
    val preset = BUILTIN_EQ_PRESETS.find { it.id == presetId } ?: return
    _ui.update { it.copy(activeEqId = preset.id, eqBands = preset.bandsDb) }
    SwaraPlaybackService.applyEqualizerBands(preset.bandsDb)
}

fun SwaraViewModel.setEqBand(index: Int, gainDb: Float) {
    val next = _ui.value.eqBands.mapIndexed { i, v -> if (i == index) gainDb else v }
    _ui.update { it.copy(activeEqId = "custom", eqBands = next) }
    SwaraPlaybackService.applyEqualizerBands(next)
}
