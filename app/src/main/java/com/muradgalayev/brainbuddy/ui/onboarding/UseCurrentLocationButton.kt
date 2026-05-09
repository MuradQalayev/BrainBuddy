package com.muradgalayev.brainbuddy.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun UseCurrentLocationRow(
    status: LocationLookupStatus,
    onRequest: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val isLocating = status is LocationLookupStatus.Locating

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onRequest()
    }

    OutlinedButton(
        onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
        enabled = !isLocating,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        if (isLocating) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
                color = colors.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text("Locating…")
        } else {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Use my current location", fontWeight = FontWeight.SemiBold)
        }
    }

    val message: String? = when (status) {
        LocationLookupStatus.Idle, LocationLookupStatus.Locating -> null
        is LocationLookupStatus.Matched ->
            if (status.cityName.equals(status.viaName, ignoreCase = true))
                "Got it: ${status.cityName}"
            else
                "Got it: ${status.cityName} (matched from ${status.viaName})"
        is LocationLookupStatus.NoMatch ->
            "We detected ${status.detected}, but it's not in our list — pick the closest one."
        LocationLookupStatus.PermissionDenied ->
            "Location access denied. Allow it from app settings or pick your city manually."
        LocationLookupStatus.LocationServicesOff ->
            "Location is turned off in your phone's settings."
        is LocationLookupStatus.Error -> status.message
    }

    val action: Pair<String, () -> Unit>? = when (status) {
        LocationLookupStatus.LocationServicesOff -> "Turn on" to {
            context.startActivity(
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        LocationLookupStatus.PermissionDenied -> "Open settings" to {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        else -> null
    }

    if (message != null) {
        Spacer(Modifier.height(8.dp))
        val tint = when (status) {
            is LocationLookupStatus.Matched -> colors.primary
            is LocationLookupStatus.NoMatch,
            LocationLookupStatus.PermissionDenied,
            LocationLookupStatus.LocationServicesOff,
            is LocationLookupStatus.Error -> colors.error
            else -> colors.onSurfaceVariant
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = tint,
                modifier = Modifier.weight(1f),
            )
            if (action != null) {
                TextButton(
                    onClick = action.second,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp, vertical = 4.dp
                    ),
                ) {
                    Text(
                        action.first,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
