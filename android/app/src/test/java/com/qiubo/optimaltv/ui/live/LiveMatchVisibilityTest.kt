package com.qiubo.optimaltv.ui.live

import com.qiubo.optimaltv.data.model.MatchItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class LiveMatchVisibilityTest {
    @Test
    fun finishedMatchesAreHiddenWhileLiveAndUpcomingRemainVisible() {
        fun match(id: String, status: String) = MatchItem(
            matchId = id,
            home = "甲队",
            away = "乙队",
            league = "英超",
            status = status,
        )
        val live = match("live", "live")
        val upcoming = match("upcoming", "upcoming")
        val finished = match("finished", "finished")

        assertEquals(
            listOf(live, upcoming),
            visibleLiveScheduleMatches(listOf(live, finished, upcoming)),
        )
    }

    @Test
    fun leagueFiltersApplyToUpcomingMatchesAndStillHideFinishedMatches() {
        fun match(
            id: String,
            league: String,
            home: String = "甲队",
            away: String = "乙队",
            status: String = "upcoming",
        ) = MatchItem(
            matchId = id,
            home = home,
            away = away,
            league = league,
            status = status,
        )

        val epl = match("epl", "英超")
        val eplFinished = match("epl-finished", "英超", status = "finished")
        val serieA = match("serie-a", "意甲")
        val important = match("important", "西甲", home = "皇家马德里")
        val championsLeague = match("champions-league", "欧冠淘汰赛")
        val europaLeague = match("europa-league", "欧联杯")
        val conferenceLeague = match("conference-league", "欧协联")
        val national = match("national", "世界杯")
        val all = listOf(
            epl, eplFinished, serieA, important,
            championsLeague, europaLeague, conferenceLeague, national,
        )

        assertEquals(listOf(epl), visibleLiveScheduleMatches(all, "英超"))
        assertEquals(listOf(serieA), visibleLiveScheduleMatches(all, "意甲"))
        assertEquals(listOf(important, national), visibleLiveScheduleMatches(all, "important"))
        assertEquals(
            listOf(championsLeague, europaLeague),
            visibleLiveScheduleMatches(all, "euro"),
        )
        assertEquals(listOf(national), visibleLiveScheduleMatches(all, "national"))
    }

    @Test
    fun beijingClockPromotesUpcomingButNeverFinishesOrOverridesHeldStates() {
        val beijing = TimeZone.getTimeZone("Asia/Shanghai")
        val start = Calendar.getInstance(beijing).apply {
            clear()
            set(2026, Calendar.DECEMBER, 31, 23, 50, 0)
        }.timeInMillis
        val previousZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val kickoff = MatchItem(
                matchId = "year-boundary",
                home = "甲队",
                away = "乙队",
                league = "英超",
                date = "12-31",
                dateFull = "2026-12-31",
                time = "23:50",
                status = "upcoming",
            )
            assertEquals(start, matchStartMillis(kickoff, start + 20 * 60_000L))
            assertEquals("live", MatchClockPolicy.project(kickoff, start, start + 149 * 60_000L).status)
            assertEquals("upcoming", MatchClockPolicy.project(kickoff, start, start + 150 * 60_000L).status)
            assertEquals("live", MatchClockPolicy.project(kickoff.copy(status = "live"), start, start + 180 * 60_000L).status)
            assertEquals("finished", MatchClockPolicy.project(kickoff.copy(status = "finished"), start, start + 20 * 60_000L).status)
            val postponed = kickoff.copy(status = "postponed")
            assertEquals("postponed", MatchClockPolicy.project(postponed, start, start + 240 * 60_000L).status)
            assertEquals("延期", postponed.displayStatus)
            assertEquals("upcoming", MatchClockPolicy.project(kickoff.copy(status = "live"), start, start - 60_000L).status)
        } finally {
            TimeZone.setDefault(previousZone)
        }
    }

    @Test
    fun staleSnapshotCannotReplaceAMatchListAlreadyReturnedByFreshRequest() {
        val cached = listOf(MatchItem("cached", "旧主队", "旧客队", league = "英超"))
        val fresh = LiveUiState(
            loading = false,
            matches = listOf(MatchItem("fresh", "新主队", "新客队")),
            refreshFinished = true,
            freshData = true,
        )

        assertNull(snapshotFallbackState(fresh, cached))
        val restored = snapshotFallbackState(LiveUiState(), cached)
        assertEquals(cached, restored?.matches)
        assertFalse(restored?.refreshFinished ?: true)
        assertFalse(restored?.freshData ?: true)
    }

    @Test
    fun firstSilentLoadFailureLeavesLoadingStateAndShowsTheError() {
        val failed = liveMatchFailureState(LiveUiState(), "赛程接口不可用")

        assertFalse(failed.loading)
        assertTrue(failed.matches.isEmpty())
        assertEquals("赛程接口不可用", failed.error)
    }

    @Test
    fun refreshFailureKeepsPreviouslyLoadedMatches() {
        val previous = MatchItem("live", "甲队", "乙队", league = "英超")
        val failed = liveMatchFailureState(
            LiveUiState(loading = false, matches = listOf(previous), freshData = true),
            "断网",
        )

        assertFalse(failed.loading)
        assertEquals(listOf(previous), failed.matches)
        assertNull(failed.error)
        assertTrue(failed.refreshFinished)
        assertTrue(failed.freshData)
    }

    @Test
    fun coldSnapshotIsNotTreatedAsFreshForVoiceResolution() {
        val cached = MatchItem("snapshot", "国际米兰", "罗马", league = "意甲", status = "upcoming")
        val restored = snapshotFallbackState(LiveUiState(), listOf(cached))

        assertFalse(restored?.refreshFinished ?: true)
        assertFalse(restored?.freshData ?: true)

        val failed = liveMatchFailureState(restored!!, "断网")
        assertTrue(failed.refreshFinished)
        assertFalse(failed.freshData)
    }

    @Test
    fun failedFetchIsRetriedTenSecondsAfterItsCompletion() {
        assertFalse(liveRefreshDue(lastAttemptAt = 1_000L, now = 10_499L))
        assertTrue(liveRefreshDue(lastAttemptAt = 1_000L, now = 10_500L))
    }
}
