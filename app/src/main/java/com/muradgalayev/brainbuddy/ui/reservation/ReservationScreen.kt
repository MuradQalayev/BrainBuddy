package com.muradgalayev.brainbuddy.ui.reservation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.domain.model.Place
import com.muradgalayev.brainbuddy.ui.sharedcomponents.SuccessPopup
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

@Composable
fun ReservationScreen(
    onBack: () -> Unit,
    onHistory: () -> Unit,
    viewModel: ReservationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val selectedPlace = state.places.firstOrNull { it.id == state.selectedPlaceId }
    var showSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved, state.error) {
        if (state.saved && state.error == null) showSuccess = true
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 164.dp),
        ) {
            item {
                val steps = listOf(
                    selectedPlace != null,
                    state.date != null,
                    state.time != null,
                )
                BookingHero(
                    onBack = onBack,
                    onHistory = onHistory,
                    city = state.cityName,
                    progress = steps.count { it } / steps.size.toFloat(),
                )
            }

            if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                item {
                    Column(
                        Modifier.padding(top = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(26.dp),
                    ) {
                        BookingSection(
                            number = "1",
                            title = stringResource(R.string.res_choose_place),
                            subtitle = stringResource(R.string.res_near, state.cityName.ifBlank { stringResource(R.string.res_you) }),
                            complete = selectedPlace != null,
                        ) {
                            LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.places, key = { it.id }) { place ->
                                    PlaceCard(
                                        place = place,
                                        selected = place.id == state.selectedPlaceId,
                                        onClick = { viewModel.selectPlace(place.id) },
                                    )
                                }
                            }
                        }

                        BookingSection(
                            number = "2",
                            title = stringResource(R.string.res_pick_day),
                            subtitle = stringResource(R.string.res_two_weeks),
                            complete = state.date != null,
                        ) {
                            LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items((1L..14L).map { LocalDate.now().plusDays(it) }) { date ->
                                    DateCard(date, state.date == date) { viewModel.setDate(date) }
                                }
                            }
                        }

                        // later steps grow into place rather than appearing, so the list never jumps under a finger
                        // that's still on the screen
                        RevealStep(visible = state.date != null) {
                            BookingSection(
                                number = "3",
                                title = stringResource(R.string.res_choose_time),
                                subtitle = stringResource(R.string.res_30_min),
                                complete = state.time != null,
                            ) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                                    verticalArrangement = Arrangement.spacedBy(9.dp),
                                ) {
                                    (8..18).flatMap { listOf(LocalTime.of(it, 0), LocalTime.of(it, 30)) }
                                        .forEach { time ->
                                            TimeChip(time, state.time == time) { viewModel.setTime(time) }
                                        }
                                }
                            }
                        }

                        AnimatedVisibility(state.error != null) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.LocalHospital,
                                        null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                    Text(
                                        state.error.orEmpty(),
                                        Modifier.padding(start = 10.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .98f),
            shadowElevation = 18.dp,
        ) {
            Column(
                Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val ready = state.isOnline && selectedPlace != null && state.date != null &&
                    state.time != null && !state.saving && !state.saved
                // a gentle lift the moment everything needed is filled in. the button becoming live is the
                // one thing on this screen worth noticing without looking for it
                val readyLift by animateFloatAsState(
                    targetValue = if (ready) 1f else .98f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 320f),
                    label = "reserveReady",
                )
                Button(
                    onClick = { viewModel.saveAppointment() },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth().height(58.dp).scale(readyLift),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(20.dp))
                        Text(
                            if (state.isOnline) stringResource(R.string.res_reserve) else stringResource(R.string.res_need_internet),
                            Modifier.padding(start = 9.dp),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        if (showSuccess) {
            SuccessPopup(
                title = stringResource(R.string.res_confirmed),
                message = stringResource(R.string.res_added_calendar),
                onDismiss = { showSuccess = false },
            )
        }
    }
}

