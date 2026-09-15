package com.muradgalayev.brainbuddy.ui.care

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.muradgalayev.brainbuddy.data.location.distanceMeters
import com.muradgalayev.brainbuddy.data.location.formatDistance
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.KeyboardArrowDown
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.muradgalayev.brainbuddy.domain.model.Place
import com.muradgalayev.brainbuddy.domain.model.PlaceCategory
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

@Composable
fun CareNearbyScreen(
    onBack: () -> Unit,
    onReserve: (String?) -> Unit,
    viewModel: CareNearbyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    val filteredPlaces = remember(state.places, state.activeCategories, state.pinnedPlaceIds) {
        var list = state.places
        // AI pin wins: show exactly the places from the chat result
        if (state.pinnedPlaceIds.isNotEmpty()) {
            list = list.filter { it.id in state.pinnedPlaceIds }
        }
        if (state.activeCategories.isNotEmpty()) {
            list = list.filter { it.category in state.activeCategories }
        }
        list
    }

    // user coordinates drive both the map blue dot and the distance order
    val userLoc = remember(state.userLat, state.userLng) {
        val la = state.userLat; val ln = state.userLng
        if (la != null && ln != null) la to ln else null
    }

    // keep the provider order stable while location resolves. re-sorting keyed rows that are
    // already visible makes the list jump under the user. distance still shows on each card
    val displayedPlaces = filteredPlaces
    val distanceLabels = remember(displayedPlaces, userLoc) {
        if (userLoc == null) emptyMap()
        else displayedPlaces.mapNotNull { p ->
            if (p.lat != null && p.lng != null)
                p.id to formatDistance(distanceMeters(userLoc.first, userLoc.second, p.lat, p.lng))
            else null
        }.toMap()
    }

    val placesWithLocation = remember(displayedPlaces) {
        displayedPlaces.filter { it.lat != null && it.lng != null }
    }
    val placeListState = rememberLazyListState()

    // a new filter is a new result set, so start it at the top
    val displayedPlaceIds = remember(displayedPlaces) { displayedPlaces.map(Place::id) }
    LaunchedEffect(displayedPlaceIds) {
        if (displayedPlaceIds.isNotEmpty()) placeListState.scrollToItem(0)
    }

    // never interrupt screen entry with a permission dialog. already granted, location resolves
    // quietly, otherwise the user asks for it
    var hasLocationPermission by remember { mutableStateOf(viewModel.hasLocationPermission()) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (granted) viewModel.requestUserLocation()
    }
    LaunchedEffect(Unit) {
        if (hasLocationPermission) {
            viewModel.requestUserLocation()
        }
    }

    val cameraPositionState = rememberCameraPositionState()
    var showFullscreenMap by rememberSaveable { mutableStateOf(false) }
    // centre on the user if we have them, otherwise on the first place with coordinates
    LaunchedEffect(userLoc, placesWithLocation) {
        val target = userLoc?.let { LatLng(it.first, it.second) }
            ?: placesWithLocation.firstOrNull()?.let { LatLng(it.lat!!, it.lng!!) }
        if (target != null) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(target, 13f)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(colors.primaryContainer.copy(alpha = .28f), colors.background, colors.background),
            ),
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                title = stringResource(R.string.ws_find_care),
                subtitle = state.cities.firstOrNull { it.id == state.selectedCityId }?.name,
                onBack = onBack,
                onReserve = {
                    if (isOnline) onReserve(null)
                    else Toast.makeText(
                        context,
                        context.getString(R.string.care_need_internet),
                        Toast.LENGTH_LONG,
                    ).show()
                },
                onRefresh = viewModel::refresh,
                isOnline = isOnline,
            )

            if (!isOnline) OfflineCareBanner()

            // error banner
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

            // 'filtered by your chat' banner, clears back to the full list
            if (state.pinnedPlaceIds.isNotEmpty()) {
                AiFilterBanner(
                    count = displayedPlaces.size,
                    onClear = { viewModel.clearAiFilter() },
                )
            }

            // medication chip
            if (state.medicationName.isNotBlank()) {
                MedicationChip(name = state.medicationName)
            }

            // map preview and category filters, folding together as the list scrolls. hidden while the
            // dialog is open so we don't run two GoogleMap composables, each with its own native
            // MapView, sharing one CameraPositionState
            if (!showFullscreenMap) {
                CareCollapsingHeader(
                    listState = placeListState,
                    placesWithLocation = placesWithLocation,
                    hasUserLocation = userLoc != null,
                    onExpandMap = { showFullscreenMap = true },
                    onLocate = {
                        if (hasLocationPermission) viewModel.requestUserLocation()
                        else locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    activeCategories = state.activeCategories,
                    onToggleCategory = viewModel::toggleCategory,
                    onClearCategories = viewModel::clearCategories,
                )
            }

            // body
            when {
                state.isLoading -> LoadingBody()
                state.cities.isEmpty() -> EmptyCitiesBody()
                state.selectedCityId == null -> ProfileCityRequiredBody()
                displayedPlaces.isEmpty() -> EmptyPlacesBody(
                    hasFilters = state.activeCategories.isNotEmpty(),
                    onClearFilters = viewModel::clearCategories,
                )
                else -> PlaceList(
                    places = displayedPlaces,
                    distanceLabels = distanceLabels,
                    context = context,
                    listState = placeListState,
                )
            }
        }
    }

    if (showFullscreenMap) {
        FullscreenMapDialog(
            cameraPositionState = cameraPositionState,
            placesWithLocation = placesWithLocation,
            myLocationEnabled = hasLocationPermission,
            onDismiss = { showFullscreenMap = false },
            onBook = { place ->
                if (isOnline) {
                    showFullscreenMap = false
                    onReserve(place.id)
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.care_need_internet),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }
}

@Composable
private fun ProfileCityRequiredBody() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.care_choose_city), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.care_city_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    onReserve: () -> Unit,
    onRefresh: () -> Unit,
    isOnline: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), tint = colors.onSurface)
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
        IconButton(onClick = onRefresh, enabled = isOnline) {
            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.care_refresh), tint = colors.onSurfaceVariant)
        }
        Surface(
            onClick = onReserve,
            shape = RoundedCornerShape(999.dp),
            color = if (isOnline) colors.primaryContainer else colors.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.MedicalServices,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isOnline) colors.onPrimaryContainer else colors.onSurfaceVariant.copy(alpha = .55f),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.care_book),
                    color = if (isOnline) colors.onPrimaryContainer else colors.onSurfaceVariant.copy(alpha = .55f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun OfflineCareBanner() {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.secondaryContainer,
    ) {
        Text(
            text = stringResource(R.string.care_offline),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = colors.onSecondaryContainer,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
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
                    contentDescription = stringResource(R.string.common_dismiss),
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
private fun AiFilterBanner(count: Int, onClear: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.secondaryContainer.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Place,
                contentDescription = null,
                tint = colors.onSecondaryContainer,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (count == 1) stringResource(R.string.care_chat_one)
                else stringResource(R.string.care_chat_many, count),
                color = colors.onSecondaryContainer,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = colors.onSecondaryContainer.copy(alpha = 0.12f),
                modifier = Modifier.clickable(onClick = onClear),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.care_clear_filter),
                        tint = colors.onSecondaryContainer,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.care_show_all),
                        color = colors.onSecondaryContainer,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
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
                    text = stringResource(R.string.care_your_meds, name),
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun MapThumbnail(
    placesWithLocation: List<Place>,
    hasUserLocation: Boolean,
    onLocate: () -> Unit,
    // 1 while the map is full size, 0 once it has shrunk to the round thumbnail
    detailAlpha: () -> Float,
) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(Color(0xFFE3F0E6), Color(0xFFDCECF1), Color(0xFFE8E3F2)),
            ),
        ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // suggests a map without the startup cost of a second native Google Map
            drawRoundRect(Color.White.copy(alpha = .30f), topLeft = androidx.compose.ui.geometry.Offset(size.width * .58f, -12f), size = androidx.compose.ui.geometry.Size(size.width * .34f, size.height * .40f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f))
            drawRoundRect(Color(0xFFC9E0CE).copy(alpha = .65f), topLeft = androidx.compose.ui.geometry.Offset(size.width * .70f, size.height * .56f), size = androidx.compose.ui.geometry.Size(size.width * .31f, size.height * .44f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(30f))
            drawRoundRect(Color.White.copy(alpha = .24f), topLeft = androidx.compose.ui.geometry.Offset(size.width * .39f, size.height * .48f), size = androidx.compose.ui.geometry.Size(size.width * .19f, size.height * .27f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f))
            val mainRoad = Path().apply {
                moveTo(size.width * .35f, -10f)
                cubicTo(size.width * .43f, size.height * .23f, size.width * .48f, size.height * .58f, size.width * .62f, size.height + 10f)
            }
            drawPath(mainRoad, Color.White.copy(alpha = .78f), style = Stroke(width = 13f))
            drawPath(mainRoad, Color(0xFFB9CDD0).copy(alpha = .55f), style = Stroke(width = 2f))
            val crossRoad = Path().apply {
                moveTo(size.width * .28f, size.height * .66f)
                cubicTo(size.width * .52f, size.height * .58f, size.width * .72f, size.height * .42f, size.width + 10f, size.height * .46f)
            }
            drawPath(crossRoad, Color.White.copy(alpha = .72f), style = Stroke(width = 10f))
        }

        // everything written on the map fades out well before the thumbnail is small enough to
        // overlap. alpha is read in the draw phase, so this costs a layer invalidation rather than
        // a recomposition
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = detailAlpha() }) {
            MapPreviewPin(
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 54.dp, top = 28.dp),
                color = Color(0xFFE57373),
                icon = Icons.Outlined.LocalHospital,
            )
            MapPreviewPin(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 120.dp, top = 18.dp),
                color = Color(0xFF0D9488),
                icon = Icons.Outlined.MedicalServices,
            )
            MapPreviewPin(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 22.dp),
                color = Color(0xFF0D9488),
                icon = Icons.Outlined.LocalPharmacy,
            )
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Surface(shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = .72f)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, null, tint = Color(0xFF47705A), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (placesWithLocation.isEmpty()) stringResource(R.string.care_map) else stringResource(R.string.care_mapped, placesWithLocation.size),
                            color = Color(0xFF385547), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Column {
                    Text(stringResource(R.string.care_see_around), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color(0xFF20352A))
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Fullscreen, null, tint = Color(0xFF536B60), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.care_tap_explore_map), style = MaterialTheme.typography.bodySmall, color = Color(0xFF536B60))
                    }
                }
            }
            Surface(
                onClick = onLocate,
                shape = CircleShape,
                color = if (hasUserLocation) Color(0xFF47705A) else Color.White.copy(alpha = .92f),
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(40.dp),
                shadowElevation = 3.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        if (hasUserLocation) stringResource(R.string.care_location_found) else stringResource(R.string.care_use_location),
                        tint = if (hasUserLocation) Color.White else Color(0xFF47705A),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MapPreviewPin(modifier: Modifier, color: Color, icon: ImageVector) {
    Surface(modifier = modifier.size(34.dp), shape = CircleShape, color = color, shadowElevation = 4.dp) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun FullscreenMapDialog(
    cameraPositionState: CameraPositionState,
    placesWithLocation: List<Place>,
    myLocationEnabled: Boolean,
    onDismiss: () -> Unit,
    onBook: (Place) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val mapScope = rememberCoroutineScope()
    var selectedPlace by remember { mutableStateOf<Place?>(null) }
    var spiderPlaceIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var spiderOpen by remember { mutableStateOf(false) }
    val spiderExpansion by animateFloatAsState(
        targetValue = if (spiderOpen) 1f else 0f,
        animationSpec = tween(280),
        label = "marker_spiderfy",
    )
    val spiderPlaces = remember(spiderPlaceIds, placesWithLocation) {
        placesWithLocation.filter { it.id in spiderPlaceIds }
    }
    val spiderPositions = remember(spiderPlaces, spiderExpansion, cameraPositionState.position.zoom) {
        spiderfyPositions(spiderPlaces, spiderExpansion, cameraPositionState.position.zoom)
    }
    val overlapGroups = remember(placesWithLocation, cameraPositionState.position.zoom) {
        visualOverlapGroups(placesWithLocation, cameraPositionState.position.zoom)
    }
    val overlapGroupByPlaceId = remember(overlapGroups) {
        overlapGroups.flatMap { group -> group.map { it.id to group } }.toMap()
    }
    LaunchedEffect(spiderPlaceIds) { spiderOpen = spiderPlaceIds.isNotEmpty() }
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving && spiderPlaceIds.isNotEmpty()) {
            spiderOpen = false
            delay(280)
            spiderPlaceIds = emptySet()
            selectedPlace = null
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            // fill the screen edge to edge instead of the default centred dialog width
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = myLocationEnabled),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    myLocationButtonEnabled = myLocationEnabled,
                    mapToolbarEnabled = true,
                ),
                onMapClick = {
                    mapScope.launch {
                        spiderOpen = false
                        selectedPlace = null
                        delay(280)
                        spiderPlaceIds = emptySet()
                    }
                },
            ) {
                spiderPlaces.forEach { place ->
                    val original = LatLng(place.lat!!, place.lng!!)
                    val spread = spiderPositions[place.id] ?: original
                    Polyline(
                        points = listOf(original, spread),
                        color = categoryAccent(place.category).copy(alpha = .62f),
                        width = 3f,
                        zIndex = 1f,
                    )
                }
                placesWithLocation.forEach { place ->
                    val overlapGroup = overlapGroupByPlaceId[place.id].orEmpty()
                    val groupIsExpanded = place.id in spiderPlaceIds
                    val isCollapsedCluster = overlapGroup.size > 1 && !groupIsExpanded
                    val isClusterRepresentative = overlapGroup.firstOrNull()?.id == place.id
                    if (isCollapsedCluster && !isClusterRepresentative) return@forEach
                    val clusterIcon = if (isCollapsedCluster) rememberClusterMarkerIcon(overlapGroup.size) else null
                    val displayPosition = spiderPositions[place.id]
                        ?: LatLng(place.lat!!, place.lng!!)
                    val markerState = remember(place.id) {
                        MarkerState(position = LatLng(place.lat!!, place.lng!!))
                    }
                    SideEffect { markerState.position = displayPosition }
                    Marker(
                        state = markerState,
                        title = place.name,
                        snippet = place.address,
                        onClick = {
                            if (place.id in spiderPlaceIds) {
                                selectedPlace = place
                            } else {
                                val overlapping = overlapGroup.ifEmpty { listOf(place) }
                                if (overlapping.size > 1) {
                                    selectedPlace = null
                                    spiderOpen = false
                                    spiderPlaceIds = overlapping.mapTo(linkedSetOf(), Place::id)
                                } else {
                                    spiderOpen = false
                                    spiderPlaceIds = emptySet()
                                    selectedPlace = place
                                }
                            }
                            true
                        },
                        icon = clusterIcon,
                        zIndex = if (place.id in spiderPlaceIds) 3f else 2f,
                    )
                }
            }
            // close button, sits below the status bar via statusBarsPadding()
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
                        contentDescription = stringResource(R.string.care_close_map),
                        tint = colors.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            selectedPlace?.let { place ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = colors.surface,
                    shadowElevation = 10.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            color = categoryAccent(place.category).copy(alpha = .15f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    categoryIcon(place.category),
                                    contentDescription = null,
                                    tint = categoryAccent(place.category),
                                )
                            }
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(place.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                place.address.ifBlank { stringResource(place.category.labelRes) },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Surface(
                            onClick = { onBook(place) },
                            shape = RoundedCornerShape(999.dp),
                            color = colors.primary,
                        ) {
                            Text(
                                stringResource(R.string.care_book),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                color = colors.onPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberClusterMarkerIcon(count: Int): BitmapDescriptor {
    val density = LocalDensity.current.density
    return remember(count, density) {
        val size = (46 * density).toInt().coerceAtLeast(46)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val center = size / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(24, 169, 209) }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f * density
        }
        canvas.drawCircle(center, center, center - 3f * density, fill)
        canvas.drawCircle(center, center, center - 3f * density, border)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 16f * density
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val baseline = center - (text.ascent() + text.descent()) / 2f
        canvas.drawText(count.toString(), center, baseline, text)
        BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}

private fun visualOverlapGroups(places: List<Place>, zoom: Float): List<List<Place>> {
    val remaining = places.toMutableList()
    val groups = mutableListOf<List<Place>>()
    while (remaining.isNotEmpty()) {
        val group = mutableListOf(remaining.removeAt(0))
        var index = 0
        while (index < group.size) {
            val anchor = group[index++]
            val connected = remaining.filter { candidate ->
                val lat = anchor.lat ?: return@filter false
                val lng = anchor.lng ?: return@filter false
                val candidateLat = candidate.lat ?: return@filter false
                val candidateLng = candidate.lng ?: return@filter false
                distanceMeters(lat, lng, candidateLat, candidateLng) <= metersPerPixel(lat, zoom) * 46.0
            }
            group += connected
            remaining.removeAll(connected.toSet())
        }
        groups += group
    }
    return groups
}

private fun overlappingPlaces(anchor: Place, places: List<Place>, zoom: Float): List<Place> {
    val lat = anchor.lat ?: return listOf(anchor)
    val lng = anchor.lng ?: return listOf(anchor)
    // marker pins are about 40-48 px wide. convert that to metres at the current latitude and
    // zoom so pins that look overlapped actually fan out
    val overlapRadiusMeters = metersPerPixel(lat, zoom) * 46.0
    return places.filter { candidate ->
        val candidateLat = candidate.lat ?: return@filter false
        val candidateLng = candidate.lng ?: return@filter false
        distanceMeters(lat, lng, candidateLat, candidateLng) <= overlapRadiusMeters
    }
}

private fun spiderfyPositions(
    places: List<Place>,
    expansion: Float,
    zoom: Float,
): Map<String, LatLng> {
    if (places.size < 2) return emptyMap()
    val centerLat = places.mapNotNull(Place::lat).average()
    val centerLng = places.mapNotNull(Place::lng).average()
    // keeps the spread near 58 screen pixels at any zoom, a fixed metre figure vanished at city zoom
    val radiusMeters = metersPerPixel(centerLat, zoom) * 58.0 * expansion
    val latitudeRadians = centerLat * PI / 180.0
    return places.mapIndexed { index, place ->
        val angle = -PI / 2.0 + (2.0 * PI * index / places.size)
        val northMeters = sin(angle) * radiusMeters
        val eastMeters = cos(angle) * radiusMeters
        val latOffset = northMeters / 111_320.0
        val lngOffset = eastMeters / (111_320.0 * cos(latitudeRadians).coerceAtLeast(.15))
        place.id to LatLng(centerLat + latOffset, centerLng + lngOffset)
    }.toMap()
}

private fun metersPerPixel(latitude: Double, zoom: Float): Double =
    156_543.03392 * cos(latitude * PI / 180.0) / 2.0.pow(zoom.toDouble())

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
            label = stringResource(R.string.common_all),
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
                label = stringResource(cat.labelRes),
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
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(3) { index ->
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .7f)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .10f)))
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth(if (index == 1) .72f else .58f).height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)))
                        Box(Modifier.fillMaxWidth(.4f).height(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .05f)))
                    }
                }
            }
        }
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
            text = stringResource(R.string.care_no_cities),
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.care_no_cities_body),
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
            text = if (hasFilters) stringResource(R.string.care_no_match) else stringResource(R.string.care_nothing_city),
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
                    text = stringResource(R.string.care_clear_filters),
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
private fun PlaceList(
    places: List<Place>,
    distanceLabels: Map<String, String>,
    context: Context,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(places, key = { it.id }) { place ->
            PlaceCard(place = place, distance = distanceLabels[place.id], context = context)
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

// name, distance and the contact buttons are always there, they're what you pick a place by.
// address, hours and notes fold away behind the chevron, open on every card they made the list
// a wall of text
@Composable
private fun PlaceCard(place: Place, distance: String?, context: Context) {
    val colors = MaterialTheme.colorScheme
    val accent = categoryAccent(place.category)
    val icon = categoryIcon(place.category)
    val hasDetails = place.address.isNotBlank() || place.hours.isNotBlank() || place.notes.isNotBlank()
    var expanded by rememberSaveable(place.id) { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        shadowElevation = 1.dp,
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
                        text = stringResource(place.category.labelRes),
                        color = accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (distance != null) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = accent.copy(alpha = 0.12f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = distance,
                                color = accent,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                if (hasDetails) {
                    Spacer(Modifier.width(4.dp))
                    PlaceDetailsChevron(expanded = expanded, onClick = { expanded = !expanded })
                }
            }

            AnimatedVisibility(
                visible = expanded && hasDetails,
                enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(220)) + fadeIn(tween(180)),
                exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(180)) + fadeOut(tween(100)),
            ) {
                Column {
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
                }
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
                        ActionPill(Icons.Outlined.Phone, stringResource(R.string.care_call), accent) { dial(context, place.phone) }
                    }
                    if (place.address.isNotBlank() || (place.lat != null && place.lng != null)) {
                        ActionPill(Icons.Outlined.LocationOn, stringResource(R.string.care_map_short), accent) { openMap(context, place) }
                    }
                    if (place.website.isNotBlank()) {
                        ActionPill(Icons.Outlined.Public, stringResource(R.string.care_site), accent) { openUrl(context, place.website) }
                    }
                    if (place.email.isNotBlank()) {
                        ActionPill(Icons.Outlined.Email, stringResource(R.string.common_email), accent) { sendEmail(context, place.email) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceDetailsChevron(expanded: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label = "place_details_chevron",
    )
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = if (expanded) stringResource(R.string.cal_hide_details) else stringResource(R.string.cal_show_details),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = rotation },
        )
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
    PlaceCategory.Specialist -> Color(0xFF0D9488)
    PlaceCategory.Pharmacy -> Color(0xFF0D9488)
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

// collapsing header. scrolling the list shrinks the map into a round thumbnail on the left
// and folds the category chips into a hamburger.
// built to cost almost nothing per frame: the collapse fraction is a derivedStateOf that is
// never read during composition, only inside layout {} and graphicsLayer {} lambdas, so a
// scroll re-runs layout and draw for this subtree and skips recomposition entirely.
// the exception is collapsed, a boolean that flips once at the halfway point. swapping chips
// for a hamburger is a change of content, so composition has to see it, but that happens
// twice per collapse rather than sixty times a second

// scroll distance the header folds over. generous on purpose: at 150dp the whole fold
// happened inside a flick and read as a glitch, you saw the start and the end but nothing
// in between
private val HEADER_COLLAPSE_DISTANCE = 260.dp

// gap under the collapsed bar so the list doesn't crowd the thumbnail
private val COLLAPSED_BOTTOM_GAP = 14.dp
private val MAP_EXPANDED_HEIGHT = 184.dp
private val MAP_COLLAPSED_SIZE = 52.dp

@Composable
private fun rememberCollapseFraction(listState: LazyListState): State<Float> {
    val distancePx = with(LocalDensity.current) { HEADER_COLLAPSE_DISTANCE.toPx() }
    return remember(listState, distancePx) {
        derivedStateOf {
            // past the first item it's always fully collapsed, within it the fraction tracks the offset
            val raw = if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / distancePx).coerceIn(0f, 1f)
            // eased, not linear. a straight ratio starts and stops abruptly because it's at full speed
            // the instant you touch the list, this settles into both ends the way a real object would
            FastOutSlowInEasing.transform(raw)
        }
    }
}

// interpolates the map's own size in the layout phase. measuring at the interpolated size
// rather than scaling a full-size layer keeps the thumbnail crisp and lets the row beside it
// reflow, at the cost of one cheap re-measure per frame
private fun Modifier.collapsingMapSize(fraction: () -> Float): Modifier =
    layout { measurable, constraints ->
    val f = fraction()
    // the row hands us the full width, so the expanded size needs no measured state of its own
    val expandedW = constraints.maxWidth.toFloat()
    val collapsedPx = MAP_COLLAPSED_SIZE.toPx()
    val expandedH = MAP_EXPANDED_HEIGHT.toPx()
    val w = androidx.compose.ui.util.lerp(expandedW, collapsedPx, f).roundToInt().coerceAtLeast(1)
    val h = androidx.compose.ui.util.lerp(expandedH, collapsedPx, f).roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

@Composable
private fun CareCollapsingHeader(
    listState: LazyListState,
    placesWithLocation: List<Place>,
    hasUserLocation: Boolean,
    onExpandMap: () -> Unit,
    onLocate: () -> Unit,
    activeCategories: Set<PlaceCategory>,
    onToggleCategory: (PlaceCategory) -> Unit,
    onClearCategories: () -> Unit,
) {
    val fractionState = rememberCollapseFraction(listState)
    val fraction = remember(fractionState) { { fractionState.value } }
    // the one composition-phase read: a content swap, not a per-frame value
    val collapsed by remember(fractionState) {
        derivedStateOf { fractionState.value > .55f }
    }
    var filtersOpen by remember { mutableStateOf(false) }
    // nothing to fold away once it's shut
    LaunchedEffect(collapsed) { if (!collapsed) filtersOpen = false }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
    // measured once per width change, not per frame. the map's full-size width, so the thumbnail
    // can crop rather than squash
    val expandedMapWidth = maxWidth - 32.dp
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .collapsingMapSize(fraction)
                    .graphicsLayer {
                        // squares off into a circle as it shrinks. reading fraction here keeps it in the draw phase
                        val f = fraction()
                        clip = true
                        shape = RoundedCornerShape(
                            androidx.compose.ui.unit.lerp(28.dp, MAP_COLLAPSED_SIZE / 2, f),
                        )
                        shadowElevation = androidx.compose.ui.util.lerp(2f, 8f, f)
                    }
                    .clickable(onClick = onExpandMap),
                contentAlignment = Alignment.Center,
            ) {
                // the map keeps its full-size layout and is scaled down evenly, with the shrinking parent
                // cropping the edges. letting it reflow into the box squashed the roads flat instead, since
                // the width fell away six times faster than the height, which is what looked wrong
                Box(
                    Modifier
                        .requiredSize(expandedMapWidth, MAP_EXPANDED_HEIGHT)
                        .graphicsLayer {
                            val f = fraction()
                            val minScale = MAP_COLLAPSED_SIZE.toPx() / MAP_EXPANDED_HEIGHT.toPx()
                            val scale = androidx.compose.ui.util.lerp(1f, minScale, f)
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    MapThumbnail(
                        placesWithLocation = placesWithLocation,
                        hasUserLocation = hasUserLocation,
                        onLocate = onLocate,
                        detailAlpha = { 1f - (fraction() / .6f).coerceAtMost(1f) },
                    )
                }
            }

            if (collapsed) {
                Spacer(Modifier.width(12.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = ((fraction() - .55f) / .45f).coerceIn(0f, 1f) },
                ) {
                    Text(
                        stringResource(R.string.care_map),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        if (placesWithLocation.isEmpty()) stringResource(R.string.care_tap_explore)
                        else stringResource(R.string.care_mapped, placesWithLocation.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                FilterMenuButton(
                    activeCount = activeCategories.size,
                    expanded = filtersOpen,
                    onToggle = { filtersOpen = !filtersOpen },
                    onDismiss = { filtersOpen = false },
                    active = activeCategories,
                    onToggleCategory = onToggleCategory,
                    onClearCategories = onClearCategories,
                    modifier = Modifier.graphicsLayer {
                        val a = ((fraction() - .55f) / .45f).coerceIn(0f, 1f)
                        alpha = a
                        scaleX = .7f + a * .3f
                        scaleY = .7f + a * .3f
                    },
                )
            }
        }

        // the chip row shrinks its own height to nothing, so the list slides up rather than jumping
        if (!collapsed) {
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val f = fraction()
                        val h = (placeable.height * (1f - f / .55f).coerceIn(0f, 1f)).roundToInt()
                        layout(placeable.width, h) { placeable.place(0, 0) }
                    }
                    .graphicsLayer {
                        alpha = (1f - fraction() / .55f).coerceIn(0f, 1f)
                        // without this the chips spill over the list below
                        clip = true
                    },
            ) {
                CategoryRow(
                    active = activeCategories,
                    onToggle = onToggleCategory,
                    onClear = onClearCategories,
                )
            }
        }

        // opens up as the header folds, otherwise the list sits right against the thumbnail and the
        // two read as one crowded block
        val gapPx = with(LocalDensity.current) { COLLAPSED_BOTTOM_GAP.toPx() }
        Spacer(
            Modifier.layout { measurable, constraints ->
                val h = (fraction() * gapPx).roundToInt()
                val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, h))
                layout(placeable.width, h) { placeable.place(0, 0) }
            },
        )
    }
    }
}

