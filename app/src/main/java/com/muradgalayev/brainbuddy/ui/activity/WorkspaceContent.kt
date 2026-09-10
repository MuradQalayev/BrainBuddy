package com.muradgalayev.brainbuddy.ui.activity

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents
import com.muradgalayev.brainbuddy.ui.games.ShapeFlowEntryCard
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.ui.sharedcomponents.AppSearchBar
import com.muradgalayev.brainbuddy.data.health.HealthConnectUiState
import com.muradgalayev.brainbuddy.domain.model.Medication
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.accessibility.speaking

// dark mode helper
@Composable
private fun isDarkTheme(): Boolean =
    colorScheme.background.luminance() < 0.5f

@Composable
private fun wellnessPrimaryText() = colorScheme.onSurface

@Composable
private fun wellnessSecondaryText() = colorScheme.onSurfaceVariant

@Composable
private fun wellnessCardColor(alpha: Float = .92f) =
    colorScheme.surfaceContainerHigh.copy(alpha = alpha)

@Composable
private fun wellnessGradient(): List<Color> = if (isDarkTheme()) {
    listOf(Color(0xFF35231F), Color(0xFF29243A), colorScheme.background)
} else {
    listOf(Color(0xFFFFD1C2), Color(0xFFD9D8FF), Color(0xFFF6F6FA))
}

@Composable
fun WorkspaceOverviewTab(
    onNavigate: (String) -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val healthState by viewModel.healthState.collectAsState()
    val medications by viewModel.medications.collectAsState()
    val avatarToPrefetch by viewModel.avatarUrl.collectAsState()
    val togetherConnections by viewModel.togetherConnections.collectAsState()
    val togetherIncomingRequests by viewModel.togetherIncomingRequests.collectAsState()
    val focusInviteCount by viewModel.focusInviteCount.collectAsState()
    val workspaceContext = LocalContext.current
    LaunchedEffect(avatarToPrefetch) {
        val url = avatarToPrefetch ?: return@LaunchedEffect
        coil.Coil.imageLoader(workspaceContext).enqueue(
            ImageRequest.Builder(workspaceContext).data(url).build(),
        )
    }
    val isSimplified = uiState.isSimplified

    // search over three destinations cost a header slot and added a mode to a list you can scan
    val filteredFeatures = features

    val wash = rememberTimeOfDayWash(simplified = isSimplified)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (wash != null) Modifier.background(wash) else Modifier)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .animateContentSize(animationSpec = tween(300))
    ) {
        // header. simplify lives in Settings now, with the rest of the display preferences.
        // isSimplified still drives the ambient wash, hero photos and the encouragement banner
        Text(
            text = "Workspace",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(20.dp))

        // the one card that answers 'what now?'
        RightNowHero(
            state = uiState,
            onStartFocus = { onNavigate("pomodoro") },
            onOpenTodos = { onNavigate("todo") },
        )

        Spacer(modifier = Modifier.height(28.dp))

        // primary actions. todo and pomodoro are what someone actually does, so they keep the accent
        // and the full card weight. everything below recedes on purpose
        WorkspaceSectionHeading(
            title = "Choose your next move",
            subtitle = "One thing is enough",
        )
        Spacer(Modifier.height(12.dp))

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
                    pomodoroProgress = uiState.pomodoroProgress,
                    badgeCount = focusInviteCount,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        filteredFeatures.firstOrNull { it.route == "calendar" }?.let { calendar ->
            FeatureCardItem(
                feature = calendar,
                isGrid = false,
                onClick = { onNavigate(calendar.route) },
                overrideStatus = uiState.nextMeetingText ?: "Your calendar is open",
            )
        }

        // support, quieter on purpose
        Spacer(modifier = Modifier.height(28.dp))
        WorkspaceSectionHeading(
            title = "Keep yourself supported",
            subtitle = "Wellness and care, close by",
        )
        Spacer(Modifier.height(12.dp))
        WellnessSummaryEntryCard(
            healthConnected = healthState.connected,
            medicationCount = medications.size,
            onClick = { onNavigate("health") },
        )
        Spacer(modifier = Modifier.height(14.dp))
        CareNearbyCard(onClick = { onNavigate("care_nearby") })

        // together. no section heading, the card already says 'Myndora Together' and repeating it
        // directly above was the same words twice
        Spacer(modifier = Modifier.height(14.dp))
        TogetherEntryCard(
            connections = togetherConnections,
            pendingRequests = togetherIncomingRequests.size,
            onClick = { onNavigate("together") },
        )

        Spacer(modifier = Modifier.height(28.dp))
        WorkspaceSectionHeading(
            title = "Take a tiny reset",
            subtitle = "A playful pause before your next thing",
        )
        Spacer(Modifier.height(12.dp))
        ShapeFlowEntryCard(onClick = { onNavigate("shape_flow") })

        // encouragement, hidden in simplified mode
        AnimatedVisibility(
            visible = !isSimplified,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(20.dp))
                ActivityBannerCard(
                    title = uiState.activityBannerTitle,
                    message = uiState.activityBannerMessage
                )
            }
        }
    }
}

