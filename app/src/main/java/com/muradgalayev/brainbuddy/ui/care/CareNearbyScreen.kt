package com.muradgalayev.brainbuddy.ui.care

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.muradgalayev.brainbuddy.domain.model.Place
import com.muradgalayev.brainbuddy.domain.model.PlaceCategory

@Composable
fun CareNearbyScreen(
    onBack: () -> Unit,
    viewModel: CareNearbyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    val displayedPlaces = remember(state.places, state.activeCategories) {
        if (state.activeCategories.isEmpty()) state.places
        else state.places.filter { it.category in state.activeCategories }
    }

    val placesWithLocation = remember(displayedPlaces) {
        displayedPlaces.filter { it.lat != null && it.lng != null }
    }

    val cameraPositionState = rememberCameraPositionState()
    var showFullscreenMap by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(placesWithLocation) {
        val first = placesWithLocation.firstOrNull()
        if (first != null) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(first.lat!!, first.lng!!),
                12f,
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                title = "Care nearby",
                subtitle = state.cities.firstOrNull { it.id == state.selectedCityId }?.name,
                onBack = onBack,
                onRefresh = { viewModel.refresh() },
            )

            // Error banner
            AnimatedVisibility(
                visible = state.errorMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                ErrorBanner(
                    message = state.errorMessage.orEmpty(),
                    onDismiss = { viewModel.dismissError() },
                )
            }

            // City switcher (always shown when cities exist)
            if (state.cities.isNotEmpty()) {
                CityRow(
                    cities = state.cities.map { it.id to it.name },
                    selectedId = state.selectedCityId,
                    onSelect = viewModel::selectCity,
                )
            }

            // Medication chip
            if (state.medicationName.isNotBlank()) {
                MedicationChip(name = state.medicationName)
            }

            // Map preview — tap anywhere on it to open the fullscreen map.
            // Hide it while the dialog is open so we don't run two GoogleMap composables
            // (each with its own native MapView) sharing the same CameraPositionState.
            if (!showFullscreenMap) {
                MapPreview(
                    cameraPositionState = cameraPositionState,
                    placesWithLocation = placesWithLocation,
                    onExpand = { showFullscreenMap = true },
                )
            }

            // Category filters
            CategoryRow(
                active = state.activeCategories,
                onToggle = viewModel::toggleCategory,
                onClear = viewModel::clearCategories,
            )

            // Body
            when {
                state.isLoading -> LoadingBody()
                state.cities.isEmpty() -> EmptyCitiesBody()
                displayedPlaces.isEmpty() -> EmptyPlacesBody(
                    hasFilters = state.activeCategories.isNotEmpty(),
                    onClearFilters = viewModel::clearCategories,
                )
                else -> PlaceList(
                    places = displayedPlaces,
                    context = context,
                )
            }
        }
    }

    if (showFullscreenMap) {
        FullscreenMapDialog(
            cameraPositionState = cameraPositionState,
            placesWithLocation = placesWithLocation,
            onDismiss = { showFullscreenMap = false },
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = colors.onSurface)
        }
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.LocationCity,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = "Refresh",
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = colors.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun CityRow(
    cities: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cities.forEach { (id, name) ->
            CityPill(
                label = name,
                selected = id == selectedId,
                onClick = { onSelect(id) },
            )
        }
    }
}