@Composable
private fun FilterMenuButton(
    activeCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    active: Set<PlaceCategory>,
    onToggleCategory: (PlaceCategory) -> Unit,
    onClearCategories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Surface(
            onClick = onToggle,
            shape = CircleShape,
            color = if (activeCount > 0) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(44.dp),
            shadowElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Menu,
                    contentDescription = if (activeCount > 0) stringResource(R.string.care_filters_active, activeCount) else stringResource(R.string.todo_filters),
                    tint = if (activeCount > 0) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(20.dp),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.care_all_categories), fontWeight = FontWeight.Bold) },
                onClick = { onClearCategories(); onDismiss() },
                leadingIcon = { Icon(Icons.Rounded.Apps, null) },
                trailingIcon = {
                    if (active.isEmpty()) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                },
            )
            listOf(
                PlaceCategory.Hospital,
                PlaceCategory.Specialist,
                PlaceCategory.Pharmacy,
                PlaceCategory.Asl,
                PlaceCategory.CentroAscolto,
                PlaceCategory.SupportGroup,
            ).forEach { cat ->
                DropdownMenuItem(
                    text = { Text(stringResource(cat.labelRes)) },
                    onClick = { onToggleCategory(cat) },
                    leadingIcon = { Icon(categoryIcon(cat), null, tint = categoryAccent(cat)) },
                    trailingIcon = {
                        if (cat in active) {
                            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
            }
        }
    }
}