@Composable
private fun WorkspaceSectionHeading(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WellnessSummaryEntryCard(
    healthConnected: Boolean,
    medicationCount: Int,
    onClick: () -> Unit,
) {
    // same shell as CareNearbyCard and TogetherEntryCard below it, so the three read as one set.
    // the trailing chevron is gone: it looked like its own button but had no onClick, and the
    // whole card was the tap target all along
    val dark = isDarkTheme()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainer,
        onClick = speaking("Health", onClick),
        shadowElevation = if (dark) 0.dp else 1.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFFF4B6E),
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Health",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        healthConnected && medicationCount > 0 ->
                            "Connected · $medicationCount ${if (medicationCount == 1) "medication" else "medications"}"
                        healthConnected -> "Connected"
                        medicationCount > 0 ->
                            "$medicationCount ${if (medicationCount == 1) "medication" else "medications"}"
                        else -> "Not connected"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = when {
                        healthConnected -> "Activity, sleep and body signals"
                        medicationCount > 0 -> "Medications and health setup"
                        else -> "Connect health data to see your daily picture"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun WellnessSummaryScreen(
    onBack: () -> Unit,
    onConnectHealth: () -> Unit,
    onActivityGoals: () -> Unit,
    onEdit: () -> Unit,
    onMedications: () -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val dark = isDarkTheme()
    val backgroundColor = colorScheme.background
    val healthState by viewModel.healthState.collectAsState()
    val medications by viewModel.medications.collectAsState()
    val medicationDoseLogs by viewModel.medicationDoseLogs.collectAsState()
    val avatarUrl by viewModel.avatarUrl.collectAsState()
    val activityGoals by viewModel.activityGoals.collectAsState()
    val cardOrder by viewModel.wellnessCardOrder.collectAsState()
    val pinnedCards by viewModel.wellnessPinnedCards.collectAsState()
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.refreshHealth()
    }
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        colors = if (dark) listOf(
                            Color(0xFF4A2C25), Color(0xFF402B38),
                            Color(0xFF2D3652), Color(0xFF213B46),
                        ) else listOf(
                            Color(0xFFFFA683), Color(0xFFF6B7CF),
                            Color(0xFFB8C7FF), Color(0xFFBDEBFF),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height * .36f),
                    ),
                )
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, backgroundColor),
                        startY = size.height * .10f,
                        endY = size.height * .46f,
                    ),
                )
            },
    ) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WellnessBackButton(onBack)
            Text("Health Connect", Modifier.weight(1f).padding(start = 14.dp), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = wellnessPrimaryText())
            Surface(modifier = Modifier.size(46.dp), shape = CircleShape, color = wellnessCardColor(.72f)) {
                if (!avatarUrl.isNullOrBlank()) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Person, "Profile", tint = wellnessSecondaryText())
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(avatarUrl).crossfade(false).build(),
                            contentDescription = "Profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Person, "Profile", tint = wellnessSecondaryText())
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        WellnessSummary(
            healthState = healthState,
            medications = medications,
            medicationDoseLogs = medicationDoseLogs,
            activityGoals = activityGoals,
            cardOrder = cardOrder,
            pinnedCards = pinnedCards,
            onEditActivityGoals = onActivityGoals,
            onEdit = onEdit,
            onMedications = onMedications,
            onConnectHealth = onConnectHealth,
        )
        Spacer(Modifier.height(32.dp))
    }
    }
}

