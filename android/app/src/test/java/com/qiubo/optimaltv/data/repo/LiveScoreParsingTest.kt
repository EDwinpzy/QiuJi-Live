package com.qiubo.optimaltv.data.repo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiveScoreParsingTest {
    @Test
    fun readsBackendScoreContract() {
        val score = JSONObject("""{"home":3,"away":1}""")

        assertEquals("3", LiveRepository.scoreText(score, "home", "h"))
        assertEquals("1", LiveRepository.scoreText(score, "away", "a"))
    }

    @Test
    fun fallsBackToLegacySnapshotKeys() {
        val score = JSONObject("""{"h":"2","a":"0"}""")

        assertEquals("2", LiveRepository.scoreText(score, "home", "h"))
        assertEquals("0", LiveRepository.scoreText(score, "away", "a"))
    }

    @Test
    fun missingOrNullScoreIsEmpty() {
        assertEquals("", LiveRepository.scoreText(null, "home", "h"))
        assertEquals("", LiveRepository.scoreText(JSONObject("""{"home":null}"""), "home", "h"))
    }
}
