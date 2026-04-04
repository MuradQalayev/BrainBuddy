package com.muradgalayev.brainbuddy.ui.activity

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.res.painterResource
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.ui.searchbar.AppSearchBar
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
private fun getHeroGradient(index: Int): Brush {
    return when (index) {
        0 -> Brush.linearGradient(
            colors = listOf(
                Color(0xFFE9E4FF),
                Color(0xFFF6F4FF)
            )
        )
        1 -> Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFE7D6),
                Color(0xFFFFF3EC)
            )
        )
        else -> Brush.linearGradient(
            colors = listOf(
                Color(0xFFDFF4FF),
                Color(0xFFF3FAFF)
            )
        )
    }
}
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
    FeatureCard(
        title = "Calendar",
        subtitle = "Plan your schedule",
        icon = R.drawable.ic_calendar,
        route = "calendar",
        status = null
    ),
    FeatureCard(
        title = "To-Do",
        subtitle = "Manage tasks",
        icon = R.drawable.ic_todo,
        route = "todo",
        status = null
    ),
    FeatureCard(
        title = "Pomodoro",
        subtitle = "Focus timer",
        icon = R.drawable.ic_timer,
        route = "pomodoro"
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkspaceScreen(
    onNavigate: (String) -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel()
) {
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    val heroCards = listOf(
        HeroCardData(
            title = "3h 20m focused today", // (we can make dynamic later)
            subtitle = "You’re doing better than yesterday.",
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

    val pagerState = rememberPagerState(pageCount = { heroCards.size })
//    val scope = rememberCoroutineScope()

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
        if (isSearchActive) {
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
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = { isSearchActive = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        HeroPagerCard(
            pagerState = pagerState,
            cards = heroCards
        )

        Spacer(modifier = Modifier.height(16.dp))

        filteredFeatures.firstOrNull { it.route == "calendar" }?.let { calendar ->
            FeatureCardItem(
                feature = calendar,
                isGrid = false,
                onClick = { onNavigate(calendar.route) },
                overrideStatus = uiState.nextMeetingText ?: "No upcoming meetings"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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

        Spacer(modifier = Modifier.height(16.dp))

        ActivityBannerCard()
    }
}
@Composable
private fun ActivityBannerCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = "Great work today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "You completed 3 tasks and stayed focused for 3h.",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}
@Composable
private fun HeroPagerCard(
    pagerState: androidx.compose.foundation.pager.PagerState,
    cards: List<HeroCardData>
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(getHeroGradient(pagerState.currentPage))
                .padding(20.dp)
        ) {
            Text(
                text = cards[pagerState.currentPage].caption,
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = cards[page].title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = cards[page].subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                cards.forEachIndexed { index, _ ->
                    val selected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(if (selected) 10.dp else 8.dp)
                            .background(
                                color = if (selected) {
                                    colorScheme.primary
                                } else {
                                    colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                },
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}
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
){
    val displayStatus = overrideStatus ?: feature.status
    val containerColor = when (feature.route) {
        "calendar" -> Color(0xFFF3F0FF)
        "todo" -> Color(0xFFEFF6FF)
        else -> colorScheme.surfaceContainer
    }

    val iconContainerColor = when (feature.route) {
        "calendar" -> Color(0xFFE7DEFF)
        "todo" -> Color(0xFFDCEEFF)
        else -> colorScheme.primaryContainer.copy(alpha = 0.5f)
    }

    Surface(
        modifier = if (isGrid) {
            modifier.aspectRatio(1f)
        } else {
            modifier.fillMaxWidth()
        },
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        onClick = onClick
    ) {
        if (isGrid) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
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
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showProgress,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            CircularTaskProgress(
                                progress = todoProgress ?: 0f,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = !showProgress,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Icon(
                                painter = painterResource(id = feature.icon),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                } else if (feature.route == "pomodoro") {

                    Column(
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = pomodoroTimeText ?: "25:00",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .width(52.dp)
                                .height(4.dp)
                                .background(
                                    color = colorScheme.onSurface.copy(alpha = 0.10f),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(pomodoroProgress ?: 0f)
                                    .height(4.dp)
                                    .background(
                                        color = colorScheme.primary,
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }

                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = iconContainerColor,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                painter = painterResource(id = feature.icon),
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
                Column() {

                    // Title
                    Text(
                        text = feature.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )

                    // Status (NEW — this is what you add)
                    if (displayStatus != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = displayStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.primary
                        )
                    }

                    // Subtitle (existing)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = feature.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = iconContainerColor,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(id = feature.icon),
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = feature.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )

                    if (displayStatus != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = displayStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
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

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(36.dp)) {
            val strokeWidth = 4.dp.toPx()
            val inset = strokeWidth / 2

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(
                    width = size.width - strokeWidth,
                    height = size.height - strokeWidth
                ),
                style = Stroke(
                    width = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )

            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(
                    width = size.width - strokeWidth,
                    height = size.height - strokeWidth
                ),
                style = Stroke(
                    width = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
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