@Composable
private fun WellnessSummary(
    healthState: HealthConnectUiState,
    medications: List<Medication>,
    medicationDoseLogs: Set<String>,
    activityGoals: ActivityGoals,
    cardOrder: List<String>,
    pinnedCards: Set<String>,
    onEditActivityGoals: () -> Unit,
    onEdit: () -> Unit,
    onConnectHealth: () -> Unit,
    onMedications: () -> Unit,
) {
    var activityOpening by remember { mutableStateOf(false) }
    var showWalkSuggestion by rememberSaveable { mutableStateOf(true) }
    var showMovementSuggestion by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    LaunchedEffect(activityOpening) {
        if (activityOpening) {
            delay(280)
            onEditActivityGoals()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pinned", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = wellnessPrimaryText())
                Text("Today, at a glance", style = MaterialTheme.typography.bodySmall, color = wellnessSecondaryText())
            }
            TextButton(onClick = onEdit) {
                Text("Edit", color = Color(0xFF1687F8), fontWeight = FontWeight.Bold)
            }
        }
        cardOrder.filter { it in pinnedCards }.forEach { card ->
        when (card) {
        "activity" -> if (healthState.connected) {
            SummaryFeedCard(
                title = "Activity",
                titleColor = Color(0xFFFF5A1F),
                icon = Icons.Rounded.DirectionsWalk,
                onClick = { if (!activityOpening) activityOpening = true },
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActivityStat("Steps", healthState.todaySteps?.toInt(), Color(0xFFFF2D55), Modifier.weight(1f))
                        ActivityStat("Exercise", healthState.exerciseMinutesThisWeek?.toInt(), Color(0xFF22C733), Modifier.weight(1f), "min")
                        ActivityStat("Energy", healthState.caloriesBurnedToday?.toInt(), Color(0xFF00AEEA), Modifier.weight(1f), "cal")
                    }
                    Spacer(Modifier.width(12.dp))
                    ActivityRings(
                        stepsProgress = (healthState.todaySteps?.toFloat() ?: 0f) / activityGoals.steps,
                        exerciseProgress = (healthState.exerciseMinutesThisWeek?.toFloat() ?: 0f) / activityGoals.exerciseMinutes,
                        energyProgress = (healthState.caloriesBurnedToday?.toFloat() ?: 0f) / activityGoals.energyKcal,
                        activated = activityOpening,
                    )
                }
            }

        } else {
            SummaryFeedCard(
                title = "Health Connect",
                titleColor = Color(0xFF2477D4),
                icon = Icons.Rounded.MonitorHeart,
                onClick = onConnectHealth,
            ) {
                Text("Connect your health data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = wellnessPrimaryText())
                Text("Tap to connect steps, sleep, activity and heart rate.", color = wellnessSecondaryText(), style = MaterialTheme.typography.bodyMedium)
            }
        }
        "sleep" -> if (healthState.connected) SummaryFeedCard(
                title = "Sleep",
                titleColor = Color(0xFF7367F0),
                icon = Icons.Rounded.Bed,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            healthState.lastSleepHours?.let { "${"%.1f".format(it)} hours" } ?: "No sleep reading yet",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = wellnessPrimaryText(),
                        )
                        Text("Latest sleep session", color = wellnessSecondaryText(), style = MaterialTheme.typography.bodyMedium)
                    }
                    SleepScoreRing(healthState.lastSleepHours)
                }
                Spacer(Modifier.height(18.dp))
                WeeklySleepChart(healthState.sleepLast7Days)
            }

            "body" -> if (healthState.connected) SummaryFeedCard(
                title = "Body signals",
                titleColor = Color(0xFF00A98F),
                icon = Icons.Rounded.MonitorHeart,
            ) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        // today's mean, not the most recent sample. a watch takes several readings a day and they
                        // swing widely, so the last one is an arbitrary pick rather than a summary
                        SummaryStat(
                            "AVG TODAY",
                            healthState.todayAverageHeartRateBpm?.let { "$it bpm" }
                                ?: healthState.weekAverageHeartRateBpm?.let { "$it bpm" }
                                ?: "—",
                        )
                        SummaryStat("RESTING", healthState.restingHeartRateBpm?.let { "$it bpm" } ?: "—")
                        SummaryStat(
                            "WEEK AVG",
                            healthState.weekAverageHeartRateBpm?.let { "$it bpm" } ?: "—",
                        )
                    }
                    // says what the average is built from, and covers a missing figure when the watch hasn't synced
                    val samples = healthState.todayHeartRateSamples
                    if (samples > 0 || healthState.weekAverageHeartRateBpm != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (samples > 0)
                                "From $samples reading${if (samples == 1) "" else "s"} today"
                            else "No readings today — showing the weekly average",
                            style = MaterialTheme.typography.labelSmall,
                            color = wellnessSecondaryText(),
                        )
                    }
                }
            }
        }
        }
        AnimatedVisibility(
            visible = showWalkSuggestion || showMovementSuggestion,
            enter = expandVertically(animationSpec = tween(420)) + fadeIn(tween(300)),
            exit = shrinkVertically(animationSpec = tween(360)) + fadeOut(tween(220)),
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                Text("Suggestions for you", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = wellnessPrimaryText())
                Text("Small ideas based on your wellness space", style = MaterialTheme.typography.bodySmall, color = wellnessSecondaryText())
                Spacer(Modifier.height(2.dp))
            }
        }
        AnimatedVisibility(
            visible = showWalkSuggestion,
            enter = expandVertically(animationSpec = tween(420)) + fadeIn(tween(300)),
            exit = shrinkVertically(animationSpec = tween(380)) + fadeOut(tween(240)),
        ) {
            WellnessVideoSuggestion(
                title = "A gentle walking reset",
                subtitle = "10 min · Guided walk",
                imageRes = R.drawable.wellness_walk_suggestion,
                onDismiss = { showWalkSuggestion = false },
                onOpen = {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/results?search_query=gentle+10+minute+walking+workout")))
                },
            )
        }
        AnimatedVisibility(
            visible = showMovementSuggestion,
            enter = expandVertically(animationSpec = tween(420)) + fadeIn(tween(300)),
            exit = shrinkVertically(animationSpec = tween(380)) + fadeOut(tween(240)),
        ) {
            WellnessVideoSuggestion(
                title = "ADHD-friendly movement break",
                subtitle = "Quick reset · Low pressure",
                imageRes = R.drawable.wellness_movement_suggestion,
                onDismiss = { showMovementSuggestion = false },
                onOpen = {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/results?search_query=ADHD+friendly+movement+break")))
                },
            )
        }
    }
}

