package com.muradgalayev.brainbuddy.ui.pomodoro.dialogs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

private const val MIN_MINUTES = 1
private const val MAX_MINUTES = 120

private val Presets = listOf(5, 15, 25, 45, 60, 90)

// one tick per minute. wide enough to grab, tight enough that 2 hours still scrolls fast
private val TickSpacing = 14.dp

// duration picker: a ruler you drag past a fixed centre marker, the way a camera dial works.
// it replaced a stepper-and-dialog because +/- buttons made every choice a counting exercise,
// and here 25 to 50 minutes is one flick, with presets underneath for the usual suspects
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationPickerSheet(
    currentMinutes: Int,
    accentColor: Color,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val startIndex = (currentMinutes.coerceIn(MIN_MINUTES, MAX_MINUTES) - MIN_MINUTES)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // with the row centre-padded, the snapped-to item is whatever sits at the content start, and
    // rounding the part-scrolled offset keeps the readout in step with the marker mid-drag rather
    // than only after the fling settles
    val selectedMinutes by remember {
        derivedStateOf {
            val itemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
            val rounded = if (listState.firstVisibleItemScrollOffset > itemSize / 2) 1 else 0
            (listState.firstVisibleItemIndex + rounded + MIN_MINUTES)
                .coerceIn(MIN_MINUTES, MAX_MINUTES)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { selectedMinutes }
            .distinctUntilChanged()
            .collect { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                        RoundedCornerShape(999.dp),
                    )
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.focus_session_length),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = selectedMinutes.toString(),
                    fontSize = 68.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "min",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            Spacer(Modifier.height(18.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                // half the viewport either side, so item 0 and the last item can both reach the centre marker
                val sidePadding = (maxWidth - TickSpacing) / 2

                LazyRow(
                    state = listState,
                    flingBehavior = flingBehavior,
                    contentPadding = PaddingValues(horizontal = sidePadding),
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp),
                ) {
                    items(MAX_MINUTES - MIN_MINUTES + 1) { index ->
                        RulerTick(
                            minute = index + MIN_MINUTES,
                            selected = index + MIN_MINUTES == selectedMinutes,
                            accentColor = accentColor,
                        )
                    }
                }

                // fade the ruler out at both edges so it reads as a continuous dial running off the sides
                // rather than a list that stops
                val surface = MaterialTheme.colorScheme.surfaceContainerLow
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(56.dp)
                        .height(76.dp)
                        .background(
                            Brush.horizontalGradient(listOf(surface, Color.Transparent))
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(56.dp)
                        .height(76.dp)
                        .background(
                            Brush.horizontalGradient(listOf(Color.Transparent, surface))
                        )
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .width(3.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentColor)
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Presets.forEach { preset ->
                    val selected = preset == selectedMinutes
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) accentColor.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(preset - MIN_MINUTES)
                            }
                        },
                    ) {
                        Text(
                            text = "$preset",
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) accentColor
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { onConfirm(selectedMinutes) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = stringResource(R.string.focus_set_min, selectedMinutes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RulerTick(
    minute: Int,
    selected: Boolean,
    accentColor: Color,
) {
    // every fifth minute gets a tall labelled tick, so the eye has anchors to aim at
    val isMajor = minute % 5 == 0
    val tickHeight = if (isMajor) 34.dp else 20.dp

    val alpha by animateFloatAsState(
        targetValue = if (selected) 1f else if (isMajor) 0.55f else 0.3f,
        animationSpec = tween(160),
        label = "tickAlpha",
    )

    Column(
        modifier = Modifier
            .width(TickSpacing)
            .height(76.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(if (isMajor) 6.dp else 13.dp))

        Box(
            modifier = Modifier
                .width(if (isMajor) 2.5.dp else 1.5.dp)
                .height(tickHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    if (selected) accentColor
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
        )

        if (isMajor) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$minute",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) accentColor
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}