@Composable
private fun CityPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) colors.primary else colors.surfaceContainer,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(
            1.dp,
            colors.outlineVariant.copy(alpha = 0.6f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationCity,
                contentDescription = null,
                tint = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = if (selected) colors.onPrimary else colors.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MedicationChip(name: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = colors.primary.copy(alpha = 0.12f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocalPharmacy,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Your meds: $name",
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun MapPreview(
    cameraPositionState: CameraPositionState,
    placesWithLocation: List<Place>,
    onExpand: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    // Outer Box is clickable — a single reliable tap target that opens fullscreen.
    // The GoogleMap sits inside with all interactive gestures disabled so it never
    // eats the outer clickable's touches.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(200.dp)
            .clip(shape)
            .border(
                1.dp,
                colors.outlineVariant.copy(alpha = 0.5f),
                shape,
            )
            .background(colors.surfaceContainer)
            .clickable(onClick = onExpand),
    ) {
        if (placesWithLocation.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Map,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No mapped locations yet",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = false),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = false,
                    scrollGesturesEnabled = false,
                    zoomGesturesEnabled = false,
                    tiltGesturesEnabled = false,
                    rotationGesturesEnabled = false,
                ),
            ) {
                placesWithLocation.forEach { place ->
                    Marker(
                        state = MarkerState(position = LatLng(place.lat!!, place.lng!!)),
                        title = place.name,
                        snippet = place.address,
                    )
                }
            }
            // Visual affordance that the preview is tappable — the outer Box already
            // handles the tap, so this Surface is decorative (no onClick needed).
            Surface(
                shape = CircleShape,
                color = colors.surface.copy(alpha = 0.92f),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Fullscreen,
                        contentDescription = "Expand map",
                        tint = colors.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FullscreenMapDialog(
    cameraPositionState: CameraPositionState,
    placesWithLocation: List<Place>,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            // Fill the screen edge-to-edge instead of the default centered dialog width.
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = false),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = true,
                ),
            ) {
                placesWithLocation.forEach { place ->
                    Marker(
                        state = MarkerState(position = LatLng(place.lat!!, place.lng!!)),
                        title = place.name,
                        snippet = place.address,
                    )
                }
            }
            // Close button — sits below the status bar via statusBarsPadding().
            Surface(
                onClick = onDismiss,
                shape = CircleShape,
                color = colors.surface.copy(alpha = 0.94f),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(14.dp)
                    .size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close map",
                        tint = colors.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    active: Set<PlaceCategory>,
    onToggle: (PlaceCategory) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryChip(
            icon = Icons.Rounded.Apps,
            label = "All",
            selected = active.isEmpty(),
            accent = MaterialTheme.colorScheme.primary,
            onClick = onClear,
        )
        listOf(
            PlaceCategory.Hospital,
            PlaceCategory.Specialist,
            PlaceCategory.Pharmacy,
            PlaceCategory.Asl,
            PlaceCategory.CentroAscolto,
            PlaceCategory.SupportGroup,
        ).forEach { cat ->
            CategoryChip(
                icon = categoryIcon(cat),
                label = cat.label,
                selected = cat in active,
                accent = categoryAccent(cat),
                onClick = { onToggle(cat) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) accent else colors.surfaceContainer,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(
            1.dp,
            colors.outlineVariant.copy(alpha = 0.6f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else accent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = if (selected) Color.White else colors.onSurface,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LoadingBody() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyCitiesBody() {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Place,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No cities to show",
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Add rows to the cities & places tables in Supabase, " +
                "or check that Row-Level Security allows reading them.",
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun EmptyPlacesBody(hasFilters: Boolean, onClearFilters: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.LocalHospital,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (hasFilters) "Nothing matches those filters" else "Nothing to show in this city yet",
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
        )
        if (hasFilters) {
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onClearFilters),
                color = colors.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = "Clear filters",
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PlaceList(places: List<Place>, context: Context) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(places, key = { it.id }) { place ->
            PlaceCard(place = place, context = context)
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun PlaceCard(place: Place, context: Context) {
    val colors = MaterialTheme.colorScheme
    val accent = categoryAccent(place.category)
    val icon = categoryIcon(place.category)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = place.name,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = place.category.label,
                        color = accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (place.address.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                IconLine(Icons.Outlined.LocationOn, place.address, colors.onSurfaceVariant)
            }
            if (place.hours.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                IconLine(Icons.Outlined.Schedule, place.hours, colors.onSurfaceVariant.copy(alpha = 0.85f))
            }
            if (place.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = place.notes,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            val hasAction = place.phone.isNotBlank() || place.website.isNotBlank() ||
                place.email.isNotBlank() || place.address.isNotBlank() ||
                (place.lat != null && place.lng != null)
            if (hasAction) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (place.phone.isNotBlank()) {
                        ActionPill(Icons.Outlined.Phone, "Call", accent) { dial(context, place.phone) }
                    }
                    if (place.address.isNotBlank() || (place.lat != null && place.lng != null)) {
                        ActionPill(Icons.Outlined.LocationOn, "Map", accent) { openMap(context, place) }
                    }
                    if (place.website.isNotBlank()) {
                        ActionPill(Icons.Outlined.Public, "Site", accent) { openUrl(context, place.website) }
                    }
                    if (place.email.isNotBlank()) {
                        ActionPill(Icons.Outlined.Email, "Email", accent) { sendEmail(context, place.email) }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconLine(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp).padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = tint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = tint.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = tint,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun categoryIcon(category: PlaceCategory): ImageVector = when (category) {
    PlaceCategory.Hospital -> Icons.Outlined.LocalHospital
    PlaceCategory.Specialist -> Icons.Outlined.MedicalServices
    PlaceCategory.Pharmacy -> Icons.Outlined.LocalPharmacy
    PlaceCategory.Asl -> Icons.Outlined.LocalHospital
    PlaceCategory.CentroAscolto -> Icons.Outlined.Headphones
    PlaceCategory.SupportGroup -> Icons.Outlined.Group
    PlaceCategory.Other -> Icons.Outlined.Place
}

private fun categoryAccent(category: PlaceCategory): Color = when (category) {
    PlaceCategory.Hospital -> Color(0xFFE57373)
    PlaceCategory.Specialist -> Color(0xFF7FA3C9)
    PlaceCategory.Pharmacy -> Color(0xFF8AAE7E)
    PlaceCategory.Asl -> Color(0xFFE9A86A)
    PlaceCategory.CentroAscolto -> Color(0xFFB28DD9)
    PlaceCategory.SupportGroup -> Color(0xFF6FB3B8)
    PlaceCategory.Other -> Color(0xFF8E9AA0)
}

private fun dial(context: Context, phone: String) {
    val intent = Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun openUrl(context: Context, raw: String) {
    val normalized = if (raw.startsWith("http", ignoreCase = true)) raw else "https://$raw"
    val intent = Intent(Intent.ACTION_VIEW, normalized.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun sendEmail(context: Context, address: String) {
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:$address".toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun openMap(context: Context, place: Place) {
    val uri = if (place.lat != null && place.lng != null) {
        "geo:${place.lat},${place.lng}?q=${place.lat},${place.lng}(${java.net.URLEncoder.encode(place.name, "UTF-8")})"
    } else {
        "geo:0,0?q=${java.net.URLEncoder.encode("${place.name} ${place.address}", "UTF-8")}"
    }
    val intent = Intent(Intent.ACTION_VIEW, uri.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
