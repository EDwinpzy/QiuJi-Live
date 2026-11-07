package com.qiubo.optimaltv.voice

import com.qiubo.optimaltv.data.model.MatchItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceMatchSelectorTest {
    private fun match(
        id: String,
        home: String,
        away: String,
        status: String = "upcoming",
        league: String = "意甲第11轮",
        date: String = "11-07",
    ) = MatchItem(
        matchId = id,
        home = home,
        away = away,
        league = league,
        status = status,
        date = date,
    )

    @Test
    fun liveInterMatchOutranksAnyScheduledAlternative() {
        val live = match("inter-live", "国际米兰", "那不勒斯", status = "live")
        val later = match("inter-later", "国际米兰", "罗马", date = "11-14")

        assertEquals(live, VoiceMatchSelector.select(listOf(later, live), "国际米兰"))
    }

    @Test
    fun commonInterNicknameResolvesToTheSameFixture() {
        val inter = match("inter", "国际米兰", "罗马")

        assertEquals(inter, VoiceMatchSelector.select(listOf(inter), "国米"))
        assertEquals(inter, VoiceMatchSelector.select(listOf(inter), "帮我打开国米的比赛"))
    }

    @Test
    fun milanAloneDoesNotSelectInter() {
        val inter = match("inter", "国际米兰", "罗马")
        val acMilan = match("ac", "AC米兰", "尤文图斯")

        assertEquals(acMilan, VoiceMatchSelector.select(listOf(inter, acMilan), "米兰"))
    }

    @Test
    fun leagueAndFinishedFallbackAreHandled() {
        val finished = match("finished", "国际米兰", "罗马", status = "finished")
        val scheduled = match("scheduled", "都灵", "莱切")

        assertNull(VoiceMatchSelector.select(listOf(finished), "国际米兰"))
        assertEquals(scheduled, VoiceMatchSelector.select(listOf(finished, scheduled), "意甲"))
    }

    @Test
    fun resolvesOtherClubNamesAndPrefixedScheduleLabels() {
        val city = match("city-live", "曼彻斯特城", "阿森纳", status = "live", league = "英超第11轮")
        val newcastle =
            match(
                "newcastle-live",
                "英超第11轮纽卡斯尔联",
                "切尔西",
                status = "live",
                league = "英超第11轮",
            )

        assertEquals(city, VoiceMatchSelector.select(listOf(city), "曼城"))
        assertEquals(newcastle, VoiceMatchSelector.select(listOf(newcastle), "纽卡斯尔联"))
    }

    @Test
    fun resolvesNationalTeamAndChampionsLeagueRequests() {
        val national = match("national-live", "中国", "韩国", status = "live", league = "世预赛")
        val champions = match("ucl-live", "皇家马德里", "拜仁慕尼黑", status = "live", league = "欧冠联赛阶段")

        assertEquals(national, VoiceMatchSelector.select(listOf(national), "中国队"))
        assertEquals(champions, VoiceMatchSelector.select(listOf(champions), "欧冠"))
    }
}
