package com.qiubo.optimaltv

import com.qiubo.optimaltv.playback.LiveWindowPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveWindowPolicyTest {
    @Test fun longSegmentsKeepOneSegmentPlusBoundedAdditionalReserve() {
        assertEquals(4_000L, LiveWindowPolicy.targetOffsetMs(32_400L, 5_400L))
        assertEquals(5_000L, LiveWindowPolicy.maxOffsetMs(32_400L, 5_400L))
        assertEquals(4_000L, LiveWindowPolicy.targetOffsetMs(30_000L, 2_000L))
    }

    @Test fun smallFootballWindowKeepsADeeperSafetyMargin() {
        assertEquals(3_600L, LiveWindowPolicy.targetOffsetMs(6_000L))
        assertEquals(4_000L, LiveWindowPolicy.targetOffsetMs(12_000L))
    }

    @Test fun shortWindowBufferProfileStaysReachable() {
        val sixSeconds = LiveWindowPolicy.bufferDurations(6_000L, 2_000L)
        assertEquals(3_100, sixSeconds.minMs)
        assertEquals(5_500, sixSeconds.maxMs)
        assertEquals(1_500, sixSeconds.playMs)
        assertEquals(1_500, sixSeconds.rebufferMs)

        val twelveSeconds = LiveWindowPolicy.bufferDurations(12_000L, 2_000L)
        assertEquals(3_500, twelveSeconds.minMs)
        assertEquals(6_000, twelveSeconds.maxMs)
    }

    @Test fun unknownOrLargeWindowUsesLowLatencyTarget() {
        assertEquals(3_000L, LiveWindowPolicy.targetOffsetMs(0L))
        assertEquals(4_000L, LiveWindowPolicy.targetOffsetMs(30_000L))
        assertEquals(5_000L, LiveWindowPolicy.maxOffsetMs(30_000L))
    }
}
