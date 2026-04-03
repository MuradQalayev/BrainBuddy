package com.muradgalayev.brainbuddy.ui.sharedcomponents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TimePickerField(
    value: String,
    label: String,
    placeholder: String,
    mutedColor: Color,
    accentColor: Color,
    borderColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label, fontSize = 13.sp) },
            placeholder = {
                Text(
                    placeholder,
                    color = mutedColor.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            },
            readOnly = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = borderColor,
                focusedLabelColor = accentColor,
                unfocusedLabelColor = mutedColor,
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            ),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Outlined.AccessTime,
                    contentDescription = null,
                    tint = mutedColor,
                    modifier = Modifier.size(18.dp)
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
        )
    }
}