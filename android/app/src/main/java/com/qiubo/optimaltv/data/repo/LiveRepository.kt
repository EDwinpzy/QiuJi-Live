package com.qiubo.optimaltv.data.repo

import com.qiubo.optimaltv.OtvLog
import com.qiubo.optimaltv.data.model.LiveStreamSource
import com.qiubo.optimaltv.data.model.MatchChannel
import com.qiubo.optimaltv.data.model.MatchItem
import com.qiubo.optimaltv.data.prefs.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 球迹直播仓库（1:1 复刻原版 web 数据链路，同一后端）：
 * - matches: /api/matches（90 场左右，含 live 比分）
 * - 直播信号: /api/stream/{matchId}?src=all&fresh=1 → 仅返回已解析并探活的 HLS 线路
 */
class LiveRepository(
    private val settings: SettingsStore,
    private val okHttp: OkHttpClient,
    private val appContext: android.content.Context,
) {

    companion object {
        /** 直播源由服务端提供对应 Referer，供中继播放清单及分片。 */
        fun relayUrl(base: String, raw: String, referer: String = "https://www.88zhibo.tv/"): String =
            "$base/api/relay?u=" + URLEncoder.encode(raw, "UTF-8") + "&ref=" + URLEncoder.encode(referer, "UTF-8")

        internal fun matchesRequestPath(fresh: Boolean = true): String =
            if (fresh) "/api/matches?fresh=1" else "/api/matches"

        internal fun matchesArray(response: JSONObject): org.json.JSONArray {
            val error = if (response.isNull("error")) "" else response.optString("error").trim()
            check(error.isEmpty() || error == "null") { "赛程接口返回错误：$error" }
            return response.optJSONArray("matches")
                ?: throw IllegalStateException("赛程接口未返回比赛列表")
        }

        /** 后端比分字段是 home/away；旧快照/旧接口曾用 h/a，两者都兼容。 */
        internal fun scoreText(score: JSONObject?, vararg keys: String): String {
            if (score == null) return ""
            for (key in keys) {
                if (score.isNull(key)) continue
                val value = score.optString(key).takeUnless { it == "null" }?.trim().orEmpty()
                if (value.isNotEmpty()) return value
            }
            return ""
        }

        private const val TAG = "OTV"
        private val TEAM_SPACE_RE = Regex("\\s+")
        private val TEAM_COMPETITION_PREFIX_RE = Regex(
            "^(英超|西甲|意甲|德甲|法甲|中超|欧冠|欧联|欧协|欧国联|世预赛|亚冠|足协杯|英联杯|足总杯|德乙|英冠|荷甲|葡超|比甲|苏超|日职|韩K|沙特联|巴甲|阿甲|美职足)[\\s\\S]*?第?[\\d一二三四五六七八九十]+[轮轮次回合]",
        )
        private val TEAM_SHORT_PREFIX_RE = Regex(
            "^(英超|西甲|意甲|德甲|法甲|中超|欧冠|欧联|欧协|欧国联|世预赛|亚冠)[\\s\\S]{0,6}?(?=[^\\x00-\\x7F])",
        )
        private val TEAM_BASE_SUFFIX_RE = Regex("(女篮|男篮|女足|男足|B队|二队|U\\d+|青年队)$")

        /** 原版 hero 重要度：live+1000 / 有比分+100 / 五大联赛+50 / 欧战与国赛+40。 */
        private val TOP_LEAGUE_RE = Regex("^(英超|西甲|意甲|德甲|法甲)")
        private val NAT_RE = Regex("欧冠|欧联|欧罗巴|欧国联|世预赛|欧预赛|欧洲杯|亚洲杯|美洲杯|世界杯|友谊赛|国家队")

        /**
         * 严格赛事白名单（2026-11-06 用户需求）：
         * 仅保留五大联赛、欧冠/欧联、双方均为主流成年国家队的比赛，以及国足比赛。
         * 中超、亚冠、荷甲/葡超、各国杯赛、欧协/世俱杯和青年/女足赛事全部剔除。
         */
        private val NON_FOOTBALL_RE = Regex(
            "菲NCAA|男篮|女篮|篮球|排球|女排|男排|网球|羽毛球|乒乓球|棒球|冰球|橄榄球|手球|" +
                "拳击|搏击|综合格斗|斯诺克|台球|高尔夫|赛车|F1|电竞|电子竞技|" +
                "琼斯杯|NBL|NBA|CBA|WNBA|MLB|NFL|NHL|UFC|ATP|WTA|" +
                "田径|游泳|体操|跳水|举重|摔跤|柔道|跆拳道|水球|曲棍球",
            RegexOption.IGNORE_CASE,
        )
        /** 用户只要求欧冠、欧联；欧协/欧会/欧超/世俱不在俱乐部杯赛白名单。 */
        private val EURO_CLUB_CUP_RE = Regex("^(欧冠|欧洲冠军联赛|欧联|欧罗巴)")
        /** 青年、女足、梯队和奥运序列不属于主流成年国家队/俱乐部赛事。 */
        private val NON_SENIOR_RE = Regex(
            "女足|女子|女青|女队|女奥|U-?\\d{1,2}|青年|国奥|国青|国少|" +
                "奥运|亚运|大运|预备队|后备队|二队|B队|青年队",
            RegexOption.IGNORE_CASE,
        )
        private val NATIONAL_TEAM_SUFFIX_RE = Regex(
            "(?:国家男子足球队|国家足球队|男子足球队|国家男子队|男子国家队|" +
                "足球队|国家队|男足队|男子队|男足|男队|队)$",
        )
        /** 上游偶尔只给通用足球分类；双方都在五大联赛球队表时仍可保留。 */
        private val GENERIC_LEAGUE_LABELS = setOf("足球综合", "足球", "综合", "其他")
        /** 国足没有对手白名单限制：中国成年男足对任意成年国家队都保留。 */
        private const val CHINA_NATIONAL_TEAM = "中国"

        /**
         * 纯筛选规则，便于单测和快照复用。nationalTeams 需为已归一化的成年国家队集合。
         * 国家队比赛必须双方都在白名单中，不能只凭“世界杯/友谊赛”等赛事名放行；
         * 国足（中国成年男足）例外，对任意成年国家队都保留。
         */
        internal fun isStrictFootballMatch(
            league: String,
            home: String,
            away: String,
            nationalTeams: Set<String>,
            top5Teams: Set<String> = emptySet(),
        ): Boolean {
            val l = league.trim()
            val h = home.trim()
            val a = away.trim()
            if (l.isEmpty() || h.isEmpty() || a.isEmpty()) return false
            if (NON_FOOTBALL_RE.containsMatchIn(l) ||
                NON_FOOTBALL_RE.containsMatchIn(h) ||
                NON_FOOTBALL_RE.containsMatchIn(a)
            ) return false
            if (NON_SENIOR_RE.containsMatchIn(l) ||
                NON_SENIOR_RE.containsMatchIn(h) ||
                NON_SENIOR_RE.containsMatchIn(a)
            ) return false
            if (TOP_LEAGUE_RE.containsMatchIn(l)) return true
            if (EURO_CLUB_CUP_RE.containsMatchIn(l)) return true
            if (l in GENERIC_LEAGUE_LABELS && h in top5Teams && a in top5Teams) return true
            val homeTeam = normalizeNationalTeamName(h)
            val awayTeam = normalizeNationalTeamName(a)
            if (
                CHINA_NATIONAL_TEAM in nationalTeams &&
                (homeTeam == CHINA_NATIONAL_TEAM || awayTeam == CHINA_NATIONAL_TEAM)
            ) return true
            return homeTeam in nationalTeams && awayTeam in nationalTeams
        }

        private fun normalizeNationalTeamName(value: String): String {
            var name = value.trim().replace(TEAM_SPACE_RE, "")
            while (true) {
                if (name == "国足" || name == "中国国足") return CHINA_NATIONAL_TEAM
                val next = name.replace(NATIONAL_TEAM_SUFFIX_RE, "")
                if (next == name) return name
                name = next
            }
        }
        fun heroImportance(m: MatchItem): Int {
            var s = 0
            if (m.isLive) s += 1000
            if (m.hasScore) s += 100
            if (TOP_LEAGUE_RE.containsMatchIn(m.league)) s += 50
            if (NAT_RE.containsMatchIn(m.league)) s += 40
            return s
        }
    }

    private var matchesCache: Pair<Long, List<MatchItem>>? = null
    /** 赛程只请求一次直连接口；代理重试曾让失败路径额外等待订阅刷新和原生核心启动。 */
    private val matchesHttp by lazy {
        okHttp.newBuilder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(50, java.util.concurrent.TimeUnit.SECONDS)
            .proxy(java.net.Proxy.NO_PROXY)
            .build()
    }

    /**
     * 足球源在本地后端解析，但超时不应继承搜索抓取的 75 秒宽限。
     *
     * 2026-10-17（用户报障「球迹直播经常找不到可用信号源，退出 app 重进又好了」）：
     * 旧版由 [okHttp] newBuilder 派生，**沿用同一个 Dispatcher**。生产请求走
     * `liveHttpResponse`（异步 enqueue），而 OkHttp 的 `maxRequestsPerHost`（默认 5）
     * 只约束异步调用；赛程与信号源请求打到同一个 127.0.0.1:8090，
     * 进足球页瞬间几路并发就能占满 5 个名额。更糟的是**排队期间 callTimeout 不生效**
     * （OkHttp 在 call 真正开始跑时才 enter 超时），信号源请求会在 ready 队列里无限期
     * 挂着——播放器停在「正在查找片源…」，用户退出重进（新会话、新队列）才恢复。
     * 实测证据：`LiveSourceDispatcherQueueTest` 用本地桩服务占满 5 个名额后，第 6 个
     * 异步请求 1.5s 内既无回调也未到达服务端；换独立 Dispatcher 后立刻 200。
     * 这里给信号源请求独立 Dispatcher：它永远不排在其它接口后面。
     */
    private val liveSourceHttp by lazy {
        okHttp.newBuilder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(40, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
            .proxy(java.net.Proxy.NO_PROXY)
            .dispatcher(
                okhttp3.Dispatcher().apply {
                    maxRequests = 24
                    maxRequestsPerHost = 24
                }
            )
            .build()
    }

    private suspend fun getLiveSource(base: String, path: String): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(base.trimEnd('/') + path)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 11) QiuJi-Live/1.0")
            .build()
        liveHttpResponse(liveSourceHttp, req).use { resp ->
            require(resp.isSuccessful) { "HTTP ${resp.code} $path" }
            JSONObject(resp.body?.string().orEmpty())
        }
    }

    private suspend fun getMatches(base: String, fresh: Boolean): org.json.JSONArray = withContext(Dispatchers.IO) {
        val path = matchesRequestPath(fresh)
        val req = Request.Builder()
            .url(base.trimEnd('/') + path)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 11) QiuJi-Live/1.0")
            .build()
        liveHttpResponse(matchesHttp, req).use { resp ->
            require(resp.isSuccessful) { "HTTP ${resp.code} $path" }
            val body = resp.body?.string().orEmpty()
            require(body.isNotBlank()) { "赛程接口返回空响应" }
            matchesArray(JSONObject(body))
        }
    }

    /** baseUrl 内存缓存（v1.19 流畅度）：UI 组合期 teamIconUrl 高频调用
     *  （足球页每张比赛卡 2 次队标），旧版每次 runBlocking 读 DataStore
     *  ——主线程组合期几十次阻塞等待，光标移动一顿一顿的直接来源。
     *  后台收集 DataStore flow：首发射加载 + 设置页变更即时刷新，语义与旧版一致。 */
    @Volatile private var cachedBase: String = "http://127.0.0.1:8090"

    /** 队标（原版 iconUrl 1:1：normTeamName 归一化 → 本地 574 队映射零请求，未命中回落后端接口） */
    private val teamIcons: Map<String, String> by lazy {
        runCatching {
            val json = appContext.assets.open("team_icons.json").bufferedReader().use { it.readText() }
            val o = JSONObject(json)
            buildMap { o.keys().forEach { k -> put(k, o.getString(k)) } }
        }.getOrDefault(emptyMap())
    }

    init {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                settings.settings.collect { cachedBase = it.liveApiBaseUrl.trim().trimEnd('/') }
            }
        }
        // 首次首页组合不再同步读 assets；映射在 IO 上预热，未完成时仍有首字兜底。
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            teamIcons
        }
    }

    private fun baseUrl(): String = cachedBase

    /** 内置后端就绪探测：app 内置 proxy 冷启动需几秒，首次拉数据前先轮询 /api/health。
     *  外置后端(非 127.0.0.1)或已连通则立即返回。 */
    private suspend fun waitBackendReady(base: String) {
        if (!base.contains("127.0.0.1") && !base.contains("localhost")) return
        repeat(30) {
            runCatching {
                val req = Request.Builder().url("$base/api/health").build()
                liveHttpResponse(liveSourceHttp, req).use { if (it.isSuccessful) return }
            }
            kotlinx.coroutines.delay(500)
        }
    }

    /** 严格赛事过滤表：只读取主流成年国家队集合，避免旧杯赛关键词继续扩大范围。 */
    private data class FootballFilters(
        val top5: Set<String>,
        val national: Set<String>,
    )

    private val filters: FootballFilters by lazy {
        runCatching {
            val o = org.json.JSONObject(appContext.assets.open("football_filters.json").bufferedReader().use { it.readText() })
            fun arr(k: String): Set<String> = buildSet {
                o.optJSONArray(k)?.let { a -> for (i in 0 until a.length()) add(a.getString(i)) }
            }
            FootballFilters(
                arr("TOP5_TEAMS"),
                arr("NATIONAL_TEAMS").mapTo(linkedSetOf()) { normalizeNationalTeamName(it) },
            )
        }.getOrDefault(FootballFilters(emptySet(), emptySet()))
    }

    /** 严格赛事判定：五大联赛、欧冠/欧联、主流成年国家队对阵，或国足比赛。 */
    fun isMainstreamMatch(m: MatchItem): Boolean =
        isStrictFootballMatch(m.league, m.home, m.away, filters.national, filters.top5)

    private fun todayTomorrowKeys(): Pair<String, String> {
        val zone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
        val fmt = java.text.SimpleDateFormat("MM-dd", java.util.Locale.CHINA).apply { timeZone = zone }
        val cal = java.util.Calendar.getInstance(zone)
        val today = fmt.format(cal.time)
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        return today to fmt.format(cal.time)
    }

    /** 比赛列表：fresh=true 等待服务端刷新；静默轮询用 fresh=false 立即取 SWR 缓存。 */
    suspend fun matches(force: Boolean = false, fresh: Boolean = true): List<MatchItem> = withContext(Dispatchers.IO) {
        matchesCache?.takeIf { !force && System.currentTimeMillis() - it.first < 30_000 }?.second?.let { return@withContext it }
        val base = baseUrl()
        waitBackendReady(base)
        val arr = getMatches(base, fresh)
        val (today, tomorrow) = todayTomorrowKeys()
        // optString 对 JSON null 会返回字面量 "null"（需求 足球#1「null'」根因之一）——统一消毒
        fun os(o: org.json.JSONObject, k: String): String =
            if (o.isNull(k)) "" else o.optString(k).takeUnless { it == "null" } ?: ""
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val sc = o.optJSONObject("score")
            // 列表频道只作目录元信息；播放页从 /api/stream?src=all 获取已探活线路。
            val chArr = o.optJSONArray("channels")
            val channels = (0 until (chArr?.length() ?: 0)).mapNotNull channel@ { ci ->
                val c = chArr?.optJSONObject(ci) ?: return@channel null
                fun cs(k: String): String = if (c.isNull(k)) "" else c.optString(k).takeUnless { it == "null" } ?: ""
                val src = cs("src")
                if (src.isBlank()) return@channel null
                MatchChannel(src = src, id = cs("id"), name = cs("name"))
            }
            MatchItem(
                matchId = os(o, "match_id"),
                home = os(o, "home"),
                away = os(o, "away"),
                league = os(o, "league"),
                leagueColor = os(o, "league_color"),
                date = os(o, "date"),
                dateFull = os(o, "date_full"),
                time = os(o, "time"),
                status = os(o, "status"),
                minute = os(o, "minute"),
                homeScore = scoreText(sc, "home", "h"),
                awayScore = scoreText(sc, "away", "a"),
                channels = channels,
            )
        }.filter {
            it.matchId.isNotBlank() && it.home.isNotBlank() && it.away.isNotBlank()
        }
        // 只展示通过严格赛事判定的比赛：今日/明日优先；没有今日/明日时展示全部合规赛事。
        // 不再让“正在直播”绕过白名单，也不再用全部足球兜底（避免澳超、杯赛、篮球泄漏）。
        val mainstream = list.filter { isMainstreamMatch(it) }
        fun matchDay(value: String): String = Regex("^(?:\\d{4}-)?(\\d{2}-\\d{2})$")
            .find(value.trim())?.groupValues?.get(1).orEmpty()
        val preferred = mainstream.filter {
            it.isLive || matchDay(it.date) == today || matchDay(it.date) == tomorrow
        }
        val finalList = when {
            preferred.isNotEmpty() -> preferred
            mainstream.isNotEmpty() -> {
                OtvLog.w("matches 今日/明日无合规主流赛事 → 展示全部合规赛事 ${mainstream.size} 场")
                mainstream
            }
            else -> {
                OtvLog.w("matches 严格赛事白名单零命中 → 显示空列表（不回退全部足球）")
                emptyList()
            }
        }
        matchesCache = System.currentTimeMillis() to finalList
        // v1.10 磁盘快照（低端机秒开）：下次启动立即恢复上屏，后台 refresh 替换
        writeMatchesSnapshot(finalList)
        finalList
    }

    // ---------- 比赛列表磁盘快照（v1.10 2026-08-31） ----------
    private fun snapshotFile(): java.io.File = java.io.File(appContext.filesDir, "matches_snapshot.json")

    /** 启动恢复上次比赛列表（null=无快照/损坏）；UI 立即上屏，后台刷新替换新数据 */
    fun matchesFromSnapshot(): List<MatchItem>? = runCatching {
        val f = snapshotFile()
        if (!f.exists() || f.length() == 0L) return null
        val arr = org.json.JSONArray(f.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val chArr = o.optJSONArray("chs")
            val channels = (0 until (chArr?.length() ?: 0)).mapNotNull channel@ { ci ->
                val c = chArr?.optJSONObject(ci) ?: return@channel null
                val src = c.optString("s")
                if (src.isBlank()) return@channel null
                MatchChannel(src = src, id = c.optString("i"), name = c.optString("n"))
            }
            MatchItem(
                matchId = o.optString("id"), home = o.optString("h"), away = o.optString("a"),
                league = o.optString("l"), leagueColor = o.optString("lc"), date = o.optString("d"),
                time = o.optString("t"), status = o.optString("st"), minute = o.optString("m"),
                homeScore = o.optString("hs"), awayScore = o.optString("as"),
                channels = channels,
            ).takeIf { it.matchId.isNotBlank() }
        }.filter { isMainstreamMatch(it) }.takeIf { it.isNotEmpty() }?.also {
            // 快照同时写入内存缓存（60s TTL）：冷启动快照态点卡进直播时 srcsFor
            // 也能拿到动态 channels（否则回退硬编码源表）；后台 refresh 到货后自然替换
            matchesCache = System.currentTimeMillis() to it
        }
    }.getOrNull()

    private fun writeMatchesSnapshot(list: List<MatchItem>) {
        runCatching {
            val arr = org.json.JSONArray()
            list.forEach {
                arr.put(
                    org.json.JSONObject()
                        .put("id", it.matchId).put("h", it.home).put("a", it.away)
                        .put("l", it.league).put("lc", it.leagueColor).put("d", it.date)
                        .put("t", it.time).put("st", it.status).put("m", it.minute)
                        .put("hs", it.homeScore).put("as", it.awayScore)
                        .put("chs", org.json.JSONArray().apply {
                            it.channels.forEach { c -> put(org.json.JSONObject().put("s", c.src).put("i", c.id).put("n", c.name)) }
                        }),
                )
            }
            // 临时文件 + 原子改名：写一半中断不留半个快照
            val dst = snapshotFile()
            val tmp = java.io.File(dst.parentFile, dst.name + ".tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(dst)) {
                dst.delete()
                tmp.renameTo(dst)
            }
        }
    }

    /**
     * Get only server-resolved HLS lines that passed the upstream probe. The all-lines request is
     * intentionally made when opening every football player so stale match-list channels never
     * appear in the picker. Source order and labels come directly from the backend.
     */
    suspend fun liveStreamSources(matchId: String, fresh: Boolean = true): List<LiveStreamSource> =
        withContext(Dispatchers.IO) {
            val selectionJob = currentCoroutineContext()[Job]
            val startedAt = System.currentTimeMillis()
            runCatching {
                selectionJob?.let { if (!it.isActive) throw CancellationException("stream request cancelled") }
                val base = baseUrl()
                val o = getLiveSource(base, LiveStreamRequestPath.build(matchId, "all", fresh))
                selectionJob?.let { if (!it.isActive) throw CancellationException("stream request cancelled") }
                LiveStreamPayloadParser.available(o).map { payload ->
                    LiveStreamSource(
                        source = payload.source,
                        name = payload.name,
                        playbackUrl = relayUrl(base, payload.url, payload.referer),
                        referer = payload.referer,
                    )
                }
            }.onFailure {
                if (it is CancellationException) throw it
                // 带上耗时：排队超时（≈45s）与上游解析失败（几秒）的现场指标不同，
                // 「无可用信号源」再发生时一眼能分辨是本地排队还是源站问题。
                android.util.Log.w(
                    TAG,
                    "stream $matchId all 线路解析失败（${System.currentTimeMillis() - startedAt}ms）: ${it.message}",
                )
            }
                .getOrDefault(emptyList())
                .also {
                    android.util.Log.i(
                        TAG,
                        "stream $matchId all → ${it.size} 条（${System.currentTimeMillis() - startedAt}ms）",
                    )
                }
        }

    /** Resolve one previously listed source again, preserving its source-specific referer if the
     *  renewal response omits it. Dynamic source IDs are URL-encoded instead of allowlisted. */
    suspend fun refreshLiveStreamSource(
        matchId: String,
        source: String,
        fresh: Boolean = true,
        fallbackName: String = "",
        fallbackReferer: String = "",
    ): LiveStreamSource? = withContext(Dispatchers.IO) {
        if (source.isBlank()) return@withContext null
        runCatching {
            val base = baseUrl()
            val payload = LiveStreamPayloadParser.renewed(
                getLiveSource(base, LiveStreamRequestPath.build(matchId, source, fresh)),
                requestedSource = source,
                fallbackName = fallbackName,
                fallbackReferer = fallbackReferer,
            ) ?: return@withContext null
            LiveStreamSource(
                source = payload.source,
                name = payload.name,
                playbackUrl = relayUrl(base, payload.url, payload.referer),
                referer = payload.referer,
            )
        }.onFailure { android.util.Log.w(TAG, "renew stream $source 失败: ${it.message}") }.getOrNull()
    }

    /** 直播续签健康检查（v1.14）：当前 relay 清单还能否拉到（HTTP 200=活）。
     *  用于「签名已轮换但旧地址仍在服务期 → 延后切换」——上游轮换常有宽限期，
     *  白白热切换 = 每 ~9 分钟一次 ~300ms 可感知冻结（直播「偶尔卡顿」来源之一）。
     *  网络异常保守视为「活」（防误切；死地址随后由卡顿触发 kick 续签兜底）。 */
    suspend fun playlistAlive(relayUrl: String): Boolean = withContext(Dispatchers.IO) {
        if (relayUrl.isBlank()) return@withContext false
        runCatching {
            val fast = okHttp.newBuilder()
                .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(java.util.concurrent.TimeUnit.SECONDS.toMillis(8), java.util.concurrent.TimeUnit.MILLISECONDS)
                .proxy(java.net.Proxy.NO_PROXY)
                .build()
            liveHttpResponse(fast, Request.Builder().url(relayUrl).build()).use { resp ->
                val alive = resp.code == 200
                android.util.Log.i(TAG, "playlistAlive ${resp.code} → $alive")
                alive
            }
        }.getOrDefault(true)
    }

    private val teamIconUrlCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** normTeamName（原版：剥赛事/轮次前缀 + 空白归一） */
    fun normTeamName(name: String): String {
        var n = name.trim().replace(TEAM_SPACE_RE, "")
        // 赛事+轮次前缀（如「英超第18轮南安普顿」「欧冠1/4决赛阿森纳」）
        n = n.replace(TEAM_COMPETITION_PREFIX_RE, "")
        n = n.replace(TEAM_SHORT_PREFIX_RE, "")
        return n
    }

    fun teamIconUrl(team: String): String {
        val base = baseUrl()
        val cacheKey = base + "\u0000" + team
        teamIconUrlCache[cacheKey]?.let { return it }
        val norm = normTeamName(team)
        // 2026-08-28：本地映射表全部改为绝对 URL（api-sports/TDB CDN，国内可达），
        // 旧版相对路径 assets/teams/*.png 在内置后端 zip 里不存在 → 全量 404 → 队标大面积缺失
        val local = teamIcons[team] ?: teamIcons[norm] ?: teamIcons[baseTeamName(norm)]
        val url = if (local != null) {
            if (local.startsWith("http")) local
            else "$base/${local.removePrefix("./")}"
        } else "$base/api/team-icon-img?name=" + URLEncoder.encode(team, "UTF-8")
        teamIconUrlCache[cacheKey] = url
        return url
    }

    /** 别名兜底：去常见后缀再查一次 */
    private fun baseTeamName(n: String): String =
        n.replace(TEAM_BASE_SUFFIX_RE, "")

}
