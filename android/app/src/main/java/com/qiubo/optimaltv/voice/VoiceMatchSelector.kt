package com.qiubo.optimaltv.voice

import com.qiubo.optimaltv.data.model.MatchItem
import com.qiubo.optimaltv.ui.live.ImportantTeamPolicy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// android/app/src/main/java/com/qiubo/optimaltv/voice/VoiceMatchSelector.kt

/**
 * Resolves a spoken team or league name to the best currently available fixture.
 * Live fixtures always outrank scheduled ones; finished fixtures are never returned.
 */
object VoiceMatchSelector {
    private val commandDecoration =
        Regex("(?:帮我|给我)?(?:打开|播放|放|看看|看|我要|我想|一下|足球|直播|比赛|赛事|的|这场)")
    private val edgeDecoration = Regex("^[\\s，,。.!！?？:：]+|[\\s，,。.!！?？:：]+$")

    fun select(matches: List<MatchItem>, rawQuery: String): MatchItem? {
        val query = normalize(rawQuery.replace(commandDecoration, ""))
        if (query.isEmpty()) return null

        val queryAliases = teamAliases(query)
        val today = dayKey(0)
        val tomorrow = dayKey(1)
        var best: MatchItem? = null
        var bestScore = Int.MIN_VALUE

        matches.asSequence()
            .filterNot { it.isFinished }
            .forEach { match ->
                val teamMatch = listOf(match.home, match.away).any { team ->
                    val normalizedTeam = teamKey(team)
                    queryAliases.any { alias ->
                        normalizedTeam == alias ||
                            (alias.length >= 3 && normalizedTeam.contains(alias))
                    }
                }
                val leagueMatch = normalize(match.league).contains(query)
                if (!teamMatch && !leagueMatch) return@forEach

                var score = if (teamMatch) 1_000 else 100
                if (match.isLive) score += 10_000
                when (normalize(match.date)) {
                    today -> score += 30
                    tomorrow -> score += 20
                }
                if (score > bestScore) {
                    best = match
                    bestScore = score
                }
            }
        return best
    }

    private fun teamAliases(query: String): Set<String> {
        val canonical = query.removeSuffix("队")
        ImportantTeamPolicy.aliasGroups
            .firstOrNull { group -> group.any { teamKey(it) == canonical } }
            ?.let { group -> return group.map(::teamKey).toSet() }
        return setOf(canonical)
    }

    private fun teamKey(value: String): String = normalize(value).removeSuffix("队")

    private fun normalize(value: String): String =
        edgeDecoration.replace(value.trim().lowercase(Locale.ROOT), "")
            .replace(Regex("[\\s\\p{P}\\p{S}]+"), "")

    private fun dayKey(offset: Int): String {
        val zone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
        val cal = java.util.Calendar.getInstance(zone)
        cal.add(java.util.Calendar.DAY_OF_MONTH, offset)
        return SimpleDateFormat("MM-dd", Locale.CHINA).apply {
            timeZone = zone
        }.format(Date(cal.timeInMillis))
    }
}
