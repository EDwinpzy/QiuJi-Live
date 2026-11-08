package com.qiubo.optimaltv.ui.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qiubo.optimaltv.Graph
import com.qiubo.optimaltv.data.model.MatchItem
import com.qiubo.optimaltv.ui.components.FocusRegistry
import com.qiubo.optimaltv.ui.components.NAV_DOWN
import com.qiubo.optimaltv.ui.components.NAV_LEFT
import com.qiubo.optimaltv.ui.components.NAV_RIGHT
import com.qiubo.optimaltv.ui.components.ProvideNavScrolls
import com.qiubo.optimaltv.ui.components.ReturnFocus
import com.qiubo.optimaltv.ui.components.dpadFocusable
import com.qiubo.optimaltv.ui.components.lazyNavContainer
import com.qiubo.optimaltv.ui.theme.OtvColors
import com.qiubo.optimaltv.ui.theme.sx
import com.qiubo.optimaltv.ui.theme.sxs
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HomeHeroHeight = 574f
private const val HomeHeroArtHeight = 484f
private const val HomeHeroInset = 84f
private const val HomeFilterInset = 84f
private const val HomeFilterHeight = 82f
private const val HomeCardHeight = 194f

private val HomeBg = Color(0xFF05060B)
private val HomeHeroNavy = Color(0xFF070A16)
private val HomePurple = Color(0xFF7438EE)
private val HomePurpleBright = Color(0xFFB678FF)
private val HomePurpleDeep = Color(0xFF3A1496)
private val HomeCard = Color(0xF2111522)
private val HomeCardBorder = Color(0xFF292D3E)

/** Frosted purple glass used by the selected filter and focused match card. */
private fun DrawScope.drawGlassPanel(
    radiusPx: Float,
    scale: Float,
    glowAlpha: Float,
    borderColor: Color,
    borderWidth: Float,
    fillColors: List<Color>,
    sheenAlpha: Float,
) {
    val corner = CornerRadius(radiusPx, radiusPx)
    if (glowAlpha > 0f) {
        drawRoundRect(
            color = HomePurple.copy(alpha = 0.16f * glowAlpha),
            cornerRadius = corner,
            style = Stroke(width = 20f.sx(scale).toPx()),
        )
        drawRoundRect(
            color = HomePurpleBright.copy(alpha = 0.20f * glowAlpha),
            cornerRadius = corner,
            style = Stroke(width = 8f.sx(scale).toPx()),
        )
    }
    drawRoundRect(
        brush = Brush.verticalGradient(fillColors),
        cornerRadius = corner,
    )
    if (sheenAlpha > 0f) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = sheenAlpha),
                0.38f to Color.White.copy(alpha = sheenAlpha * 0.18f),
                0.62f to Color.Transparent,
            ),
            cornerRadius = corner,
        )
    }
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(
                borderColor.copy(alpha = 0.94f),
                borderColor.copy(alpha = 0.66f),
                borderColor.copy(alpha = 0.38f),
            ),
        ),
        cornerRadius = corner,
        style = Stroke(width = borderWidth),
    )
    val inset = 2.2f.sx(scale).toPx()
    if (size.width > inset * 2f && size.height > inset * 2f) {
        drawRoundRect(
            color = Color.White.copy(alpha = 0.10f),
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2f, size.height - inset * 2f),
            cornerRadius = CornerRadius((radiusPx - inset).coerceAtLeast(1f)),
            style = Stroke(width = 1f.sx(scale).toPx()),
        )
    }
}

/** Full-screen ambient background used by the redesign. */
@Composable
internal fun HomeBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                0f to HomeBg,
                0.48f to Color(0xFF070A15),
                0.76f to Color(0xFF070B10),
                1f to Color(0xFF040907),
            ),
        )
        val grassTop = size.height * 0.80f
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.24f to Color(0xFF0E3423).copy(alpha = 0.34f),
                1f to Color(0xFF05110A),
            ),
            topLeft = androidx.compose.ui.geometry.Offset(0f, grassTop),
            size = androidx.compose.ui.geometry.Size(size.width, size.height - grassTop),
        )
        val stripe = size.width / 8f
        repeat(8) { index ->
            if (index % 2 == 0) {
                drawRect(
                    color = Color(0xFF0D482E).copy(alpha = 0.08f),
                    topLeft = androidx.compose.ui.geometry.Offset(stripe * index, grassTop + 26f),
                    size = androidx.compose.ui.geometry.Size(stripe, size.height - grassTop),
                )
            }
        }
        drawLine(
            color = Color.White.copy(alpha = 0.035f),
            start = androidx.compose.ui.geometry.Offset(size.width / 2f, grassTop + 40f),
            end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height),
            strokeWidth = 2f,
        )
    }
}