@Composable
private fun SleepScoreRing(hours: Double?) {
    val rawScore = (((hours ?: 0.0) / 8.0) * 100).toInt().coerceIn(0, 100)
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(if (started) rawScore / 100f else 0f, tween(850, easing = FastOutSlowInEasing), label = "sleep_score")
    Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            drawArc(Color(0xFFE9E7FF), -90f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Color(0xFF5F6BFF), -90f, 360f * progress, false, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Color(0xFFFF907D), -90f + 360f * progress, 38f.coerceAtMost(360f * (1f - progress)), false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$rawScore%", fontWeight = FontWeight.Bold, color = wellnessPrimaryText(), style = MaterialTheme.typography.titleMedium)
            Text("8h goal", color = wellnessSecondaryText(), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun WeeklySleepChart(days: List<com.muradgalayev.brainbuddy.data.health.DailySleepSummary>) {
    var started by remember { mutableStateOf(false) }
    var selectedDate by remember(days) { mutableStateOf(days.lastOrNull()?.date) }
    LaunchedEffect(days) { started = true }
    val selectedDay = days.firstOrNull { it.date == selectedDate }
    Column(Modifier.fillMaxWidth()) {
        AnimatedContent(targetState = selectedDay, label = "selected_sleep_day") { day ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDarkTheme()) Color(0xFF302E4A) else Color(0xFFECEAFF),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Row(Modifier.padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        day?.date?.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")) ?: "Select a day",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5E55D6),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        day?.let { if (it.hours > 0) "${"%.1f".format(it.hours)} hours total" else "No sleep recorded" }.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = wellnessSecondaryText(),
                    )
                }
            }
        }
        Spacer(Modifier.height(9.dp))
    Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { day ->
            val selected = day.date == selectedDate
            val interactionSource = remember { MutableInteractionSource() }
            val barWidth by animateDpAsState(if (selected) 22.dp else 18.dp, spring(), label = "sleep_bar_width")
            val fraction by animateFloatAsState(
                targetValue = if (started) (day.hours / 10.0).toFloat().coerceIn(0f, 1f) else 0f,
                animationSpec = tween(700, delayMillis = days.indexOf(day) * 65, easing = FastOutSlowInEasing),
                label = "sleep_bar",
            )
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.weight(1f).width(barWidth)
                        .clip(RoundedCornerShape(11.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { selectedDate = day.date },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(fraction.coerceAtLeast(.04f))
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                Brush.verticalGradient(
                                    if (selected) listOf(Color(0xFF5D52F5), Color(0xFFA69EFF))
                                    else listOf(Color(0xFF9188F8), Color(0xFFD0CBFF)),
                                ),
                            ),
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    day.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color(0xFF8178FF) else wellnessSecondaryText(),
                )
            }
        }
    }
    }
}

