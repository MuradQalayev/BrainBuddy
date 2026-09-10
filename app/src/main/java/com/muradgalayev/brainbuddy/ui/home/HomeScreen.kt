package com.muradgalayev.brainbuddy.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.muradgalayev.brainbuddy.ui.accessibility.AccessibilityQuickAccess
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.components.AiPromptCard
import com.muradgalayev.brainbuddy.ui.onboarding.TreeGrowthCard
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.ui.stories.StoriesViewModel
import com.muradgalayev.brainbuddy.ui.stories.StoryHint
import com.muradgalayev.brainbuddy.ui.stories.StoryTray
import com.muradgalayev.brainbuddy.ui.stories.StoryViewer
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// home, laid out as a bento rather than a grid. equal tiles make everything look equally
// important, which on a screen meant to answer 'what now?' is the wrong answer four times.
// so the sizes carry the ranking: a wide strip for the shape of the day, a full-width hero
// for the one thing that's next, then an uneven pair where capture takes the larger half.
// which tiles are there and where they sit is the user's call, this is only the starting order
@Composable
fun HomeScreen(
    onContinueProfile: () -> Unit,
    onOpenWellness: () -> Unit,
    onOpenPomodoro: () -> Unit,
    onOpenProfile: () -> Unit,
    onManageModes: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val modes by viewModel.modes.collectAsState()
    val activeMode by viewModel.activeMode.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val identity by viewModel.identity.collectAsState()
    val healthState by viewModel.healthState.collectAsState()
    val medicationLogs by viewModel.medicationDoseLogs.collectAsState()
    val weather by viewModel.weather.collectAsState()
    val agenda by viewModel.agenda.collectAsState()
    val hiddenWidgets by viewModel.hiddenWidgets.collectAsState()
    val layout by viewModel.layout.collectAsState()
    val timerRunning by viewModel.timerRunning.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    // ten seconds is finer than the countdown reads and coarse enough to stay off the battery;
    // the ribbon marker moves about a pixel a minute either way
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            now = LocalDateTime.now()
        }
    }
    val nextUp = remember(agenda, now) { selectNextUp(agenda, now) }

    // a seed per visit, not per recomposition: now reticks every ten seconds and the line must
    // not reshuffle while it's being read. leaving home and coming back draws a new one
    val greetingSeed = remember { Random.nextInt() }
    val band = dayBandFor(now.toLocalTime())
    val greeting = remember(band, greetingSeed, identity.firstName) {
        homeGreeting(now.toLocalTime(), identity.firstName, greetingSeed)
    }

    var editing by remember { mutableStateOf(false) }

    // stories. refreshed on every visit so one published while the app was backgrounded is there
    // when the user next pulls. the pull is inert with nothing to show, and inert while
    // rearranging: a drag that starts on a tile is a reorder, not a request for encouragement
    val storiesViewModel: StoriesViewModel = hiltViewModel()
    val storiesState by storiesViewModel.uiState.collectAsState()
    // the pull reveals the tray of circles, only tapping a circle opens a story. null means
    // nothing is open.
    // remember and not rememberSaveable on purpose: leaving Home closes the tray. saveable would
    // restore it on the way back, so the screen would return already opened by a gesture the user
    // made ten minutes ago. the tray is the answer to a pull, not a setting
    var trayOpen by remember { mutableStateOf(false) }
    var openStoryIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { storiesViewModel.refresh() }

    val storyPull = rememberStoryPullState(
        // pulling again with the tray already open would be a no-op the user can feel as the screen
        // refusing them, so the gesture retires once it has done its job
        enabled = { storiesState.hasStories && !editing && !trayOpen },
        onTrigger = { trayOpen = true },
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    fun showCustomizationLocked() {
        val mode = activeMode ?: return
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                "Home customization isn't available while ${mode.name} mode is active. " +
                    "Edit the mode's Home screen from Modes instead."
            )
        }
    }

    // a scheduled or manual mode can switch on while Home is being edited. close the editor at
    // once so its overlay can never be written into the user's normal layout
    LaunchedEffect(activeMode?.id) {
        if (activeMode != null) {
            editing = false
        }
    }
    // editing shows everything, switched-off tiles included as ghosts, so the screen doesn't
    // reflow under the finger that just turned one off
    val rows = remember(layout, editing) { layout.rows(includeHidden = editing) }
    val hiddenList = remember(hiddenWidgets) { HomeWidget.entries.filter { it.id in hiddenWidgets } }

    // drag-to-reorder. the move is committed once, on drop: reordering live would relayout the
    // list under the finger and the tile would have to be re-chased every frame
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    val tileCentres = remember { mutableStateMapOf<String, Float>() }
    LaunchedEffect(editing) {
        if (!editing) {
            draggingId = null
            dragDelta = 0f
        }
    }

    val dragFrom = layout.order.indexOfFirst { it.id == draggingId }
    // computed rather than remembered: the centres are a snapshot map whose values move without
    // its size changing, so any key cheap enough to write would also be wrong. nine items, the
    // arithmetic is cheaper than the bookkeeping to skip it
    val dropTarget = draggingId?.let {
        dropTargetIndex(
            order = layout.order,
            centres = tileCentres,
            draggingId = it,
            delta = dragDelta,
        )
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(storyPull.connection)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // titled like the other tabs (see CalendarHeader) so the header sits at the same weight and
        // baseline wherever the nav bar lands you. the greeting rides under it as a subtitle: it's
        // the line that reacts to when you opened the app, so it wants to be read first
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Home",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // between the greeting and the tiles, so it reads as header rather than a competing card
                AnimatedVisibility(
                    visible = !trayOpen && storiesState.hasUnseen,
                    enter = fadeIn(tween(220)) + expandVertically(tween(240)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
                ) {
                    Column {
                        Spacer(Modifier.height(7.dp))
                        StoryHint(
                            unseenCount = storiesState.stories.count { !it.seen },
                            onOpen = { trayOpen = true },
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            ProfileAvatar(
                avatarUrl = identity.avatarUrl,
                name = identity.fullName ?: identity.firstName,
                onClick = onOpenProfile,
            )
        }

        // directly under the greeting and inside the scroll rather than pinned over it: the tray
        // takes real layout space and pushes the tiles down, so scrolling carries it away like any
        // other content instead of hanging over the screen until it's dismissed
        AnimatedVisibility(
            visible = trayOpen && storiesState.hasStories,
            enter = fadeIn(tween(200)) + expandVertically(tween(280, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(200)),
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                StoryTray(
                    stories = storiesState.stories,
                    onOpen = { openStoryIndex = it },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        rows.forEachIndexed { index, row ->
            if (index > 0) Spacer(Modifier.height(14.dp))
            // where the dragged tile would land, drawn at the row boundary it falls on
            DropIndicator(
                visible = draggingId != null &&
                    dropTarget != null &&
                    dropTarget != dragFrom &&
                    row.widgets.firstOrNull()?.let { layout.order.indexOf(it) } == dropTarget,
            )
            HomeWidgetRow(
                row = row,
                index = index,
                editing = editing,
                draggingId = draggingId,
                dragDelta = dragDelta,
                onCentreChanged = { id, centre -> tileCentres[id] = centre },
                onToggle = { widget, visible ->
                    if (!viewModel.setWidgetVisible(widget, visible)) showCustomizationLocked()
                },
                onResize = { widget ->
                    val next = if (layout.spanOf(widget) == WidgetSpan.Full) {
                        WidgetSpan.Half
                    } else {
                        WidgetSpan.Full
                    }
                    if (!viewModel.setWidgetSpan(widget, next)) showCustomizationLocked()
                },
                onDragStart = { id ->
                    draggingId = id
                    dragDelta = 0f
                },
                onDrag = { dragDelta += it },
                onDragEnd = {
                    // recomputed here rather than read from the enclosing composition: pointerInput keeps the
                    // lambda it was launched with, so a captured target would be whatever it was when the drag
                    // started and every drop would land where it began
                    val from = layout.order.indexOfFirst { it.id == draggingId }
                    val to = dropTargetIndex(layout.order, tileCentres, draggingId, dragDelta)
                    draggingId = null
                    dragDelta = 0f
                    if (from >= 0 && to != null && to != from) viewModel.moveWidget(from, to)
                },
                onLongPress = {
                    if (activeMode == null) editing = true else showCustomizationLocked()
                },
            ) { widget, tileModifier ->
                HomeWidgetContent(
                    widget = widget,
                    modifier = tileModifier,
                    weather = weather,
                    agenda = agenda,
                    now = now,
                    nextUp = nextUp,
                    healthState = healthState,
                    profile = profile,
                    medicationLogs = medicationLogs,
                    timerRunning = timerRunning,
                    onOpenWellness = onOpenWellness,
                    onOpenPomodoro = onOpenPomodoro,
                    onContinueProfile = onContinueProfile,
                    modes = modes,
                    activeMode = activeMode,
                    onSelectMode = viewModel::selectMode,
                    onManageModes = onManageModes,
                    viewModel = viewModel,
                )
            }
        }

        if (rows.isEmpty() && !editing) {
            EmptyHome(
                onCustomise = {
                    if (activeMode == null) editing = true else showCustomizationLocked()
                }
            )
        }

        if (editing) {
            Spacer(Modifier.height(14.dp))
            EditingHint(hiddenCount = hiddenList.size)
        }

        Spacer(Modifier.height(20.dp))
        CustomiseBar(
            editing = editing,
            locked = activeMode != null,
            onToggle = {
                when {
                    editing -> editing = false
                    activeMode != null -> showCustomizationLocked()
                    else -> editing = true
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }

        // pinned to the edge rather than placed in the scroll, because the point of it is to be
        // reachable when reading gets hard, which is rarely when the page is scrolled to the top
        AccessibilityQuickAccess()

        com.muradgalayev.brainbuddy.ui.sharedcomponents.MyndoraSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
        )

        // above the content and below the snackbar: it belongs to the gesture, not to the page, so
        // it mustn't scroll away with what's underneath it
        StoryPullIndicator(
            progress = storyPull.progress,
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
        )
    }

    openStoryIndex?.let { index ->
        StoryViewer(
            stories = storiesState.stories,
            // opens on the circle that was tapped, not on the first unseen one
            startIndex = index,
            onSeen = storiesViewModel::markSeen,
            onDismiss = { openStoryIndex = null },
        )
    }
}

// the header photo, and the fallback for anyone who hasn't set one. initials rather than a
// generic person glyph: a silhouette is the same for every user so it reads as a missing
// image, while two letters still say the app knows who is signed in
@Composable
private fun ProfileAvatar(
    avatarUrl: String?,
    name: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val initials = name
        ?.split(' ', '.', '_', '-')
        ?.mapNotNull { it.firstOrNull()?.uppercase() }
        ?.take(2)
        ?.joinToString("")
        ?.takeIf { it.isNotBlank() }
        ?: "?"

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colors.primary.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Your profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
            )
        }
    }
}

// one row of tiles with the edit-mode chrome. the remove badge has to hang off the tile's
// corner, so it's drawn in a Box the tile only partly fills. clipping it to the card would
// put the remove button inside the thing it removes, competing with that tile's own taps
@Composable
private fun HomeWidgetRow(
    row: HomeRow,
    index: Int,
    editing: Boolean,
    draggingId: String?,
    dragDelta: Float,
    onCentreChanged: (String, Float) -> Unit,
    onToggle: (HomeWidget, Boolean) -> Unit,
    onResize: (HomeWidget) -> Unit,
    onDragStart: (String) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onLongPress: () -> Unit,
    tile: @Composable (HomeWidget, Modifier) -> Unit,
) {
    val height = if (row.isPair) 196.dp else null
    val holdsDragged = row.tiles.any { it.widget.id == draggingId }

    Row(
        Modifier
            .fillMaxWidth()
            .then(if (height != null) Modifier.fillMaxWidth().height(height) else Modifier)
            // the dragged tile has to ride over its neighbours, and z-order resolves per parent, so the
            // row it lives in has to rise too, not just the tile
            .zIndex(if (holdsDragged) 1f else 0f),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        row.tiles.forEachIndexed { position, homeTile ->
            val widget = homeTile.widget
            val dragging = widget.id == draggingId

            Box(
                Modifier
                    .weight(widgetWeight(widget))
                    .then(if (height != null) Modifier.fillMaxHeight() else Modifier)
                    // only while editing: this writes snapshot state from the layout pass, and doing that on
                    // every layout of every tile for the life of the screen invalidates readers for a number
                    // nothing is looking at
                    .then(
                        if (editing) {
                            Modifier.onGloballyPositioned { coordinates ->
                                onCentreChanged(
                                    widget.id,
                                    coordinates.positionInRoot().y + coordinates.size.height / 2f,
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
                    // on the tile's own wrapper rather than an overlay laid over the top: an ancestor sees the
                    // Initial pass before the tile's buttons do, so nothing has to sit in front of them
                    .longPressToCustomise(
                        enabled = !editing && !widget.holdsTextInput,
                        onLongPress = onLongPress,
                    )
                    .graphicsLayer {
                        if (dragging) {
                            translationY = dragDelta
                            scaleX = 1.03f
                            scaleY = 1.03f
                            shadowElevation = 18f
                            shape = HomeCardShape
                            clip = false
                        }
                    }
                    .zIndex(if (dragging) 1f else 0f)
                    // a dragged tile shouldn't also be tilting, the two motions together read as a glitch rather
                    // than as picking something up
                    .wiggle(editing && !dragging, index + position)
            ) {
                if (homeTile.hidden) {
                    GhostTile(
                        widget = widget,
                        onRestore = { onToggle(widget, true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (height != null) Modifier.fillMaxHeight() else Modifier.height(84.dp)),
                    )
                } else {
                    tile(
                        widget,
                        Modifier.fillMaxWidth().then(if (height != null) Modifier.fillMaxHeight() else Modifier),
                    )
                }

                if (editing && !homeTile.hidden) {
                    // covers the whole tile so its own controls stay inert while editing. cheaper and far more
                    // reliable than threading an enabled flag through every card, and it reaches things a flag
                    // can't, like the AI prompt's text field and the wellness pager's swipes. it also carries
                    // the new meaning of a tap: take this off the home screen
                    Box(
                        Modifier
                            .matchParentSize()
                            .clip(HomeCardShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onToggle(widget, false) },
                            )
                    )
                }

                RemoveBadge(
                    visible = editing && !homeTile.hidden,
                    onClick = { onToggle(widget, false) },
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp),
                )
                ResizeBadge(
                    visible = editing && !homeTile.hidden,
                    span = homeTile.span,
                    onToggle = { onResize(widget) },
                    modifier = Modifier.align(Alignment.TopStart).offset(x = (-6).dp, y = (-6).dp),
                )
                DragHandle(
                    visible = editing,
                    dragging = dragging,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // just inside the bottom edge, hanging it off would put it over the next tile
                        .offset(y = (-8).dp)
                        .pointerInput(widget.id) {
                            detectDragGestures(
                                onDragStart = { onDragStart(widget.id) },
                                onDrag = { change, amount ->
                                    change.consume()
                                    onDrag(amount.y)
                                },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd,
                            )
                        },
                )
            }
        }
    }
}

// what the three controls do, said once at the foot of edit mode. three new gestures land at
// the same moment and two of them are on badges small enough to read as decoration
@Composable
private fun EditingHint(hiddenCount: Int) {
    Text(
        text = buildString {
            append("Tap a tile to take it off · drag the handle to reorder · ⤢ to resize")
            if (hiddenCount > 0) {
                append(" · tap a dashed slot to put one back")
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

// the bar showing where a dragged tile will land
@Composable
private fun DropIndicator(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(if (animationsOn()) 120 else 0)),
        exit = fadeOut(tween(if (animationsOn()) 120 else 0)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .height(4.dp)
                .clip(HomePillShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

// which slot a dragged tile is over. it takes a slot once its centre passes the centre of the
// tile living there, which stops the drop flickering while the finger sits on the boundary
private fun dropTargetIndex(
    order: List<HomeWidget>,
    centres: Map<String, Float>,
    draggingId: String?,
    delta: Float,
): Int? {
    val from = order.indexOfFirst { it.id == draggingId }
    if (from < 0) return null
    val centre = (centres[draggingId] ?: return null) + delta

    var target = from
    order.forEachIndexed { index, widget ->
        val other = centres[widget.id] ?: return@forEachIndexed
        if (index < from && centre < other) target = minOf(target, index)
        if (index > from && centre > other) target = maxOf(target, index)
    }
    return target
}

// maps a widget to its tile. while editing, tiles stop reacting to their own taps: the only
// sensible meaning of touching a card in edit mode is 'get this off my home screen', and
// starting a focus session by accident while rearranging would be a nasty surprise
@Composable
private fun HomeWidgetContent(
    widget: HomeWidget,
    modifier: Modifier,
    weather: com.muradgalayev.brainbuddy.data.repository.WeatherSnapshot?,
    agenda: List<HomeAgendaItem>,
    now: LocalDateTime,
    nextUp: HomeAgendaItem?,
    healthState: com.muradgalayev.brainbuddy.data.health.HealthConnectUiState,
    profile: com.muradgalayev.brainbuddy.domain.model.AdhdProfile?,
    medicationLogs: Set<String>,
    timerRunning: Boolean,
    onOpenWellness: () -> Unit,
    onOpenPomodoro: () -> Unit,
    onContinueProfile: () -> Unit,
    modes: List<com.muradgalayev.brainbuddy.domain.model.AppMode>,
    activeMode: com.muradgalayev.brainbuddy.domain.model.AppMode?,
    onSelectMode: (String?) -> Unit,
    onManageModes: () -> Unit,
    viewModel: HomeViewModel,
) {
    when (widget) {
        HomeWidget.Today -> HomeTodayCard(weather = weather, agenda = agenda, now = now, modifier = modifier)

        HomeWidget.NextUp -> HomeNextUpCard(
            item = nextUp,
            now = now,
            onStart = { item -> viewModel.startFocus(item, onOpenPomodoro) },
            onDone = viewModel::markDone,
            onSnooze = viewModel::snooze,
            modifier = modifier,
        )

        HomeWidget.Mode -> HomeModeTile(
            modes = modes,
            activeMode = activeMode,
            onSelect = onSelectMode,
            onManage = onManageModes,
            modifier = modifier,
        )

        HomeWidget.QuickAdd -> QuickCaptureCard(modifier = modifier)

        HomeWidget.Wellness -> HomeWellnessCarousel(
            healthState = healthState,
            medications = profile?.medications.orEmpty(),
            medicationLogs = medicationLogs,
            onClick = onOpenWellness,
            modifier = modifier,
        )

        HomeWidget.Focus -> HomeFocusTile(
            running = timerRunning,
            onStart = { minutes -> viewModel.startQuickFocus(minutes, onOpenPomodoro) },
            onOpen = onOpenPomodoro,
            modifier = modifier,
        )

        HomeWidget.Wins -> {
            // the full view belongs to the tile that opens it, so the state lives here rather than at
            // screen level where every other tile would have to know about it
            var showBuddy by remember { mutableStateOf(false) }
            val done = agenda.count { it.completed }
            HomeWinsTile(
                done = done,
                total = agenda.size,
                onOpen = { showBuddy = true },
                modifier = modifier,
            )
            if (showBuddy) {
                TodayProgressBuddyDialog(
                    done = done,
                    total = agenda.size,
                    onDismiss = { showBuddy = false },
                )
            }
        }

        HomeWidget.Tree -> {
            val profileComplete = profile.isPersonalizationComplete()
            HomeCard(modifier = modifier) {
                TreeGrowthCard(
                    progress = if (profileComplete) 1f else profile.personalizationProgress(),
                    invitation = !profileComplete,
                    completed = profileComplete,
                    onClick = if (profileComplete) null else onContinueProfile,
                    bare = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        HomeWidget.AiPrompt -> Box(modifier) { AiPromptCard(embedded = true) }
    }
}

// what's left when someone removes everything. not an error, just a way back
@Composable
private fun EmptyHome(onCustomise: () -> Unit) {
    HomeCard(modifier = Modifier.fillMaxWidth(), onClick = onCustomise) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Your home is empty",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap Customise home to put something back.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeWellnessCarousel(
    healthState: com.muradgalayev.brainbuddy.data.health.HealthConnectUiState,
    medications: List<com.muradgalayev.brainbuddy.domain.model.Medication>,
    medicationLogs: Set<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val scheduled = medications.filter { it.isScheduledOn(today.dayOfWeek) }.sumOf { it.slots.distinct().size }
    val logged = medicationLogs.count { it.startsWith("$today|") }.coerceAtMost(scheduled)

    LaunchedEffect(pager.currentPage) {
        delay(210_000L)
        pager.animateScrollToPage((pager.currentPage + 1) % 3)
    }

    // accented by whichever page is showing, so the tile's wash tracks the rings on it
    val pageAccent = listOf(Color(0xFFFF2D55), Color(0xFF7367F0), Color(0xFF18A9D1))[pager.currentPage]
    HomeCard(modifier = modifier, accent = pageAccent, onClick = onClick) {
        // tighter than it was: this tile is the narrow half of the row now, and the rings plus their
        // three readouts need the width more than the card needs the margin
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 16.dp)) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                when (page) {
                    0 -> HomeActivityPage(healthState)
                    1 -> HomeSleepPage(healthState.lastSleepHours)
                    else -> HomeMedicationPage(logged, scheduled)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(3) { page ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(if (pager.currentPage == page) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (pager.currentPage == page) listOf(Color(0xFFFF5A1F), Color(0xFF7367F0), Color(0xFF18A9D1))[page] else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .2f))
                            .clickable { scope.launch { pager.animateScrollToPage(page) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeActivityPage(state: com.muradgalayev.brainbuddy.data.health.HealthConnectUiState) {
    val steps = ((state.todaySteps ?: 0).toFloat() / 8_000f).coerceIn(0f, 1f)
    val exercise = ((state.exerciseMinutesThisWeek ?: 0).toFloat() / 30f).coerceIn(0f, 1f)
    val energy = ((state.caloriesBurnedToday ?: 0).toFloat() / 500f).coerceIn(0f, 1f)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        HomeTripleRings(steps, exercise, energy)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            HomeCompactMetric("${state.todaySteps ?: 0}", Color(0xFFFF2D55))
            HomeCompactMetric("${state.exerciseMinutesThisWeek ?: 0}m", Color(0xFF22C733))
            HomeCompactMetric("${state.caloriesBurnedToday ?: 0}", Color(0xFF00AEEA))
        }
    }
}

@Composable
private fun HomeCompactMetric(value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(value, style = MaterialTheme.typography.labelSmall.tabular(), fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun HomeTripleRings(steps: Float, exercise: Float, energy: Float) {
    val values = listOf(steps, exercise, energy).mapIndexed { index, value ->
        animateFloatAsState(value, tween(650 + index * 100, easing = FastOutSlowInEasing), label = "home_activity_$index").value
    }
    Canvas(Modifier.size(88.dp)) {
        val colors = listOf(Color(0xFFFF2D55), Color(0xFF22C733), Color(0xFF00AEEA))
        values.forEachIndexed { index, progress ->
            val inset = index * 12.dp.toPx()
            val arcSize = size.copy(width = size.width - inset * 2, height = size.height - inset * 2)
            drawArc(colors[index].copy(alpha = .14f), -90f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
            drawArc(colors[index], -90f, progress * 360f, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun HomeSleepPage(hours: Double?) {
    val target = ((hours ?: 0.0) / 8.0).toFloat().coerceIn(0f, 1f)
    HomeSingleWellnessPage(hours?.let { "${"%.1f".format(it)}h" } ?: "No data", target, Color(0xFF7367F0))
}

@Composable
private fun HomeMedicationPage(logged: Int, scheduled: Int) {
    val progress = if (scheduled == 0) 0f else logged.toFloat() / scheduled
    HomeSingleWellnessPage(if (scheduled == 0) "Not scheduled" else "$logged of $scheduled", progress, Color(0xFF18A9D1))
}

@Composable
private fun HomeSingleWellnessPage(value: String, target: Float, accent: Color) {
    val progress by animateFloatAsState(target, tween(700, easing = FastOutSlowInEasing), label = "home_wellness_progress")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(accent.copy(alpha = .14f), -90f, 360f, false, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                if (progress > 0f) drawArc(accent, -90f, progress * 360f, false, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
            }
            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyLarge.tabular(),
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(value, style = MaterialTheme.typography.labelLarge.tabular(), color = accent, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