@Composable
internal fun HomeHeroBand(
    hero: MatchItem?,
    heroes: List<MatchItem>,
    s: Float,
    heroBtnFocus: FocusRequester,
    backScope: CoroutineScope,
    listState: LazyListState,
    selectedFilter: String,
    onSelectFilter: (String) -> Unit,
    filterFocusRequesters: Map<String, FocusRequester>,
    onFocusFilters: () -> Unit,
    onHeroFocused: (Boolean) -> Unit,
    onPlay: (MatchItem) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(HomeHeroHeight.sx(s)),
    ) {
        HeroPosterCarousel(s)
        Box(
            Modifier
                .fillMaxWidth()
                .height(HomeHeroHeight.sx(s))
                .background(
                    Brush.verticalGradient(
                        0.78f to Color.Transparent,
                        0.90f to Color(0x99070A16),
                        1f to Color(0xFF070A16),
                    ),
                ),
        )

        hero?.let { current ->
            key(current.matchId) {
                Column(
                    Modifier
                        .padding(start = HomeHeroInset.sx(s), top = 62f.sx(s))
                        .width(760f.sx(s)),
                ) {
                    Column(
                        Modifier.clearAndSetSemantics {
                            contentDescription = heroAccessibilityText(current)
                        },
                    ) {
                        HeroTeamLine(
                            team = current.home,
                            score = current.homeScore.takeIf { current.hasScore },
                            s = s,
                        )
                        HeroTeamLine(
                            team = current.away,
                            score = current.awayScore.takeIf { current.hasScore },
                            s = s,
                        )
                        Text(
                            homeHeroMeta(current),
                            color = OtvColors.TextPrimary.copy(alpha = 0.92f),
                            fontSize = 23f.sxs(s),
                            lineHeight = 28f.sxs(s),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 34f.sx(s)),
                        )
                    }
                    HomeWatchButton(
                        s = s,
                        heroBtnFocus = heroBtnFocus,
                        heroesCount = heroes.size,
                        onFocusFilters = onFocusFilters,
                        onHeroFocused = onHeroFocused,
                        onPlay = { onPlay(current) },
                        onPrev = onPrev,
                        onNext = onNext,
                        backScope = backScope,
                        listState = listState,
                        modifier = Modifier.padding(top = 42f.sx(s)),
                    )
                }
            }
        }

        LeagueFilterRail(
            selected = selectedFilter,
            onSelect = onSelectFilter,
            s = s,
            focusRequesters = filterFocusRequesters,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun HeroTeamLine(
    team: String,
    score: String?,
    s: Float,
) {
    Row(
        modifier = Modifier.height(86f.sx(s)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamCrest(team, 74f, s)
        Spacer(Modifier.width(22f.sx(s)))
        Text(
            team,
            color = OtvColors.PureWhite,
            fontSize = heroHeadlineSize(team, hasScore = score != null).sxs(s),
            lineHeight = 58f.sxs(s),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        score?.let {
            Spacer(Modifier.width(20f.sx(s)))
            Text(
                it,
                color = HomePurpleBright,
                fontSize = 39f.sxs(s),
                lineHeight = 52f.sxs(s),
                fontWeight = FontWeight.Black,
            )
        }
    }
}

private fun heroHeadlineSize(team: String, hasScore: Boolean): Float {
    val available = if (hasScore) 500f else 630f
    val units = team.trim().fold(0f) { total, ch ->
        total + when {
            ch.isWhitespace() -> 0.35f
            ch.code <= 0x7F -> 0.58f
            else -> 1f
        }
    }.coerceAtLeast(1f)
    return (available / (units * 1.3f)).coerceIn(24f, 48f)
}

@Composable
private fun HomeWatchButton(
    s: Float,
    heroBtnFocus: FocusRequester,
    heroesCount: Int,
    onFocusFilters: () -> Unit,
    onHeroFocused: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    backScope: CoroutineScope,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val radius = 23f.sx(s)
    Box(
        modifier = modifier
            .focusRequester(heroBtnFocus)
            .dpadFocusable(
                scaleFocused = 1f,
                drawRing = false,
                focusedBg = null,
                navOverride = { dir ->
                    when (dir) {
                        NAV_LEFT -> if (heroesCount > 1) {
                            onPrev()
                            true
                        } else {
                            false
                        }
                        NAV_RIGHT -> if (heroesCount > 1) {
                            onNext()
                            true
                        } else {
                            false
                        }
                        NAV_DOWN -> {
                            onFocusFilters()
                            true
                        }
                        else -> false
                    }
                },
                onFocusedChange = {
                    focused = it
                    onHeroFocused(it)
                    if (it) {
                        ReturnFocus.mark("live", "hero")
                        backScope.launch { listState.animateScrollToItem(0) }
                    }
                },
                contentDescription = "立即观看",
                onClick = onPlay,
            )
            .width(378f.sx(s))
            .height(86f.sx(s))
            .drawBehind {
                drawGlassPanel(
                    radiusPx = radius.toPx(),
                    scale = s,
                    glowAlpha = if (focused) 1f else 0.34f,
                    borderColor = if (focused) OtvColors.PureWhite else HomePurpleBright,
                    borderWidth = (if (focused) 2.8f else 2f).sx(s).toPx(),
                    fillColors = listOf(
                        Color(0xFF9A68FF),
                        Color(0xFF6734E7),
                        Color(0xFF4020AC),
                    ),
                    sheenAlpha = if (focused) 0.28f else 0.20f,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(34f.sx(s))) {
                val path = Path().apply {
                    moveTo(1f, 1f)
                    lineTo(size.width - 1f, size.height / 2f)
                    lineTo(1f, size.height - 1f)
                    close()
                }
                drawPath(path, OtvColors.PureWhite)
            }
            Spacer(Modifier.width(24f.sx(s)))
            Text(
                "立即观看",
                color = OtvColors.PureWhite,
                fontSize = 29f.sxs(s),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

@Composable
internal fun HeroPosterCarousel(s: Float) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val posterOrder = remember { HeroPosterResources.shuffled() }
    var cursor by remember { mutableStateOf(0) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(8_000L)
                cursor += 1
            }
        }
    }

    Crossfade(
        targetState = cursor,
        animationSpec = tween(650),
        label = "heroPosterCarousel",
        modifier = Modifier
            .fillMaxWidth()
            .height(HomeHeroArtHeight.sx(s)),
    ) { frame ->
        val poster = posterOrder[frame % posterOrder.size]
        AsyncImage(
            model = remember(context, poster) {
                coil.request.ImageRequest.Builder(context)
                    .data(poster)
                    .allowHardware(false)
                    .build()
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            onError = {
                com.qiubo.optimaltv.OtvLog.w("hero poster 加载失败: resource=$poster")
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun LeagueFilterRail(
    selected: String,
    onSelect: (String) -> Unit,
    s: Float,
    focusRequesters: Map<String, FocusRequester>,
    modifier: Modifier = Modifier,
) {
    val rowState = rememberLazyListState()
    val rowScroll = remember(rowState) { lazyNavContainer(rowState) }
    ProvideNavScrolls(horizontal = rowScroll) {
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(18f.sx(s)),
            contentPadding = PaddingValues(horizontal = HomeFilterInset.sx(s)),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .height(HomeFilterHeight.sx(s))
                .padding(vertical = 7f.sx(s)),
        ) {
            items(LEAGUE_FILTERS.size, key = { index -> "league-${LEAGUE_FILTERS[index].first}" }) { index ->
                val (key, label) = LEAGUE_FILTERS[index]
                val active = selected == key
                var focused by remember(key) { mutableStateOf(false) }
                val radius = (HomeFilterHeight - 14f).sx(s) / 2f
                Box(
                    modifier = Modifier
                        .focusRequester(focusRequesters.getValue(key))
                        .dpadFocusable(
                            scaleFocused = 1f,
                            drawRing = false,
                            focusedBg = null,
                            floating = true,
                            autoSelect = true,
                            autoSelectDelayMs = 420L,
                            onFocusedChange = { focused = it },
                            contentDescription = label,
                            onClick = { onSelect(key) },
                        )
                        .height((HomeFilterHeight - 14f).sx(s))
                        .drawBehind {
                            val emphasized = active || focused
                            if (emphasized) {
                                drawGlassPanel(
                                    radiusPx = radius.toPx(),
                                    scale = s,
                                    glowAlpha = if (focused) 1f else 0.62f,
                                    borderColor = if (focused) Color(0xFFE4D5FF) else HomePurpleBright,
                                    borderWidth = (if (focused) 2.4f else 1.8f).sx(s).toPx(),
                                    fillColors = listOf(
                                        Color(0xFFA677FF),
                                        Color(0xFF7440EC),
                                        Color(0xFF4C21B9),
                                    ),
                                    sheenAlpha = if (focused) 0.32f else 0.25f,
                                )
                            } else {
                                drawRoundRect(
                                    color = Color(0xF20E101A),
                                    cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
                                )
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.20f),
                                    cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
                                    style = Stroke(width = 1f.sx(s).toPx()),
                                )
                            }
                        }
                        .padding(horizontal = 45f.sx(s)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = OtvColors.PureWhite,
                        fontSize = 23f.sxs(s),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier
                            .clearAndSetSemantics { }
                            .graphicsLayer {
                                // 焦点亮度只在绘制层读取，避免每键重组整块 chip 文字。
                                alpha = if (active || focused) 1f else 0.84f
                            },
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeMatchCard(
    match: MatchItem,
    s: Float,
    onPlay: (MatchItem) -> Unit,
    cardReg: FocusRegistry? = null,
) {
    var focused by remember(match.matchId) { mutableStateOf(false) }
    val radius = 26f.sx(s)
    val radiusPx = with(LocalDensity.current) { radius.toPx() }
    Box(
        modifier = Modifier
            .width(TvLiveLayout.CardWidth.sx(s))
            .dpadFocusable(
                scaleFocused = 1f,
                drawRing = false,
                focusedBg = null,
                externalFocusRequester = cardReg?.fr(match.matchId),
                onFocusedChange = {
                    focused = it
                    if (it) ReturnFocus.mark("live", match.matchId)
                },
                contentDescription = cardAccessibilityText(match),
                onClick = { onPlay(match) },
            )
            .height(HomeCardHeight.sx(s))
            .drawBehind {
                if (focused) {
                    drawGlassPanel(
                        radiusPx = radiusPx,
                        scale = s,
                        glowAlpha = 1f,
                        borderColor = Color(0xFFCBAAFF),
                        borderWidth = 2.6f.sx(s).toPx(),
                        fillColors = listOf(
                            Color(0xE64C258B),
                            Color(0xE832175E),
                            Color(0xE622103E),
                        ),
                        sheenAlpha = 0.18f,
                    )
                } else {
                    drawRoundRect(
                        color = HomeCard,
                        cornerRadius = CornerRadius(radiusPx, radiusPx),
                    )
                    drawRoundRect(
                        color = HomeCardBorder,
                        cornerRadius = CornerRadius(radiusPx, radiusPx),
                        style = Stroke(width = 1.2f.sx(s).toPx()),
                    )
                }
            }
            .padding(horizontal = 18f.sx(s)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.fillMaxWidth().clearAndSetSemantics { },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HomeCardTeam(match.home, s)
            HomeCardCenter(match, s, Modifier.weight(1f))
            HomeCardTeam(match.away, s)
        }
    }
}

@Composable
private fun HomeCardTeam(team: String, s: Float) {
    val label = cardTeamLabel(team)
    val longestLine = label.lineSequence().maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1
    val fit = (102f / longestLine).coerceIn(13f, 21f)
    Column(
        modifier = Modifier.width(112f.sx(s)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TeamCrest(team, 58f, s)
        Spacer(Modifier.height(7f.sx(s)))
        Text(
            label,
            color = OtvColors.TextPrimary,
            fontSize = fit.sxs(s),
            lineHeight = 23f.sxs(s),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun cardTeamLabel(team: String): String {
    val name = team.trim()
    if (name.length <= 4 || name.any { it.isWhitespace() || it.code <= 0x7F }) return name
    val breakAt = (name.length + 1) / 2
    return name.take(breakAt) + "\n" + name.drop(breakAt)
}

@Composable
private fun HomeCardCenter(
    match: MatchItem,
    s: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (match.league.isNotBlank()) {
            LeagueBadge(match.league, 84f, s)
            Spacer(Modifier.height(8f.sx(s)))
        }
        when {
            match.isLive -> {
                val minute = buildString {
                    if (match.minute.isNotBlank()) append("${match.minute}'")
                }
                Row(
                    modifier = Modifier
                        .background(Color(0x33E52D4F), RoundedCornerShape(50))
                        .padding(horizontal = 10f.sx(s), vertical = 4f.sx(s)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(7f.sx(s))
                            .background(Color(0xFFFF4B68), CircleShape),
                    )
                    Spacer(Modifier.width(6f.sx(s)))
                    Text(
                        "LIVE",
                        color = OtvColors.PureWhite,
                        fontSize = 15f.sxs(s),
                        lineHeight = 18f.sxs(s),
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                    )
                    if (minute.isNotBlank()) {
                        Spacer(Modifier.width(7f.sx(s)))
                        Text(
                            minute,
                            color = OtvColors.PureWhite,
                            fontSize = 18f.sxs(s),
                            lineHeight = 21f.sxs(s),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (match.hasScore) {
                    Spacer(Modifier.height(4f.sx(s)))
                    HomeCardScore(match, s)
                }
            }
            match.isFinished -> {
                if (match.hasScore) {
                    HomeCardScore(match, s)
                } else {
                    Text(
                        "完场",
                        color = OtvColors.TextSecondary,
                        fontSize = 31f.sxs(s),
                        lineHeight = 36f.sxs(s),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            match.isClockHeld -> {
                Text(
                    match.displayStatus,
                    color = OtvColors.TextDisabled,
                    fontSize = 25f.sxs(s),
                    lineHeight = 30f.sxs(s),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (match.hasScore) {
                    Spacer(Modifier.height(4f.sx(s)))
                    HomeCardScore(match, s)
                }
                if (match.time.isNotBlank()) {
                    Spacer(Modifier.height(1f.sx(s)))
                    Text(
                        match.time,
                        color = OtvColors.TextPrimary.copy(alpha = 0.72f),
                        fontSize = 18f.sxs(s),
                        lineHeight = 22f.sxs(s),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            else -> {
                if (match.hasScore) {
                    HomeCardScore(match, s)
                } else {
                    Text(
                        "VS",
                        color = OtvColors.PureWhite,
                        fontSize = 31f.sxs(s),
                        lineHeight = 34f.sxs(s),
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                    )
                }
                if (match.time.isNotBlank()) {
                    Spacer(Modifier.height(2f.sx(s)))
                    Text(
                        match.time,
                        color = OtvColors.TextPrimary.copy(alpha = 0.86f),
                        fontSize = 21f.sxs(s),
                        lineHeight = 25f.sxs(s),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeCardScore(match: MatchItem, s: Float) {
    Text(
        "${match.homeScore} - ${match.awayScore}",
        color = OtvColors.PureWhite,
        fontSize = 28f.sxs(s),
        lineHeight = 32f.sxs(s),
        fontWeight = FontWeight.Black,
        maxLines = 1,
    )
}

@Composable
private fun LeagueBadge(league: String, size: Float, s: Float) {
    val logoRes = leagueBadgeResource(league)
    Box(
        modifier = Modifier
            .size(size.sx(s)),
        contentAlignment = Alignment.Center,
    ) {
        if (logoRes != null) {
            Image(
                painter = painterResource(logoRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12f.sx(s))),
            )
        } else {
            Text(
                leagueFallbackMark(league),
                color = OtvColors.PureWhite,
                fontSize = 22f.sxs(s),
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
    }
}

private fun leagueBadgeResource(league: String): Int? = when {
    league.contains("英超") -> com.qiubo.optimaltv.R.drawable.league_pl
    league.contains("西甲") -> com.qiubo.optimaltv.R.drawable.league_laliga
    league.contains("意甲") -> com.qiubo.optimaltv.R.drawable.league_seriea
    league.contains("德甲") -> com.qiubo.optimaltv.R.drawable.league_bundesliga
    league.contains("法甲") -> com.qiubo.optimaltv.R.drawable.league_ligue1
    league.contains("欧冠") -> com.qiubo.optimaltv.R.drawable.league_ucl
    league.contains("欧联") || league.contains("欧罗巴") -> com.qiubo.optimaltv.R.drawable.league_uel
    else -> null
}

private fun leagueFallbackMark(league: String): String =
    league.replace(Regex("第\\s*\\d+\\s*轮"), "")
        .replace("联赛", "")
        .trim()
        .take(2)
        .ifBlank { "赛" }

@Composable
private fun InlineLive(s: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8f.sx(s))
                .background(Color(0xFFE52D4F), CircleShape),
        )
        Spacer(Modifier.width(7f.sx(s)))
        Text(
            "LIVE",
            color = OtvColors.PureWhite,
            fontSize = 16f.sxs(s),
            lineHeight = 20f.sxs(s),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
        )
    }
}

@Composable
private fun TeamCrest(team: String, size: Float, s: Float) {
    Box(
        Modifier
            .size(size.sx(s))
            .padding(2f.sx(s)),
        contentAlignment = Alignment.Center,
    ) {
        var iconReady by remember(team) { mutableStateOf(false) }
        if (!iconReady) {
            Text(
                team.take(1),
                color = OtvColors.TextPrimary,
                fontSize = (size * 0.42f).sxs(s),
                fontWeight = FontWeight.Bold,
            )
        }
        AsyncImage(
            model = remember(team) { Graph.live.teamIconUrl(team) },
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                val width = state.result.drawable.intrinsicWidth
                iconReady = width !in 1..2
            },
            onError = { iconReady = false },
            modifier = Modifier
                .fillMaxSize()
                .padding(3f.sx(s)),
        )
    }
}

private val HeroPosterResources = listOf(
    com.qiubo.optimaltv.R.drawable.hero_official_pl,
    com.qiubo.optimaltv.R.drawable.hero_official_laliga,
    com.qiubo.optimaltv.R.drawable.hero_official_seriea,
    com.qiubo.optimaltv.R.drawable.hero_official_bundesliga,
    com.qiubo.optimaltv.R.drawable.hero_official_ligue1,
    com.qiubo.optimaltv.R.drawable.hero_official_ucl,
    com.qiubo.optimaltv.R.drawable.hero_official_uel,
    com.qiubo.optimaltv.R.drawable.hero_official_uecl,
    com.qiubo.optimaltv.R.drawable.hero_official_copa,
    com.qiubo.optimaltv.R.drawable.hero_official_nations,
    com.qiubo.optimaltv.R.drawable.hero_extra_man_city_chelsea,
    com.qiubo.optimaltv.R.drawable.hero_extra_liverpool_arsenal,
    com.qiubo.optimaltv.R.drawable.hero_extra_man_utd_chelsea,
    com.qiubo.optimaltv.R.drawable.hero_extra_juventus_roma,
    com.qiubo.optimaltv.R.drawable.hero_extra_inter_fiorentina,
    com.qiubo.optimaltv.R.drawable.hero_extra_real_atletico,
    com.qiubo.optimaltv.R.drawable.hero_extra_barcelona_real,
    com.qiubo.optimaltv.R.drawable.hero_extra_bayern_dortmund,
    com.qiubo.optimaltv.R.drawable.hero_extra_psg_marseille,
    com.qiubo.optimaltv.R.drawable.hero_extra_portugal_argentina,
)

private val HomeMetaRound = Regex("第\\s*\\d+\\s*轮")
private val HomeMetaFullDate = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
private val HomeMetaShortDate = Regex("^(\\d{2})-(\\d{2})$")

internal fun homeHeroMeta(match: MatchItem): String {
    val tokens = mutableListOf<String>()
    tokens += if (match.isLive) {
        buildString {
            append("直播中")
            if (match.minute.isNotBlank()) append("  ${match.minute}'")
        }
    } else {
        match.displayStatus
    }
    val plainLeague = match.league
        .replace(HomeMetaRound, "")
        .replace("联赛", "")
        .trim()
    if (plainLeague.isNotBlank()) tokens += plainLeague
    if (!match.isLive) {
        val date = homeMonthDay(match.date)
        if (date.isNotBlank()) tokens += date
        if (match.time.isNotBlank()) tokens += match.time
    }
    return tokens.filter(String::isNotBlank).joinToString("  ")
}

private fun heroAccessibilityText(match: MatchItem): String = buildString {
    append(match.home)
    if (match.hasScore) append(" ").append(match.homeScore)
    append(" 对 ")
    append(match.away)
    if (match.hasScore) append(" ").append(match.awayScore)
    append("，")
    append(homeHeroMeta(match))
}

private fun cardAccessibilityText(match: MatchItem): String = buildString {
    append(match.home)
    if (match.hasScore) append(" ").append(match.homeScore)
    append(" 对 ")
    append(match.away)
    if (match.hasScore) append(" ").append(match.awayScore)
    append("，")
    append(homeHeroMeta(match))
}

private fun homeMonthDay(value: String): String {
    val full = HomeMetaFullDate.find(value.trim())
    if (full != null) {
        return "${full.groupValues[2].toInt()}月${full.groupValues[3].toInt()}日"
    }
    val short = HomeMetaShortDate.find(value.trim()) ?: return value.trim()
    return "${short.groupValues[1].toInt()}月${short.groupValues[2].toInt()}日"
}