@Composable
private fun SummaryFeedCard(
    title: String,
    titleColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    customIcon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardShape = RoundedCornerShape(28.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .then(
                if (onClick != null) Modifier.clickable(onClick = speaking(title, onClick))
                else Modifier
            ),
        shape = cardShape,
        color = wellnessCardColor(),
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = titleColor.copy(alpha = .12f)) {
                    Box(Modifier.padding(8.dp).size(19.dp), contentAlignment = Alignment.Center) {
                        if (customIcon != null) customIcon()
                        else if (icon != null) Icon(icon, null, tint = titleColor, modifier = Modifier.size(19.dp))
                    }
                }
                Text(title, Modifier.weight(1f).padding(start = 9.dp), fontWeight = FontWeight.Bold, color = titleColor, style = MaterialTheme.typography.titleMedium)
                Text("Today", color = wellnessSecondaryText(), style = MaterialTheme.typography.labelMedium)
                if (onClick != null) {
                    Icon(Icons.Rounded.ChevronRight, "Open", tint = wellnessSecondaryText(), modifier = Modifier.padding(start = 5.dp).size(18.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicationWellnessRow(medication: Medication, index: Int) {
    val accents = listOf(
        Color(0xFF18A9D1) to Color(0xFF7C6CF2),
        Color(0xFF00A98F) to Color(0xFF49BDEA),
        Color(0xFFFF7B68) to Color(0xFFF3A65A),
    )
    val (startColor, endColor) = accents[index % accents.size]
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(medication.id) {
        delay(index * 70L)
        visible = true
    }
    val entrance by animateFloatAsState(
        if (visible) 1f else 0f,
        tween(420, easing = FastOutSlowInEasing),
        label = "medication_entrance",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entrance
                translationY = (1f - entrance) * 18.dp.toPx()
            }
            .clip(RoundedCornerShape(21.dp))
            .background(
                Brush.linearGradient(
                    listOf(startColor.copy(alpha = .15f), endColor.copy(alpha = .10f)),
                ),
            )
            .padding(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(startColor, endColor))),
                    contentAlignment = Alignment.Center,
                ) {
                    MedicationArtwork(index, Color.White)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        medication.name.ifBlank { "Medication" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = wellnessPrimaryText(),
                    )
                    Text(
                        if (medication.slots.size == 1) "Once a day" else "${medication.slots.size} times a day",
                        style = MaterialTheme.typography.bodySmall,
                        color = wellnessSecondaryText(),
                    )
                }
                if (medication.doseLabel.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(50), color = wellnessCardColor(.86f)) {
                        Text(
                            medication.doseLabel,
                            Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = startColor,
                        )
                    }
                }
            }
            Spacer(Modifier.height(11.dp))
            if (medication.slots.isEmpty()) {
                Text("No timing selected", style = MaterialTheme.typography.labelMedium, color = wellnessSecondaryText())
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    medication.slots.forEach { slot ->
                        val slotLabel = slot.replaceFirstChar(Char::uppercase)
                        Surface(shape = RoundedCornerShape(50), color = wellnessCardColor(.78f)) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(startColor))
                                Text(
                                    slotLabel,
                                    Modifier.padding(start = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = wellnessPrimaryText(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicationAdherenceTracker(
    medications: List<Medication>,
    logs: Set<String>,
) {
    val today = java.time.LocalDate.now()
    val scheduled = medications.filter { it.isScheduledOn(today.dayOfWeek) }.sumOf { it.slots.distinct().size }
    val todayPrefix = "$today|"
    val completed = logs.count { it.startsWith(todayPrefix) }.coerceAtMost(scheduled)
    val target = if (scheduled == 0) 0f else completed.toFloat() / scheduled
    val progress by animateFloatAsState(target, tween(500, easing = FastOutSlowInEasing), label = "medication_progress")

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Today's doses",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = wellnessPrimaryText(),
            )
            Text(
                if (scheduled == 0) "Not scheduled" else "$completed of $scheduled",
                style = MaterialTheme.typography.bodyMedium,
                color = wellnessSecondaryText(),
            )
            Text("Tap to view and log doses", style = MaterialTheme.typography.labelMedium, color = Color(0xFF18A9D1))
        }
        Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 9.dp.toPx()
                drawArc(
                    color = Color(0xFF18A9D1).copy(alpha = .14f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                if (progress > 0f) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(Color(0xFF19C4DB), Color(0xFF1687F8), Color(0xFF19C4DB))),
                        startAngle = -90f,
                        sweepAngle = progress.coerceIn(0f, 1f) * 360f,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Text(
                if (scheduled == 0) "—" else "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1687F8),
            )
        }
    }
}

@Composable
private fun MedicationArtwork(variant: Int, color: Color) {
    Canvas(Modifier.size(25.dp)) {
        when (variant % 3) {
            0 -> rotate(-38f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .30f, size.height * .05f),
                    size = Size(size.width * .40f, size.height * .88f),
                    cornerRadius = CornerRadius(size.width * .20f),
                )
                drawLine(color.copy(alpha = .55f), Offset(size.width * .30f, size.height * .50f), Offset(size.width * .70f, size.height * .50f), 1.3.dp.toPx())
            }
            1 -> {
                drawCircle(color, radius = size.minDimension * .34f, center = center)
                drawCircle(color.copy(alpha = .35f), radius = size.minDimension * .13f, center = center)
                drawLine(color.copy(alpha = .55f), Offset(size.width * .28f, size.height * .50f), Offset(size.width * .72f, size.height * .50f), 1.2.dp.toPx())
            }
            else -> {
                drawRoundRect(color, Offset(size.width * .23f, size.height * .28f), Size(size.width * .54f, size.height * .60f), CornerRadius(5.dp.toPx()))
                drawRoundRect(color.copy(alpha = .72f), Offset(size.width * .33f, size.height * .10f), Size(size.width * .34f, size.height * .23f), CornerRadius(2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun CapsuleMedicationIcon(color: Color) {
    Canvas(Modifier.size(19.dp)) {
        rotate(-38f) {
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * .28f, size.height * .02f),
                size = Size(size.width * .42f, size.height * .78f),
                cornerRadius = CornerRadius(size.width * .21f),
            )
            drawLine(
                color = Color.White.copy(alpha = .88f),
                start = Offset(size.width * .29f, size.height * .41f),
                end = Offset(size.width * .69f, size.height * .41f),
                strokeWidth = 1.4.dp.toPx(),
            )
        }
    }
}

@Composable
private fun ActivityStat(
    label: String,
    value: Int?,
    color: Color,
    modifier: Modifier = Modifier,
    unit: String = "",
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value?.let { "%,d".format(it) } ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = wellnessPrimaryText(), maxLines = 1)
            if (unit.isNotBlank()) Text(" $unit", style = MaterialTheme.typography.labelMedium, color = wellnessSecondaryText(), modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun ActivityRings(
    stepsProgress: Float,
    exerciseProgress: Float,
    energyProgress: Float,
    activated: Boolean,
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val animatedSteps by animateFloatAsState(if (started) stepsProgress.coerceIn(0f, 1f) else 0f, tween(850, easing = FastOutSlowInEasing), label = "steps_ring")
    val animatedExercise by animateFloatAsState(if (started) exerciseProgress.coerceIn(0f, 1f) else 0f, tween(950, delayMillis = 90, easing = FastOutSlowInEasing), label = "exercise_ring")
    val animatedEnergy by animateFloatAsState(if (started) energyProgress.coerceIn(0f, 1f) else 0f, tween(1050, delayMillis = 180, easing = FastOutSlowInEasing), label = "energy_ring")
    val rotation by animateFloatAsState(if (activated) 20f else 0f, spring(dampingRatio = .58f), label = "activity_ring_rotation")
    val scale by animateFloatAsState(if (activated) 1.12f else 1f, spring(dampingRatio = .55f), label = "activity_ring_tap_scale")
    Canvas(
        Modifier.size(76.dp).graphicsLayer {
            rotationZ = rotation
            scaleX = scale
            scaleY = scale
        },
    ) {
        val stroke = 7.dp.toPx()
        val gap = 3.dp.toPx()
        val colors = listOf(Color(0xFFFF2D55), Color(0xFF22C733), Color(0xFF00AEEA))
        val progress = listOf(animatedSteps, animatedExercise, animatedEnergy)
        colors.forEachIndexed { index, color ->
            val inset = index * (stroke + gap)
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = color.copy(alpha = .14f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress[index].coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
fun ActivityGoalsScreen(
    onBack: () -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val current by viewModel.activityGoals.collectAsState()
    var steps by rememberSaveable(current.steps) { mutableStateOf(current.steps.toString()) }
    var exercise by rememberSaveable(current.exerciseMinutes) { mutableStateOf(current.exerciseMinutes.toString()) }
    var energy by rememberSaveable(current.energyKcal) { mutableStateOf(current.energyKcal.toString()) }
    val valid = listOf(steps, exercise, energy).all { (it.toIntOrNull() ?: 0) > 0 }

    WellnessPageBackground {
        WellnessPageHeader("Activity goals", onBack)
        Spacer(Modifier.height(24.dp))
        Text("Build your rings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = wellnessPrimaryText())
        Text("Pick goals that support your day—not goals that add pressure.", style = MaterialTheme.typography.bodyMedium, color = wellnessSecondaryText())
        Spacer(Modifier.height(18.dp))
        Surface(shape = RoundedCornerShape(28.dp), color = wellnessCardColor()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                GoalField("Steps", steps, { steps = it.filter(Char::isDigit) }, Color(0xFFFF2D55), "steps")
                GoalField("Exercise", exercise, { exercise = it.filter(Char::isDigit) }, Color(0xFF22C733), "minutes")
                GoalField("Energy", energy, { energy = it.filter(Char::isDigit) }, Color(0xFF00AEEA), "kcal")
                Button(
                    enabled = valid,
                    onClick = {
                        viewModel.setActivityGoals(steps.toInt(), exercise.toInt(), energy.toInt())
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(17.dp),
                ) { Text("Save goals", fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

// choose which wellness cards appear and in what order. long-press and drag to reorder: the
// dragged card lifts, rows it passes slide out of the way, and the new order is written once
// on lift rather than on every crossing
@Composable
fun WellnessEditScreen(
    onBack: () -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val order by viewModel.wellnessCardOrder.collectAsState()
    val pinned by viewModel.wellnessPinnedCards.collectAsState()

    // local working copy so the list can rearrange under the finger without waiting for a
    // DataStore round-trip. re-synced from the source whenever we're idle
    val localOrder = remember { mutableStateListOf<String>() }
    var draggingCard by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { WELLNESS_ROW_HEIGHT.toPx() }

    LaunchedEffect(order, draggingCard) {
        if (draggingCard == null && localOrder.toList() != order) {
            localOrder.clear()
            localOrder.addAll(order)
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(wellnessGradient()))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            WellnessPageHeader("Edit Summary", onBack)
            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Pinned cards",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = wellnessPrimaryText(),
                    )
                    Text(
                        "Hold a card, then place it exactly where you want.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = wellnessSecondaryText(),
                    )
                }
                Surface(shape = RoundedCornerShape(999.dp), color = wellnessCardColor(.78f)) {
                    Text(
                        "${pinned.size} pinned",
                        Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2477D4),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                itemsIndexed(localOrder, key = { _, card -> card }) { index, card ->
                    val (label, color) = wellnessCardInfo(card)
                    val isPinned = card in pinned
                    val dragging = draggingCard == card
                    val scale by animateFloatAsState(
                        if (dragging) 1.035f else 1f, spring(), label = "wellness_drag_scale",
                    )
                    val elevation by animateDpAsState(
                        if (dragging) 10.dp else 0.dp, spring(), label = "wellness_drag_elevation",
                    )

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = wellnessCardColor(if (isPinned) .92f else .55f),
                        shadowElevation = elevation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WELLNESS_ROW_HEIGHT)
                            // the dragged row rides the finger, the rest is animated by the LazyColumn's item placement
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationY = if (dragging) dragOffset else 0f
                            }
                            .then(
                                if (dragging) Modifier else Modifier.animateItem()
                            )
                            .pointerInput(card, localOrder.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingCard = card
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        draggingCard = null
                                        dragOffset = 0f
                                        viewModel.setWellnessCardOrder(localOrder.toList())
                                    },
                                    onDragCancel = {
                                        draggingCard = null
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        // swap once the card has travelled a full row, then rebase the offset so it stays under the finger
                                        val from = localOrder.indexOf(card)
                                        val step = (dragOffset / rowHeightPx).toInt()
                                        if (step != 0 && from >= 0) {
                                            val to = (from + step).coerceIn(0, localOrder.lastIndex)
                                            if (to != from) {
                                                localOrder.removeAt(from)
                                                localOrder.add(to, card)
                                                dragOffset -= (to - from) * rowHeightPx
                                            }
                                        }
                                    },
                                )
                            },
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(color.copy(alpha = .14f)),
                                contentAlignment = Alignment.Center,
                            ) { WellnessCardIcon(card, color) }

                            Spacer(Modifier.width(12.dp))
                            Text(
                                label,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isPinned) wellnessPrimaryText() else wellnessSecondaryText(),
                            )

                            Icon(
                                Icons.Rounded.DragHandle,
                                contentDescription = "Hold to reorder $label",
                                tint = wellnessSecondaryText().copy(alpha = .5f),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Switch(
                                checked = isPinned,
                                onCheckedChange = { viewModel.setWellnessCardPinned(card, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// fixed row height, the drag maths needs to know how far one place is
private val WELLNESS_ROW_HEIGHT = 64.dp

@Composable
private fun WellnessPageBackground(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(wellnessGradient()))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), content = content)
    }
}

@Composable
private fun WellnessPageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WellnessBackButton(onBack)
        Text(title, Modifier.padding(start = 14.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = wellnessPrimaryText())
    }
}

@Composable
internal fun WellnessBackButton(onClick: () -> Unit) {
    val dark = isDarkTheme()
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = if (dark) colorScheme.surfaceContainerHigh.copy(alpha = .92f) else Color.White.copy(alpha = .90f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = if (dark) .22f else .35f)),
        shadowElevation = if (dark) 0.dp else 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.ArrowBackIosNew,
                "Back",
                tint = colorScheme.onSurface,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun WellnessVideoSuggestion(
    title: String,
    subtitle: String,
    imageRes: Int,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(190.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF25252B),
        onClick = speaking(title, onOpen),
    ) {
        Box {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = .82f), Color.Black.copy(alpha = .46f), Color.Transparent),
                    ),
                ),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = .38f)) {
                    Icon(Icons.Rounded.Close, "Dismiss suggestion", tint = Color.White, modifier = Modifier.padding(7.dp).size(18.dp))
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp).fillMaxWidth(.72f)) {
                Surface(shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = .18f)) {
                    Text("FOR YOU", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(9.dp))
                Text(title, fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(28.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, tint = Color(0xFFE53030), modifier = Modifier.size(19.dp)) }
                    }
                    Text(subtitle, Modifier.padding(start = 9.dp), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .9f), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun wellnessCardInfo(card: String): Pair<String, Color> = when (card) {
    "activity" -> "Activity" to Color(0xFFFF5A1F)
    "sleep" -> "Sleep" to Color(0xFF7367F0)
    "body" -> "Body signals" to Color(0xFF00A98F)
    else -> "Medications" to Color(0xFF18A9D1)
}

@Composable
private fun WellnessCardIcon(card: String, color: Color) {
    when (card) {
        "activity" -> Icon(Icons.Rounded.DirectionsWalk, null, tint = color)
        "sleep" -> Icon(Icons.Rounded.Bed, null, tint = color)
        "body" -> Icon(Icons.Rounded.MonitorHeart, null, tint = color)
        else -> CapsuleMedicationIcon(color)
    }
}

@Composable
private fun ActivityGoalsDialog(
    current: ActivityGoals,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int) -> Unit,
) {
    var steps by rememberSaveable(current.steps) { mutableStateOf(current.steps.toString()) }
    var exercise by rememberSaveable(current.exerciseMinutes) { mutableStateOf(current.exerciseMinutes.toString()) }
    var energy by rememberSaveable(current.energyKcal) { mutableStateOf(current.energyKcal.toString()) }
    val valid = listOf(steps, exercise, energy).all { (it.toIntOrNull() ?: 0) > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(30.dp),
        containerColor = colorScheme.surfaceContainerHigh,
        title = {
            Column {
                Text("Your activity goals", fontWeight = FontWeight.Bold, color = wellnessPrimaryText())
                Text("Choose limits that feel motivating, not demanding.", style = MaterialTheme.typography.bodySmall, color = wellnessSecondaryText())
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GoalField("Steps", steps, { steps = it.filter(Char::isDigit) }, Color(0xFFFF2D55), "steps")
                GoalField("Exercise", exercise, { exercise = it.filter(Char::isDigit) }, Color(0xFF22C733), "minutes")
                GoalField("Energy", energy, { energy = it.filter(Char::isDigit) }, Color(0xFF00AEEA), "kcal")
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { onSave(steps.toInt(), exercise.toInt(), energy.toInt()) },
                shape = RoundedCornerShape(16.dp),
            ) { Text("Save goals") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GoalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    color: Color,
    suffix: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, color = color, fontWeight = FontWeight.Bold) },
        suffix = { Text(suffix) },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = wellnessSecondaryText())
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, color = wellnessPrimaryText())
    }
}

// data classes

private data class FeatureCard(
    val title: String,
    val subtitle: String,
    val icon: Int,
    val route: String,
    val status: String? = null
)

private val features = listOf(
    FeatureCard("Calendar", "Plan your schedule", R.drawable.ic_calendar, "calendar"),
    FeatureCard("To-Do", "Manage tasks", R.drawable.ic_todo, "todo"),
    FeatureCard("Pomodoro", "Focus timer", R.drawable.ic_timer, "pomodoro")
)

@Composable
private fun FeatureCardItem(
    feature: FeatureCard,
    isGrid: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overrideStatus: String? = null,
    todoProgress: Float? = null,
    pomodoroTimeText: String? = null,
    pomodoroProgress: Float? = null,
    // waiting focus-together invites. zero draws nothing at all
    badgeCount: Int = 0,
) {
    val dark = isDarkTheme()
    val displayStatus = overrideStatus ?: feature.status

    // cards are tinted from the theme rather than from per-card constants. calendar and pomodoro
    // used to carry their own warm tints, which meant they alone ignored the chosen theme.
    // splitting them by accent vs support keeps them apart without either owning a colour
    val accents = MaterialTheme.myndoraAccents
    val containerColor = when (feature.route) {
        "calendar" -> colorScheme.primaryContainer
        "todo" -> colorScheme.primaryContainer
        "pomodoro" -> colorScheme.secondaryContainer
        else -> colorScheme.surfaceContainer
    }

    val iconContainerColor = when (feature.route) {
        "calendar" -> colorScheme.primaryContainer.copy(alpha = 0.6f)
        "todo" -> colorScheme.primaryContainer.copy(alpha = 0.6f)
        "pomodoro" -> colorScheme.secondaryContainer.copy(alpha = 0.6f)
        else -> colorScheme.primaryContainer.copy(alpha = 0.5f)
    }

    val accentColor = when (feature.route) {
        "pomodoro" -> accents.support
        else -> accents.accent
    }

    Surface(
        modifier = if (isGrid) modifier.heightIn(max = 200.dp) else modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        // reads the tile's title plus what it's showing: 'To-Do, 3 tasks left' is the useful sentence
        onClick = speaking(
            listOfNotNull(feature.title, displayStatus?.takeIf { it.isNotBlank() })
                .joinToString(", "),
            onClick,
        ),
        shadowElevation = if (dark) 0.dp else 1.dp,
        tonalElevation = 0.dp
    ) {
      // boxed so the badge can sit over the corner without joining the layout, one that reflowed
      // would nudge everything beside it the moment an invite arrived
      Box {
        if (isGrid) {
            // grid card (todo / pomodoro)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // reduce motion takes both back to a plain icon. the to-do tile otherwise cross-fades between
                // a progress ring and its logo every 4.5 seconds forever, and the pomodoro tile sweeps an arc:
                // idle movement in the corner of the eye, which is what the setting exists to stop. nothing
                // is lost either, the count and the session state are both spelled out in displayStatus
                if (feature.route == "todo" && animationsOn()) {
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
                } else if (feature.route == "pomodoro" && animationsOn()) {
                    PomodoroMiniTimer(
                        timeText = pomodoroTimeText ?: "25:00",
                        progress = pomodoroProgress ?: 0f,
                        accentColor = accentColor
                    )
                } else {
                    FeatureTileIcon(
                        icon = feature.icon,
                        containerColor = iconContainerColor,
                        accentColor = accentColor,
                    )
                }

                // bottom text
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
            // list card (calendar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeatureTileIcon(
                    icon = feature.icon,
                    containerColor = iconContainerColor,
                    accentColor = accentColor,
                )
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

        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(colorScheme.error),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badgeCount.coerceAtMost(9).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onError,
                )
            }
        }
      }
    }
}

