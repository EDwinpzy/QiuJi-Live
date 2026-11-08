package com.qiubo.optimaltv

import com.qiubo.optimaltv.data.model.MatchItem
import com.qiubo.optimaltv.ui.live.ImportantTeamPolicy
import com.qiubo.optimaltv.ui.live.MatchStatusKind
import com.qiubo.optimaltv.ui.live.TodayMatchPager
import com.qiubo.optimaltv.ui.live.TvLiveLayout
import com.qiubo.optimaltv.ui.live.cardLeagueLabel
import com.qiubo.optimaltv.ui.live.heroTeamNameFontSize
import com.qiubo.optimaltv.ui.live.matchStatusKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFlashPolicyTest {
    private fun match(id: Int, home: String = "甲队", away: String = "乙队", league: String = "英超") =
        MatchItem(id.toString(), home, away, league)

    @Test fun todayPagerFillsFirstRowBeforeSecond() {
        val five = TodayMatchPager.pages((1..5).map(::match))
        assertEquals(listOf(1, 2, 3, 4), five.first().rows[0].map { it.matchId.toInt() })
        assertEquals(listOf(5), five.first().rows[1].map { it.matchId.toInt() })
        assertEquals(listOf(4, 4), TodayMatchPager.pages((1..8).map(::match)).first().rows.map { it.size })
        assertEquals(2, TodayMatchPager.pages((1..9).map(::match)).size)
    }

    @Test fun tvGridFitsExactlyFourCardsAcrossTheCanvas() {
        assertEquals(420f, TvLiveLayout.CardWidth)
        assertEquals(
            TvLiveLayout.CanvasWidth,
            TvLiveLayout.HorizontalInset * 2 +
                TvLiveLayout.CardWidth * TvLiveLayout.Columns +
                TvLiveLayout.CardGap * (TvLiveLayout.Columns - 1),
        )
    }

    @Test fun cardLeagueLabelKeepsRoundButAvoidsLongEllipsis() {
        assertEquals("法甲\n第9轮", cardLeagueLabel("法甲联赛 第9轮"))
        assertEquals("英超\n第9轮", cardLeagueLabel("英超第9轮"))
        assertEquals("足球综合", cardLeagueLabel("足球综合"))
    }

    @Test fun heroTeamNamesShrinkBeforeTheyCanBeTruncated() {
        assertEquals(68f, heroTeamNameFontSize("国际米兰", hasScore = true))
        assertEquals(68f, heroTeamNameFontSize("AC米兰", hasScore = false))
        assertTrue(heroTeamNameFontSize("托特纳姆热刺足球俱乐部青年队", hasScore = true) < 68f)
    }

    @Test fun importantMeansPopularTeamsNotWholeLeague() {
        assertTrue(ImportantTeamPolicy.isImportant(match(1, "曼彻斯特联", "布莱顿")))
        assertTrue(ImportantTeamPolicy.isImportant(match(2, "皇家马德里", "赫塔费", "西甲")))
        assertFalse(ImportantTeamPolicy.isImportant(match(3, "上海申花", "北京国安", "中超")))
        assertFalse(ImportantTeamPolicy.isImportant(match(4, "布伦特福德", "伯恩利", "英超")))
    }

    @Test fun legacyEndedAndCanonicalFinishedStayFinishedOnCards() {
        val legacy = match(5).copy(status = "ended")
        val canonical = match(6).copy(status = "finished")
        assertEquals(MatchStatusKind.FINISHED, matchStatusKind(legacy))
        assertEquals(MatchStatusKind.FINISHED, matchStatusKind(canonical))
        assertFalse(legacy.isLive)
        assertFalse(canonical.isLive)
    }

    @Test fun finishedMatchWithoutScoreDoesNotBecomeUpcoming() {
        val finished = match(7).copy(status = "finished", time = "20:00")
        assertEquals(MatchStatusKind.FINISHED, matchStatusKind(finished))
        assertFalse(finished.hasScore)
    }
}
