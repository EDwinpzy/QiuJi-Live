package com.qiubo.optimaltv.ui.player

import com.qiubo.optimaltv.playback.EnginePlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlayerScreenStateProjectionTest {
    @Test
    fun playbackTelemetryChangesDoNotChangeScreenProjection() {
        val base = PlayerUiState(playState = EnginePlayState.READY)
        val telemetryOnly = base.copy(
            isPlaying = true,
            positionMs = 123_000L,
            durationMs = 900_000L,
            bufferedAheadMs = 8_000L,
        )

        assertEquals(base.screenProjection(), telemetryOnly.screenProjection())
    }

    @Test
    fun visibleStateChangesStillReachTheScreen() {
        val base = PlayerUiState(playState = EnginePlayState.BUFFERING)

        assertNotEquals(base.screenProjection(), base.copy(playState = EnginePlayState.READY).screenProjection())
        assertNotEquals(base.screenProjection(), base.copy(srcPickerVisible = true).screenProjection())
        assertNotEquals(base.screenProjection(), base.copy(engineEpoch = 2).screenProjection())
    }
}
