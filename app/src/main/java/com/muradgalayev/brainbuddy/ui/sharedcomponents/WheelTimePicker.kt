package com.muradgalayev.brainbuddy.ui.sharedcomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val ItemHeight: Dp = 56.dp
private const val VisibleRows = 3
private val WheelHeight: Dp = ItemHeight * VisibleRows
private val ColumnWidth: Dp = 72.dp

@Composable
fun WheelTimePicker(
    initialHour: Int,
    initialMinute: Int,
    onChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val safeHour = initialHour.coerceIn(0, 23)
    val safeMinute = initialMinute.coerceIn(0, 59)

    var hour by remember { mutableIntStateOf(safeHour) }
    var minute by remember { mutableIntStateOf(safeMinute) }

    LaunchedEffect(hour, minute) { onChange(hour, minute) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(WheelHeight),
        contentAlignment = Alignment.Center,
    ) {
        // iOS-style selection band sitting behind the centred row
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.92f)
                .height(ItemHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(accentColor.copy(alpha = 0.12f))
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Wheel(
                initialIndex = safeHour,
                count = 24,
                accentColor = accentColor,
                onIndexChange = { hour = it }
            )
            Text(
                text = ":",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Wheel(
                initialIndex = safeMinute,
                count = 60,
                accentColor = accentColor,
                onIndexChange = { minute = it }
            )
        }
    }
}

@Composable
private fun Wheel(
    initialIndex: Int,
    count: Int,
    accentColor: Color,
    onIndexChange: (Int) -> Unit
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = state)

    val centeredIndex by remember {
        derivedStateOf { state.firstVisibleItemIndex.coerceIn(0, count - 1) }
    }

    LaunchedEffect(centeredIndex) { onIndexChange(centeredIndex) }

    Box(
        modifier = Modifier
            .width(ColumnWidth)
            .height(WheelHeight),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = state,
            flingBehavior = flingBehavior,
            modifier = Modifier
                .fillMaxWidth()
                .height(WheelHeight)
        ) {
            // top spacer pushes the first real value into the centre slot at rest
            item { Spacer(Modifier.height(ItemHeight)) }
            items(count) { index ->
                val distance = abs(index - centeredIndex)
                val isCenter = distance == 0
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ItemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = index.toString().padStart(2, '0'),
                        fontSize = if (isCenter) 34.sp else 22.sp,
                        fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = when (distance) {
                                0 -> 1f
                                1 -> 0.45f
                                else -> 0.2f
                            }
                        )
                    )
                }
            }
            // bottom spacer lets the last real value land in the centre slot
            item { Spacer(Modifier.height(ItemHeight)) }
        }
    }
}