package com.muradgalayev.brainbuddy.ui.todo

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.ui.accessibility.speaking
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTaskCard(
    palette: TodoPalette,
    task: TaskUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFlagClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // wrapped here rather than at each call site, so both the active and completed lists get it
    // from one place
    val speakingClick = speaking(task.title, onClick)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onEdit()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    val backgroundColor by animateColorAsState(
        targetValue = when (dismissState.dismissDirection) {
            SwipeToDismissBoxValue.StartToEnd -> task.accent
            SwipeToDismissBoxValue.EndToStart -> palette.flagRed
            SwipeToDismissBoxValue.Settled, null -> palette.cardBg
        },
        label = "swipeBackground"
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.todo_edit_task_cd),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Box(modifier = Modifier.size(24.dp))
                    }

                    SwipeToDismissBoxValue.EndToStart -> {
                        Box(modifier = Modifier.size(24.dp))
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.todo_delete_task),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    SwipeToDismissBoxValue.Settled, null -> {
                        Box(modifier = Modifier.size(24.dp))
                        Box(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    ) {
        TaskCard(
            palette = palette,
            task = task,
            onClick = speakingClick,
            onLongClick = onLongClick,
            onFlagClick = onFlagClick
        )
    }
}

