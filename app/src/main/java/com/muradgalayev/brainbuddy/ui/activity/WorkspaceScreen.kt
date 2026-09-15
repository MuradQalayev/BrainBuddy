package com.muradgalayev.brainbuddy.ui.activity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.ui.ai.AiAssistantViewModel
import com.muradgalayev.brainbuddy.ui.ai.offline.OfflineAssistantPanel
import com.muradgalayev.brainbuddy.ui.calendar.CalendarVoiceAssistant
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource

@Composable
fun WorkspaceScreen(
    onNavigate: (String) -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel()
) {
    val voiceViewModel: AiAssistantViewModel = hiltViewModel()
    var showVoiceAssistant by remember { mutableStateOf(false) }
    val savedVoiceHandlePosition by viewModel.voiceHandlePosition.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    // the handle is a Myndora Plus feature. locked, it stays visible and opens the plan screen, so
    // the feature can be found and explained rather than silently missing
    val plusActive = viewModel.plan.collectAsState().value == com.muradgalayev.brainbuddy.domain.model.Plan.Plus
    // locked until the survey is finished, matching the calendar and the nav bar
    val surveyCompleted by voiceViewModel.surveyCompleted.collectAsState()
    LaunchedEffect(Unit) { voiceViewModel.refreshSurveyCompleted() }
    // governs the live-voice overlay only: speech needs a server to send audio to
    val voiceUsable = isOnline && surveyCompleted
    var showOfflineAssistant by remember { mutableStateOf(false) }
    var voiceHandlePosition by rememberSaveable { mutableFloatStateOf(0f) }
    var draggingVoiceHandle by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    LaunchedEffect(savedVoiceHandlePosition, draggingVoiceHandle) {
        if (!draggingVoiceHandle) savedVoiceHandlePosition?.let { voiceHandlePosition = it }
    }
    LaunchedEffect(voiceUsable) {
        if (!voiceUsable) showVoiceAssistant = false
    }

    Box(Modifier.fillMaxSize()) {
        WorkspaceOverviewTab(
            onNavigate = onNavigate,
            viewModel = viewModel,
        )

        AnimatedVisibility(
            // hidden outright when the survey isn't done, a permanently dead handle floating over the
            // screen is worse than no handle
            visible = savedVoiceHandlePosition != null && !showVoiceAssistant && surveyCompleted,
            enter = fadeIn(tween(220)) + scaleIn(initialScale = .82f, animationSpec = tween(260)),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = .86f, animationSpec = tween(170)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset {
                    val travel = with(density) { (configuration.screenHeightDp.dp * .28f).toPx() }
                    IntOffset(0, (voiceHandlePosition * travel).roundToInt())
                },
        ) {
            val maxTravelPx = with(density) { (configuration.screenHeightDp.dp * .28f).toPx() }
            Surface(
                modifier = Modifier
                    .width(46.dp)
                    .height(62.dp)
                    .pointerInput(maxTravelPx) {
                        detectVerticalDragGestures(
                            onDragStart = { draggingVoiceHandle = true },
                            onDragEnd = {
                                draggingVoiceHandle = false
                                viewModel.saveVoiceHandlePosition(voiceHandlePosition)
                            },
                            onDragCancel = { draggingVoiceHandle = false },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                voiceHandlePosition = (voiceHandlePosition + amount / maxTravelPx)
                                    .coerceIn(-1f, 1f)
                            },
                        )
                    }
                    // offline the handle opens the on-device assistant rather than silently doing nothing
                    .clickable {
                        when {
                            !plusActive -> onNavigate(
                                com.muradgalayev.brainbuddy.ui.navigation.planRoute(
                                    com.muradgalayev.brainbuddy.domain.model.PlanFeature.WorkspaceAi,
                                ),
                            )
                            voiceUsable -> showVoiceAssistant = true
                            else -> showOfflineAssistant = true
                        }
                    },
                shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                // the handle looks identical online and off. it used to mute its colours when the connection
                // dropped, which on a flaky network read as the control breaking, and it never was: the tap
                // still lands somewhere useful either way
                // somewhere useful either way.
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                // flat, like the screen edge it's docked to. a shadow made it look like it was floating
                // over the content instead of growing out of the side
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ai),
                        contentDescription = stringResource(R.string.ws_open_voice),
                        modifier = Modifier.size(27.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    if (plusActive) {
                        Box(
                            Modifier.width(14.dp).height(3.dp).background(
                                MaterialTheme.colorScheme.primary.copy(alpha = .45f),
                                CircleShape,
                            ),
                        )
                    } else {
                        Icon(
                            androidx.compose.material.icons.Icons.Rounded.Lock,
                            contentDescription = stringResource(R.string.plan_locked_cd),
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }
        }

        com.muradgalayev.brainbuddy.ui.navigation.DimAppChrome(
            when {
                showVoiceAssistant -> .46f
                showOfflineAssistant -> .32f
                else -> 0f
            },
        )

        AnimatedVisibility(
            visible = showVoiceAssistant,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize(),
        ) {
            CalendarVoiceAssistant(
                viewModel = voiceViewModel,
                onDismiss = { showVoiceAssistant = false },
            )
        }

        AnimatedVisibility(
            visible = showOfflineAssistant,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(130)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showOfflineAssistant = false },
                    ),
                contentAlignment = Alignment.TopCenter,
            ) {
                OfflineAssistantPanel(
                    onDismiss = { showOfflineAssistant = false },
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}
