package com.muradgalayev.brainbuddy.ui.activity

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.ui.sharedcomponents.AppSearchBar
import kotlinx.coroutines.delay

// ─── Dark mode helper ───
@Composable
private fun isDarkTheme(): Boolean =
    colorScheme.background.luminance() < 0.5f

// ─── Workspace palette (light/dark aware) ───
private object WsPalette {
    // Hero gradients – light (blue-based)
    val heroGradient0Light = listOf(Color(0xFFDCE8FF), Color(0xFFEDF3FF))
    val heroGradient1Light = listOf(Color(0xFFFFE0C8), Color(0xFFFFF0E6))
    val heroGradient2Light = listOf(Color(0xFFD4F0FF), Color(0xFFEEF8FF))
    // Hero gradients – dark (indigo-based)
    val heroGradient0Dark = listOf(Color(0xFF1A1A40), Color(0xFF151530))
    val heroGradient1Dark = listOf(Color(0xFF3A2820), Color(0xFF2A1E18))
    val heroGradient2Dark = listOf(Color(0xFF1A1E3A), Color(0xFF161830))
    // Hero accent dots (indigo-based primary)
    val heroDot0 = Color(0xFF6366F1)
    val heroDot1 = Color(0xFFFF9A5C)
    val heroDot2 = Color(0xFF8B8CF8)

    // Calendar card (indigo-purple)
    val calendarBgLight = Color(0xFFEEEDFF)
    val calendarBgDark = Color(0xFF1A1A35)
    val calendarIconBgLight = Color(0xFFE0DFFF)
    val calendarIconBgDark = Color(0xFF2A2650)
    val calendarAccent = Color(0xFF6366F1)

    // Pomodoro card
    val pomodoroBgLight = Color(0xFFFFF3EB)
    val pomodoroBgDark = Color(0xFF2A2018)
    val pomodoroAccentLight = Color(0xFFFF8A50)
    val pomodoroAccentDark = Color(0xFFFFAB80)
    val pomodoroTrackLight = Color(0xFFFFDCC8)
    val pomodoroTrackDark = Color(0xFF3A2E22)

    // Activity banner
    val bannerGradientLight = listOf(Color(0xFFE8FAE8), Color(0xFFF5FFF5))
    val bannerGradientDark = listOf(Color(0xFF1A2E1A), Color(0xFF162016))
    val bannerAccent = Color(0xFF4CAF50)
}

