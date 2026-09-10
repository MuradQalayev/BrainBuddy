package com.muradgalayev.brainbuddy.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.domain.scheduling.TimeSuggestion
import java.time.LocalDate

// the 'when should this go?' strip inside the add-event dialog. appears once the title is
// recognisable and disappears again when it isn't, so it never occupies space while it has
// nothing to say. each chip states its own reasoning underneath the time, because a nudge you
// can't account for is one you learn to ignore
@Composable
fun TimeSuggestionStrip(
    palette: CalendarPalette,
    suggestions: List<TimeSuggestion>,
    // the date the dialog is currently writing to. a chip for this day needs no label
    selectedDate: LocalDate,
    // the real today, which is what Tomorrow is relative to, not selectedDate. defaulted rather
    // than threaded from the caller because it's the wall clock, not screen state; passing it in
    // exists so tests can pin it
    today: LocalDate = LocalDate.now(),
    // currently entered HH:mm, used to show which chip is in effect
    selectedStart: String,
    onPick: (TimeSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = suggestions.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = palette.lavender,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    // singular matters: 'Suggested times' over one chip reads as though two more failed to load
                    text = if (suggestions.size == 1) "Suggested time" else "Suggested times",
                    color = palette.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(end = 4.dp),
            ) {
                itemsIndexed(
                    suggestions,
                    key = { _, s -> "${s.date}#${s.startMinutes}" },
                ) { index, suggestion ->
                    val dayLabel = suggestion.dayLabel(selectedDate, today)
                    SuggestionChip(
                        palette = palette,
                        suggestion = suggestion,
                        dayLabel = dayLabel,
                        // a chip for another day can't be the time you've entered, that belongs to the day on screen
                        selected = dayLabel == null && suggestion.startLabel == selectedStart,
                        // the first is the engine's actual pick, the rest are alternatives. weighting it visually
                        // saves the user comparing three chips when the first is usually right
                        primary = index == 0,
                        onClick = { onPick(suggestion) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    palette: CalendarPalette,
    suggestion: TimeSuggestion,
    // non-null when tapping this chip moves the event to another day
    dayLabel: String?,
    selected: Boolean,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val movesDay = dayLabel != null
    val container = when {
        selected -> palette.lavender.copy(alpha = 0.18f)
        // another day gets its own colour rather than the accent. it's a different kind of answer to
        // the question, and it must not read as one more time on the day the user is looking at
        movesDay -> palette.sky.copy(alpha = 0.12f)
        primary -> palette.lavender.copy(alpha = 0.10f)
        else -> palette.pillBg
    }
    val borderColor = when {
        selected -> palette.lavender
        movesDay -> palette.sky
        else -> palette.dialogBorder
    }

    Column(
        modifier = Modifier
            .widthIn(min = 132.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            // one announcement for the whole chip: a screen reader hitting the time and the reason as two
            // nodes would read the day as a list of numbers
            .semantics {
                contentDescription = buildString {
                    // the day comes first for a screen reader too, 'moves this to tomorrow' is the part that
                    // changes what tapping does
                    if (dayLabel != null) append("$dayLabel, ")
                    append("${suggestion.startLabel} to ${suggestion.endLabel}. ")
                    append(suggestion.reasonText)
                }
            },
    ) {
        if (dayLabel != null) {
            Text(
                text = dayLabel.uppercase(),
                color = palette.sky,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
        }
        Text(
            text = "${suggestion.startLabel} – ${suggestion.endLabel}",
            color = palette.ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = suggestion.reasonText,
            color = palette.muted,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