@Composable
private fun BookingHero(
    onBack: () -> Unit,
    onHistory: () -> Unit,
    city: String,
    progress: Float,
) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ),
            )
            .padding(start = 10.dp, end = 22.dp, top = 12.dp, bottom = 26.dp),
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        stringResource(R.string.common_back),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                IconButton(onClick = onHistory) {
                    Icon(
                        Icons.Rounded.History,
                        stringResource(R.string.res_history),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(54.dp), CircleShape, color = Color.White.copy(alpha = .8f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(
                        stringResource(R.string.res_book_an),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.LocationOn,
                            null,
                            Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            city.ifBlank { stringResource(R.string.res_profile_city) },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            // how much of the booking is done, filling as you go. a three-step form with no sense of an
            // end is exactly the kind of thing that gets abandoned halfway
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = spring(dampingRatio = .9f, stiffness = 180f),
                label = "bookingProgress",
            )
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 18.dp, end = 2.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .13f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

// grows a later step into place instead of snapping it in
@Composable
private fun RevealStep(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = tween(320), expandFrom = Alignment.Top) +
            fadeIn(tween(260, delayMillis = 80)) +
            slideInVertically(tween(320)) { it / 5 },
        exit = shrinkVertically(animationSpec = tween(220), shrinkTowards = Alignment.Top) +
            fadeOut(tween(140)),
    ) { content() }
}

@Composable
private fun BookingSection(
    number: String,
    title: String,
    subtitle: String,
    complete: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            // the badge is the progress indicator: it fills in and flips to a tick the moment the step is
            // satisfied, so how far you've got is legible from the numbers alone
            val badge by animateColorAsState(
                targetValue = if (complete) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.primary,
                animationSpec = tween(320),
                label = "stepBadge",
            )
            Surface(Modifier.size(30.dp), CircleShape, color = badge) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = complete,
                        transitionSpec = {
                            (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn())
                                .togetherWith(scaleOut(tween(120)) + fadeOut(tween(120)))
                        },
                        label = "stepBadgeContent",
                    ) { done ->
                        if (done) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.res_step_complete),
                                tint = Color.White,
                                modifier = Modifier.size(17.dp),
                            )
                        } else {
                            Text(number, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

// springs a card slightly when picked and dips it while held. the whole screen is a sequence
// of small choices, and each one previously changed colour and nothing else, so with no
// motion it was easy to tap and not be sure anything had registered
@Composable
private fun rememberPickScale(
    selected: Boolean,
    interactionSource: MutableInteractionSource,
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> .96f
            selected -> 1.03f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 420f),
        label = "pickScale",
    )
    return scale
}

@Composable
private fun PlaceCard(place: Place, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPickScale(selected, interaction)
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(280),
        label = "placeCardColor",
    )
    Surface(
        modifier = Modifier
            .size(width = 240.dp, height = 138.dp)
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = container,
        shadowElevation = if (selected) 10.dp else 1.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(38.dp), RoundedCornerShape(12.dp), color = Color.White.copy(alpha = if (selected) .2f else .8f)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LocalHospital, null, tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary) }
                }
                Text(stringResource(place.category.labelRes), Modifier.padding(start = 9.dp), color = if (selected) Color.White.copy(.82f) else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(
                    visible = selected,
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut(tween(140)) + fadeOut(tween(140)),
                ) {
                    Icon(Icons.Rounded.Check, stringResource(R.string.common_selected), tint = Color.White, modifier = Modifier.size(19.dp))
                }
            }
            Text(place.name, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(place.address, color = if (selected) Color.White.copy(.76f) else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DateCard(date: LocalDate, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPickScale(selected, interaction)
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(260),
        label = "dateCardColor",
    )
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = container,
        shadowElevation = if (selected) 6.dp else 0.dp,
        modifier = Modifier
            .size(width = 74.dp, height = 88.dp)
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(date.format(DateTimeFormatter.ofPattern("EEE")).uppercase(), style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White.copy(.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
            Text(date.format(DateTimeFormatter.ofPattern("MMM")), style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White.copy(.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimeChip(time: LocalTime, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPickScale(selected, interaction)
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(240),
        label = "timeChipColor",
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = container,
        modifier = Modifier
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Row(
            Modifier.padding(start = if (selected) 9.dp else 13.dp, end = 13.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut(tween(120)) + fadeOut(tween(120)),
            ) {
                Row {
                    Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = Color.White)
                    Spacer(Modifier.size(5.dp))
                }
            }
            Text(
                time.format(DateTimeFormatter.ofPattern("HH:mm")),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