@Composable
fun WorkspaceOverviewTab(
    onNavigate: (String) -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel()
) {
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    val heroCards = listOf(
        HeroCardData(
            title = "3h 20m focused today",
            subtitle = "You're doing better than yesterday.",
            caption = "Statistics"
        ),
        HeroCardData(
            title = "Consistency beats intensity",
            subtitle = "Small progress every day still wins.",
            caption = "Motivation"
        ),
        HeroCardData(
            title = uiState.todayCompletedText,
            subtitle = "Your most productive time was today.",
            caption = "Insights"
        )
    )

    val filteredFeatures = features.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
                it.subtitle.contains(searchQuery, ignoreCase = true)
    }
    val showEmptySearch =
        isSearchActive && searchQuery.isNotBlank() && filteredFeatures.isEmpty()

    val pagerState = rememberPagerState(pageCount = { heroCards.size })

    LaunchedEffect(pagerState) {
        while (true) {
            delay(3500)
            val nextPage = (pagerState.currentPage + 1) % heroCards.size
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .animateContentSize(animationSpec = tween(300))
    ) {
        // ─── Header ───
        AnimatedContent(
            targetState = isSearchActive,
            transitionSpec = {
                (slideInHorizontally(tween(450)) { it / 3 } + fadeIn(tween(450))) togetherWith
                        (fadeOut(tween(250)) + slideOutHorizontally(tween(250)) { -it / 8 })
            },
            label = "search_transition"
        ) { searchActive ->
            if (searchActive) {
                AppSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        searchQuery = ""
                        isSearchActive = false
                    },
                    placeholderText = "Search activities..."
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Workspace",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    Surface(
                        shape = CircleShape,
                        color = colorScheme.surfaceContainer,
                        modifier = Modifier.size(42.dp)
                    ) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (showEmptySearch) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.surfaceContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "No results found",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Try a different keyword",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }
        } else {
            // ─── Hero Carousel ───
            HeroPagerCard(pagerState = pagerState, cards = heroCards)

            Spacer(modifier = Modifier.height(20.dp))

            // ─── Calendar Card ───
            filteredFeatures.firstOrNull { it.route == "calendar" }?.let { calendar ->
                FeatureCardItem(
                    feature = calendar,
                    isGrid = false,
                    onClick = { onNavigate(calendar.route) },
                    overrideStatus = uiState.nextMeetingText ?: "No upcoming meetings"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ─── Todo & Pomodoro Grid ───
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                filteredFeatures.firstOrNull { it.route == "todo" }?.let { todo ->
                    FeatureCardItem(
                        feature = todo,
                        isGrid = true,
                        onClick = { onNavigate(todo.route) },
                        modifier = Modifier.weight(1f),
                        overrideStatus = "${uiState.todoTasksLeft} tasks left",
                        todoProgress = uiState.todoProgress
                    )
                }
                filteredFeatures.firstOrNull { it.route == "pomodoro" }?.let { pomodoro ->
                    FeatureCardItem(
                        feature = pomodoro,
                        isGrid = true,
                        onClick = { onNavigate(pomodoro.route) },
                        modifier = Modifier.weight(1f),
                        overrideStatus = uiState.pomodoroStatusText,
                        pomodoroTimeText = uiState.pomodoroTimeText,
                        pomodoroProgress = uiState.pomodoroProgress
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── Activity Banner ───
            ActivityBannerCard()
        }
    }
}

// ─── Data classes ───

private data class FeatureCard(
    val title: String,
    val subtitle: String,
    val icon: Int,
    val route: String,
    val status: String? = null
)

private data class HeroCardData(
    val title: String,
    val subtitle: String,
    val caption: String
)

private val features = listOf(
    FeatureCard("Calendar", "Plan your schedule", R.drawable.ic_calendar, "calendar"),
    FeatureCard("To-Do", "Manage tasks", R.drawable.ic_todo, "todo"),
    FeatureCard("Pomodoro", "Focus timer", R.drawable.ic_timer, "pomodoro")
)

// ─── Hero Carousel ───

@Composable
private fun getHeroGradient(index: Int): Brush {
    val dark = isDarkTheme()
    val colors = when (index) {
        0 -> if (dark) WsPalette.heroGradient0Dark else WsPalette.heroGradient0Light
        1 -> if (dark) WsPalette.heroGradient1Dark else WsPalette.heroGradient1Light
        else -> if (dark) WsPalette.heroGradient2Dark else WsPalette.heroGradient2Light
    }
    return Brush.linearGradient(colors)
}

@Composable
private fun getHeroAccentColor(index: Int): Color = when (index) {
    0 -> WsPalette.heroDot0
    1 -> WsPalette.heroDot1
    else -> WsPalette.heroDot2
}

@Composable
private fun HeroPagerCard(
    pagerState: androidx.compose.foundation.pager.PagerState,
    cards: List<HeroCardData>
) {
    val dark = isDarkTheme()
    val accentColor = getHeroAccentColor(pagerState.currentPage)

    // Subtle glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "heroGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .drawBehind {
                drawRoundRect(
                    color = accentColor.copy(alpha = glowAlpha * 0.5f),
                    cornerRadius = CornerRadius(32.dp.toPx()),
                    topLeft = Offset(-4.dp.toPx(), 2.dp.toPx()),
                    size = Size(size.width + 8.dp.toPx(), size.height + 4.dp.toPx())
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(getHeroGradient(pagerState.currentPage))
                .padding(24.dp)
        ) {
            // Caption badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accentColor.copy(alpha = if (dark) 0.2f else 0.12f)
            ) {
                Text(
                    text = cards[pagerState.currentPage].caption.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = cards[page].title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        lineHeight = 30.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = cards[page].subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Pill-style indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                cards.forEachIndexed { index, _ ->
                    val selected = index == pagerState.currentPage
                    val indicatorWidth by animateDpAsState(
                        targetValue = if (selected) 24.dp else 8.dp,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                        label = "indicatorWidth"
                    )
                    val indicatorColor by animateColorAsState(
                        targetValue = if (selected) accentColor
                        else colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        animationSpec = tween(300),
                        label = "indicatorColor"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .width(indicatorWidth)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(indicatorColor)
                    )
                }
            }
        }
    }
}

// ─── Feature Cards ───

@Composable
private fun FeatureCardItem(
    feature: FeatureCard,
    isGrid: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overrideStatus: String? = null,
    todoProgress: Float? = null,
    pomodoroTimeText: String? = null,
    pomodoroProgress: Float? = null
) {
    val dark = isDarkTheme()
    val displayStatus = overrideStatus ?: feature.status

    // Todo uses app's primaryContainer (from task.md), others have custom palettes
    val containerColor = when (feature.route) {
        "calendar" -> if (dark) WsPalette.calendarBgDark else WsPalette.calendarBgLight
        "todo" -> colorScheme.primaryContainer
        "pomodoro" -> if (dark) WsPalette.pomodoroBgDark else WsPalette.pomodoroBgLight
        else -> colorScheme.surfaceContainer
    }

    val iconContainerColor = when (feature.route) {
        "calendar" -> if (dark) WsPalette.calendarIconBgDark else WsPalette.calendarIconBgLight
        "todo" -> colorScheme.primaryContainer.copy(alpha = 0.6f)
        else -> colorScheme.primaryContainer.copy(alpha = 0.5f)
    }

    val accentColor = when (feature.route) {
        "calendar" -> WsPalette.calendarAccent
        "pomodoro" -> if (dark) WsPalette.pomodoroAccentDark else WsPalette.pomodoroAccentLight
        else -> colorScheme.primary
    }

    Surface(
        modifier = if (isGrid) modifier.aspectRatio(1f) else modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        onClick = onClick,
        shadowElevation = if (dark) 0.dp else 1.dp,
        tonalElevation = 0.dp
    ) {
        if (isGrid) {
            // ─── Grid card (Todo / Pomodoro) ───
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (feature.route == "todo") {
                    var showProgress by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(4500)
                            showProgress = !showProgress
                        }
                    }
                    Box(
                        modifier = Modifier.size(52.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showProgress,
                            enter = fadeIn(tween(400)),
                            exit = fadeOut(tween(400))
                        ) {
                            CircularTaskProgress(
                                progress = todoProgress ?: 0f,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !showProgress,
                            enter = fadeIn(tween(400)),
                            exit = fadeOut(tween(400))
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = iconContainerColor,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        painter = painterResource(id = feature.icon),
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                } else if (feature.route == "pomodoro") {
                    PomodoroMiniTimer(
                        timeText = pomodoroTimeText ?: "25:00",
                        progress = pomodoroProgress ?: 0f,
                        accentColor = accentColor
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = iconContainerColor,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                painter = painterResource(id = feature.icon),
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Bottom text
                Column {
                    Text(
                        text = feature.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    if (displayStatus != null) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = displayStatus,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = feature.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // ─── List card (Calendar) ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = iconContainerColor,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(id = feature.icon),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = feature.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    if (displayStatus != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = displayStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = feature.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─── Pomodoro Mini Timer (circular arc) ───

@Composable
private fun PomodoroMiniTimer(
    timeText: String,
    progress: Float,
    accentColor: Color
) {
    val dark = isDarkTheme()
    val trackColor = if (dark) WsPalette.pomodoroTrackDark else WsPalette.pomodoroTrackLight
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "pomodoroArc"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(68.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(68.dp)) {
                val strokeWidth = 5.dp.toPx()
                val inset = strokeWidth / 2
                drawArc(
                    color = trackColor,
                    startAngle = -225f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accentColor,
                    startAngle = -225f,
                    sweepAngle = 270f * animatedProgress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                fontSize = 13.sp
            )
        }
    }
}

// ─── Activity Banner ───

@Composable
private fun ActivityBannerCard() {
    val dark = isDarkTheme()
    val gradientColors = if (dark) WsPalette.bannerGradientDark else WsPalette.bannerGradientLight

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        shadowElevation = if (dark) 0.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = WsPalette.bannerAccent.copy(alpha = if (dark) 0.15f else 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(text = "\u2B50", fontSize = 22.sp)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Great work today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "You completed 3 tasks and stayed focused for 3h.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

// ─── Circular Task Progress ───

@Composable
fun CircularTaskProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val color = when {
        progress >= 1f -> Color(0xFF4CAF50)
        progress > 0.5f -> Color(0xFF2196F3)
        else -> Color(0xFFFF9800)
    }
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "taskProgress"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 5.dp.toPx()
            val inset = strokeWidth / 2
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
