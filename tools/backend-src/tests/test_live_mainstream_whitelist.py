"""严格足球赛事范围契约（2026-11-06）。

用户要求只保留主流五大联赛、欧冠/欧联、主流成年国家队和国足比赛。这里锁住 TV
客户端的筛选边界，防止后续把中超、亚冠、其他小联赛、国内杯赛、欧协/世俱杯、
青年队/女足或篮球重新放回列表，也防止“正在直播”绕过白名单。
"""
import json
import pathlib
import re
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[3]


class LiveMainstreamWhitelistTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.src = (
            ROOT
            / "android/app/src/main/java/com/qiubo/optimaltv/data/repo/LiveRepository.kt"
        ).read_text(encoding="utf-8")

    def _pattern(self, name):
        match = re.search(
            rf'private val {name} = Regex\("([^"]+)"\)',
            self.src,
        )
        self.assertIsNotNone(match, f"缺少 {name} 常量")
        return re.compile(match.group(1))

    def test_top_five_leagues_only(self):
        pattern = self._pattern("TOP_LEAGUE_RE")
        for league in ("英超", "西甲", "意甲", "德甲", "法甲"):
            self.assertTrue(pattern.search(league), f"{league} 应被保留")
        for league in ("中超", "荷甲", "葡超", "澳超", "沙特联"):
            self.assertFalse(pattern.search(league), f"{league} 应被剔除")
        self.assertNotIn("ASIA_CLUB_CUP_RE", self.src)
        self.assertNotIn("CHINA_COMP_RE", self.src)

    def test_only_champions_league_and_europa_league_are_club_cups(self):
        pattern = self._pattern("EURO_CLUB_CUP_RE")
        for league in ("欧冠淘汰赛", "欧洲冠军联赛", "欧联杯", "欧罗巴联赛"):
            self.assertTrue(pattern.search(league), f"{league} 应被保留")
        for league in ("欧协联", "欧会杯", "世俱杯", "亚冠精英赛", "欧国联"):
            self.assertFalse(pattern.search(league), f"{league} 应被剔除")

    def test_national_fixture_requires_mainstream_senior_teams_with_china_exception(self):
        self.assertIn("isStrictFootballMatch", self.src)
        self.assertIn("val homeTeam = normalizeNationalTeamName(h)", self.src)
        self.assertIn("val awayTeam = normalizeNationalTeamName(a)", self.src)
        self.assertIn("CHINA_NATIONAL_TEAM in nationalTeams", self.src)
        self.assertIn(
            "homeTeam == CHINA_NATIONAL_TEAM || awayTeam == CHINA_NATIONAL_TEAM",
            self.src,
        )
        self.assertIn("NON_SENIOR_RE.containsMatchIn(l)", self.src)
        self.assertIn("NON_SENIOR_RE.containsMatchIn(h)", self.src)
        self.assertIn("NON_SENIOR_RE.containsMatchIn(a)", self.src)
        self.assertIn("U-?", self.src)
        self.assertIn("女足", self.src)
        filters = json.loads(
            (
                ROOT / "android/app/src/main/assets/football_filters.json"
            ).read_text(encoding="utf-8")
        )
        self.assertIn("中国", filters["NATIONAL_TEAMS"])

    def test_live_bypass_and_all_football_fallback_are_removed(self):
        self.assertNotIn("liveFootballIds", self.src)
        self.assertNotIn("it.matchId in liveFootballIds", self.src)
        self.assertNotIn("主流白名单零命中 → 回退全部足球", self.src)
        self.assertNotIn("list.size} 场兜底", self.src)
        self.assertIn("val mainstream = list.filter { isMainstreamMatch(it) }", self.src)
        self.assertIn("emptyList()", self.src)
        self.assertIn("显示空列表", self.src)
        self.assertIn(
            ".filter { isMainstreamMatch(it) }.takeIf { it.isNotEmpty() }",
            self.src,
        )

    def test_non_football_is_still_filtered(self):
        self.assertIn("NON_FOOTBALL_RE.containsMatchIn", self.src)
        for marker in ("男篮", "女排", "NBA", "CBA", "菲NCAA", "斯诺克", "电子竞技"):
            self.assertIn(marker, self.src)

    def test_tv_filter_bar_removes_chinese_super_league(self):
        live = (
            ROOT
            / "android/app/src/main/java/com/qiubo/optimaltv/ui/live/LiveScreen.kt"
        ).read_text(encoding="utf-8")
        self.assertNotIn('"中超" to "中超"', live)
        self.assertIn('"euro" to "欧战"', live)
        self.assertIn('private val EURO_RE = Regex("欧冠|欧洲冠军联赛|欧联|欧罗巴")', live)


if __name__ == "__main__":
    unittest.main()
