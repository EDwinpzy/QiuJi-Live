package com.qiubo.optimaltv.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballCompetitionPolicyTest {
    private val nationalTeams = setOf(
        "中国",
        "日本",
        "韩国",
        "巴西",
        "阿根廷",
        "法国",
        "德国",
        "西班牙",
        "意大利",
        "英格兰",
        "葡萄牙",
        "荷兰",
        "比利时",
        "克罗地亚",
        "乌拉圭",
    )

    @Test
    fun keepsOnlyTopFiveLeaguesAndChampionsEuropaCups() {
        for (league in listOf("英超", "西甲", "意甲", "德甲", "法甲")) {
            assertTrue(league, eligible(league, "任意主队", "任意客队"))
        }
        assertTrue(eligible("欧冠淘汰赛", "本菲卡", "阿贾克斯"))
        assertTrue(eligible("欧联杯", "罗马", "勒沃库森"))

        for (league in listOf("澳超", "中超", "荷甲", "葡超", "沙特联", "足总杯")) {
            assertFalse(league, eligible(league, "甲队", "乙队"))
        }
        for (league in listOf("欧协联", "欧会杯", "世俱杯", "亚冠精英赛")) {
            assertFalse(league, eligible(league, "皇家马德里", "拜仁"))
        }
    }

    @Test
    fun keepsMainstreamNationalTeamFixturesWithChinaException() {
        assertTrue(eligible("世界杯", "阿根廷", "巴西"))
        assertTrue(eligible("世预赛", "中国男足", "日本"))
        assertTrue(eligible("欧国联", "法国", "德国"))
        assertTrue(eligible("国际友谊", "中国国家队", "日本队"))
        assertTrue(eligible("国际友谊", "中国", "泰国"))
        assertTrue(eligible("世预赛", "中国男子足球队", "越南"))
        assertTrue(eligible("国际友谊", "国足", "泰国"))

        assertFalse(eligible("世界杯", "圣马力诺", "安道尔"))
        assertFalse(eligible("世界杯", "阿根廷", "圣马力诺"))
        assertFalse(eligible("世预赛", "中国", "日本女足"))
        assertFalse(eligible("U20世界杯", "巴西", "阿根廷"))
        assertFalse(eligible("女足世界杯", "中国", "日本"))
        assertFalse(eligible("U20亚洲杯", "中国", "泰国"))
        assertFalse(eligible("女足亚洲杯", "中国", "泰国"))
        assertFalse(eligible("国际友谊", "中国香港", "泰国"))
        assertFalse(eligible("世界女排联赛", "中国", "日本"))
        assertFalse(eligible("NBA", "中国", "日本"))
    }

    @Test
    fun rejectsNonSeniorAndNonFootballMatches() {
        assertFalse(eligible("英超", "阿森纳女足", "切尔西女足"))
        assertFalse(eligible("英超U21", "阿森纳U21", "切尔西U21"))
        assertFalse(eligible("NBA", "湖人", "勇士"))
        assertFalse(eligible("亚运男篮", "中国", "日本"))
        assertFalse(eligible("欧协联", "皇家马德里", "拜仁"))
    }

    private fun eligible(league: String, home: String, away: String): Boolean =
        LiveRepository.isStrictFootballMatch(league, home, away, nationalTeams)
}