// the resting state of a tile's badge: just the logo, no motion, no reading of state
@Composable
private fun FeatureTileIcon(
    icon: Int,
    containerColor: Color,
    accentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        modifier = Modifier.size(52.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// pomodoro mini timer

@Composable
private fun PomodoroMiniTimer(
    timeText: String,
    progress: Float,
    accentColor: Color
) {
    val dark = isDarkTheme()
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.14f else 0.10f)
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

// activity banner

@Composable
private fun ActivityBannerCard(
    title: String,
    message: String
) {
    val dark = isDarkTheme()
    val gradientColors = listOf(
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.surfaceContainerLow,
    )

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
                color = MaterialTheme.myndoraAccents.support.copy(alpha = if (dark) 0.15f else 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(text = "\u2B50", fontSize = 22.sp)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

// care nearby card

// Myndora Together entry point. shows the actual faces rather than a count, an overlapping
// avatar stack tells you at a glance who is here. a pending request gets a coloured badge
// because it's the one state that needs the user to do something
@Composable
private fun TogetherEntryCard(
    connections: List<com.muradgalayev.brainbuddy.domain.model.Connection>,
    pendingRequests: Int,
    onClick: () -> Unit,
) {
    val dark = isDarkTheme()
    val needsAttention = pendingRequests > 0
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainer,
        onClick = speaking("Myndora Together", onClick),
        shadowElevation = if (dark) 0.dp else 1.dp,
        tonalElevation = 0.dp,
        border = if (needsAttention) {
            androidx.compose.foundation.BorderStroke(1.5.dp, colorScheme.tertiary.copy(alpha = .55f))
        } else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (connections.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Rounded.Diversity3,
                            contentDescription = null,
                            tint = colorScheme.tertiary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            } else {
                AvatarStack(connections = connections)
            }

            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Myndora Together",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        needsAttention ->
                            "$pendingRequests ${if (pendingRequests == 1) "request" else "requests"} waiting"
                        connections.isNotEmpty() ->
                            connections.take(2).joinToString(" & ") { it.name } +
                                if (connections.size > 2) " +${connections.size - 2}" else ""
                        else -> "Nobody yet"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (needsAttention) colorScheme.tertiary else colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (connections.isEmpty()) {
                        "Add a partner, family or friend"
                    } else {
                        "Share a calendar, a list, or a check-in"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            if (needsAttention) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(colorScheme.tertiary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$pendingRequests",
                        color = colorScheme.onTertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// overlapping initials or photos, capped at four so the row can't grow unbounded
@Composable
private fun AvatarStack(
    connections: List<com.muradgalayev.brainbuddy.domain.model.Connection>,
) {
    val shown = connections.take(4)
    val overflow = connections.size - shown.size
    Row(
        // the negative spacing makes the overlap, the ring keeps each face readable against the next
        horizontalArrangement = Arrangement.spacedBy((-12).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEach { connection ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceContainer)
                    .padding(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(colorScheme.tertiary.copy(alpha = .18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!connection.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(connection.avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = connection.name,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Text(
                            text = connection.name.take(1).uppercase(),
                            color = colorScheme.tertiary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceContainer)
                    .padding(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(colorScheme.onSurface.copy(alpha = .10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+$overflow",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CareNearbyCard(onClick: () -> Unit) {
    val dark = isDarkTheme()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainer,
        onClick = speaking("Care nearby", onClick),
        shadowElevation = if (dark) 0.dp else 1.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(text = "\ud83c\udfe5", fontSize = 24.sp)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Find Care",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Healthcare near you",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.primary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Locate services around you",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// circular task progress

@Composable
fun CircularTaskProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    // three tiers: done, well underway, just started
    val color = when {
        progress >= 1f -> Color(0xFF10B981)
        progress > 0.5f -> Color(0xFF0EA5E9)
        else -> Color(0xFFEA580C)
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
