@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package net.sagberg.tournarrat.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import net.sagberg.tournarrat.BuildConfig
import net.sagberg.tournarrat.core.model.AiProvider
import net.sagberg.tournarrat.core.model.InsightFrequency
import net.sagberg.tournarrat.core.model.InsightGenerationMetadata
import net.sagberg.tournarrat.core.model.InsightRecord
import net.sagberg.tournarrat.core.model.InsightTone
import net.sagberg.tournarrat.core.model.InterestTopic
import net.sagberg.tournarrat.core.model.OperatingMode
import net.sagberg.tournarrat.detail.DetailUiState
import net.sagberg.tournarrat.detail.DetailViewModel
import net.sagberg.tournarrat.history.HistoryViewModel
import net.sagberg.tournarrat.home.HomeMapState
import net.sagberg.tournarrat.home.HomeViewModel
import net.sagberg.tournarrat.home.ResolvedPlaceDebug
import net.sagberg.tournarrat.navigation.DetailRoute
import net.sagberg.tournarrat.navigation.HistoryRoute
import net.sagberg.tournarrat.navigation.HomeRoute
import net.sagberg.tournarrat.navigation.SettingsRoute
import net.sagberg.tournarrat.onboarding.OnboardingViewModel
import net.sagberg.tournarrat.settings.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun TournarratApp() {
    val rootViewModel = koinViewModel<RootViewModel>()
    val onboardingCompleted by rootViewModel.onboardingCompleted.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        if (!onboardingCompleted) {
            OnboardingRoute()
        } else {
            MainRoute()
        }
    }
}

@Composable
private fun OnboardingRoute() {
    val viewModel = koinViewModel<OnboardingViewModel>()
    var step by rememberSaveable { mutableIntStateOf(0) }
    val pages = listOf(
        "Tournarrat notices when you reach a new area and gives short, relevant context instead of a constant stream of chatter.",
        "Live mode speaks through normal Android audio routing. Popup mode keeps things asynchronous. Both respect your tone and topic preferences.",
        "Add an OpenAI key later in Settings, or start in demo mode and test the full manual flow immediately.",
    )

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Welcome to Tournarrat", style = MaterialTheme.typography.headlineMedium)
                Text(
                    pages[step],
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Step ${step + 1} of ${pages.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step > 0) {
                    OutlinedButton(onClick = { step -= 1 }) {
                        Text("Back")
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    modifier = Modifier.testTag(
                        if (step == pages.lastIndex) UiTags.OnboardingFinishButton else UiTags.OnboardingNextButton,
                    ),
                    onClick = {
                        if (step == pages.lastIndex) {
                            viewModel.completeOnboarding()
                        } else {
                            step += 1
                        }
                    },
                ) {
                    Text(if (step == pages.lastIndex) "Start exploring" else "Next")
                }
            }
        }
    }
}

@Composable
private fun MainRoute() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val topLevelDestinations = listOf(
        TopLevelDestination("Home", Icons.Rounded.AutoStories, HomeRoute),
        TopLevelDestination("History", Icons.Rounded.History, HistoryRoute),
        TopLevelDestination("Settings", Icons.Rounded.Settings, SettingsRoute),
    )

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        bottomBar = {
            NavigationBar {
                topLevelDestinations.forEach { destination ->
                    NavigationBarItem(
                        modifier = Modifier.testTag(
                            when (destination.route) {
                                HomeRoute -> UiTags.TabHome
                                HistoryRoute -> UiTags.TabHistory
                                SettingsRoute -> UiTags.TabSettings
                                else -> destination.label.lowercase()
                            },
                        ),
                        selected = currentDestination?.hasRoute(destination.route::class) == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    modifier = Modifier.padding(padding),
                    onOpenDetail = { id -> navController.navigate(DetailRoute(id)) },
                )
            }
            composable<HistoryRoute> {
                HistoryScreen(
                    modifier = Modifier.padding(padding),
                    onOpenDetail = { id -> navController.navigate(DetailRoute(id)) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(modifier = Modifier.padding(padding))
            }
            composable<DetailRoute> { entry ->
                val route = entry.toRoute<DetailRoute>()
                DetailScreen(
                    modifier = Modifier.padding(padding),
                    insightId = route.insightId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenDetail: (String) -> Unit,
) {
    val viewModel = koinViewModel<HomeViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var permissionsGranted by remember { mutableStateOf(context.hasLocationPermission()) }
    var overflowExpanded by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionsGranted = result.values.any { it }
        if (permissionsGranted) {
            viewModel.generateInsight()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier.testTag(UiTags.HomeScreen),
        topBar = {
            TopAppBar(
                title = { Text("Around you") },
                actions = {
                    HomeModeToggle(
                        selectedMode = uiState.preferences.mode,
                        onSelectMode = viewModel::setMode,
                    )
                    Box {
                        IconButton(
                            modifier = Modifier.testTag(UiTags.HomeOverflowButton),
                            onClick = { overflowExpanded = true },
                        ) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                        ) {
                            DropdownMenuItem(
                                modifier = Modifier.testTag(UiTags.HomeDiagnosticsMenuItem),
                                text = { Text("Diagnostics") },
                                onClick = {
                                    overflowExpanded = false
                                    viewModel.showDiagnostics()
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        if (uiState.isDiagnosticsVisible) {
            DiagnosticsSheet(
                resolved = uiState.latestResolvedPlace,
                onDismiss = viewModel::hideDiagnostics,
            )
        }
        uiState.latestInsight?.takeIf { uiState.isInsightSheetVisible }?.let { record ->
            LatestInsightSheet(
                insight = record,
                isNarrating = uiState.isNarrating,
                onDismiss = viewModel::hideLatestInsight,
                onToggleNarration = viewModel::toggleNarration,
                onOpenDetail = {
                    viewModel.hideLatestInsight()
                    onOpenDetail(record.id)
                },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag(UiTags.HomeMapHero),
        ) {
            HomeMapHero(
                mapState = uiState.mapState,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (uiState.isGenerating) {
                    Card {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Generating insight", style = MaterialTheme.typography.titleMedium)
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                } else if (uiState.latestInsight != null && !uiState.isInsightSheetVisible) {
                    OutlinedButton(onClick = viewModel::showLatestInsight) {
                        Text("Open latest insight")
                    }
                }
                ManualInsightFab(
                    isGenerating = uiState.isGenerating,
                    hasPermission = permissionsGranted,
                    onGenerate = {
                        permissionsGranted = context.hasLocationPermission()
                        if (permissionsGranted) {
                            viewModel.generateInsight()
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                ),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    modifier: Modifier = Modifier,
    onOpenDetail: (String) -> Unit,
) {
    val viewModel = koinViewModel<HistoryViewModel>()
    val history by viewModel.history.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.testTag(UiTags.HistoryScreen),
        topBar = {
            TopAppBar(
                title = { Text("History") },
                actions = {
                    if (history.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearAll) {
                            Text("Clear all")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (history.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                title = "No history yet",
                body = "Generate your first manual insight from the Home tab.",
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(history, key = { it.id }) { record ->
                    Card(onClick = { onOpenDetail(record.id) }) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(record.title, style = MaterialTheme.typography.titleMedium)
                            Text(record.placeContext.areaName, color = MaterialTheme.colorScheme.primary)
                            Text(
                                record.summary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onOpenDetail(record.id) }) {
                                    Text("Open")
                                }
                                TextButton(onClick = { viewModel.delete(record.id) }) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(
    modifier: Modifier = Modifier,
    insightId: String,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<DetailViewModel>()
    val record by viewModel.record(insightId).collectAsStateWithLifecycle()
    val detailUiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showMetadata by rememberSaveable(insightId) { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.testTag(UiTags.DetailScreen),
        topBar = {
            TopAppBar(
                title = { Text("Insight detail") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    DetailNarrationAction(
                        uiState = detailUiState,
                        enabled = record != null,
                        onToggleNarration = { viewModel.toggleNarration(record) },
                    )
                },
            )
        },
    ) { padding ->
        val current = record
        if (current == null) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                title = "Insight not found",
                body = "It may have been deleted from local history.",
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(current.title, style = MaterialTheme.typography.headlineSmall)
                    Text(current.placeContext.areaName, color = MaterialTheme.colorScheme.primary)
                }
                item {
                    DetailSection("Summary", current.summary)
                }
                item {
                    DetailSection("Why it matters", current.whyItMatters)
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { showMetadata = !showMetadata }) {
                            Text(if (showMetadata) "Hide metadata" else "Metadata")
                        }
                        if (showMetadata) {
                            MetadataSection(
                                metadata = current.generationMetadata,
                                confidenceNote = current.confidenceNote,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.validationMessage) {
        uiState.validationMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier.testTag(UiTags.SettingsScreen),
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsSection("Mode") {
                OptionChips(
                    options = OperatingMode.entries,
                    selected = uiState.preferences.mode,
                    labelFor = { it.name.lowercase().replaceFirstChar(Char::titlecase) },
                    onSelect = viewModel::setMode,
                )
            }
            SettingsSection("Frequency") {
                OptionChips(
                    options = InsightFrequency.entries,
                    selected = uiState.preferences.frequency,
                    labelFor = { it.name.lowercase().replaceFirstChar(Char::titlecase) },
                    onSelect = viewModel::setFrequency,
                )
            }
            SettingsSection("Provider") {
                OptionChips(
                    options = AiProvider.entries,
                    selected = uiState.preferences.aiProvider,
                    labelFor = {
                        when (it) {
                            AiProvider.OPEN_AI -> "OpenAI"
                            AiProvider.DEMO -> "Demo"
                        }
                    },
                    onSelect = viewModel::setProvider,
                )
            }
            SettingsSection("OpenAI API key") {
                OutlinedTextField(
                    value = uiState.openAiKey,
                    onValueChange = viewModel::setOpenAiKey,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("sk-...") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::saveOpenAiKey) {
                        Text("Save")
                    }
                    OutlinedButton(
                        onClick = viewModel::validateOpenAiKey,
                        enabled = !uiState.isValidating,
                    ) {
                        Text(if (uiState.isValidating) "Validating..." else "Validate")
                    }
                    TextButton(onClick = viewModel::clearOpenAiKey) {
                        Text("Clear")
                    }
                }
            }
            SettingsSection("Google Places API key") {
                Text(
                    "Optional. When set, nearby place context uses Google Places and falls back to Android geocoding if needed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = uiState.googlePlacesKey,
                    onValueChange = viewModel::setGooglePlacesKey,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("AIza...") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::saveGooglePlacesKey) {
                        Text("Save")
                    }
                    TextButton(onClick = viewModel::clearGooglePlacesKey) {
                        Text("Clear")
                    }
                }
            }
            SettingsSection("Tone") {
                OptionChips(
                    options = InsightTone.entries,
                    selected = uiState.preferences.tone,
                    labelFor = { it.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase) },
                    onSelect = viewModel::setTone,
                )
            }
            SettingsSection("Interests") {
                WrapChips(
                    topics = InterestTopic.entries.toList(),
                    selected = uiState.preferences.interests,
                    onToggle = viewModel::toggleInterest,
                )
            }
            SettingsSection("Language") {
                OutlinedTextField(
                    value = uiState.preferences.outputLanguage,
                    onValueChange = viewModel::setOutputLanguage,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Output language") },
                    singleLine = true,
                )
            }
            SettingsSection("Custom prompt") {
                OutlinedTextField(
                    value = uiState.preferences.customPrompt,
                    onValueChange = viewModel::setCustomPrompt,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("Custom instructions") },
                )
            }
            SettingsSection("Privacy") {
                OutlinedButton(onClick = viewModel::clearHistory) {
                    Text("Clear local history")
                }
            }
        }
    }
}

@Composable
private fun HomeMapHero(
    mapState: HomeMapState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (BuildConfig.GOOGLE_MAPS_API_KEY.isBlank() || mapState.latitude == null || mapState.longitude == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(UiTags.HomeMapFallback),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Map view", style = MaterialTheme.typography.titleLarge)
                        Text(
                            when {
                                BuildConfig.GOOGLE_MAPS_API_KEY.isBlank() ->
                                    "Add an app-owned MAPS_API_KEY locally to enable the embedded map."
                                else ->
                                    "Generate an insight to center the map on your current place."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        mapState.label?.let {
                            Text(
                                "Latest place: $it",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        } else {
            val latLng = remember(mapState.latitude, mapState.longitude) {
                LatLng(mapState.latitude, mapState.longitude)
            }
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(latLng, 15f)
            }
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
            ) {
                Marker(
                    state = MarkerState(position = latLng),
                    title = mapState.label ?: "Current place",
                    snippet = mapState.fullAddress,
                )
                Circle(
                    center = latLng,
                    radius = 140.0,
                    fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    strokeWidth = 2f,
                )
            }
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        mapState.label ?: "Current place",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    mapState.fullAddress?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeModeToggle(
    selectedMode: OperatingMode,
    onSelectMode: (OperatingMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            modifier = Modifier.testTag(UiTags.HomeModeLive),
            selected = selectedMode == OperatingMode.LIVE,
            onClick = { onSelectMode(OperatingMode.LIVE) },
            label = { Text("Live") },
        )
        FilterChip(
            modifier = Modifier.testTag(UiTags.HomeModePopups),
            selected = selectedMode == OperatingMode.POPUPS,
            onClick = { onSelectMode(OperatingMode.POPUPS) },
            label = { Text("Popups") },
        )
    }
}

@Composable
private fun ManualInsightFab(
    isGenerating: Boolean,
    hasPermission: Boolean,
    onGenerate: () -> Unit,
) {
    ExtendedFloatingActionButton(
        modifier = Modifier.testTag(UiTags.HomeGenerateInsightButton),
        onClick = onGenerate,
        expanded = true,
        icon = {
            Icon(
                Icons.Rounded.MyLocation,
                contentDescription = null,
            )
        },
        text = {
            Text(
                when {
                    isGenerating -> "Generating..."
                    hasPermission -> "Get insight here"
                    else -> "Grant location and get insight"
                },
            )
        },
    )
}

@Composable
private fun LatestInsightSheet(
    insight: InsightRecord,
    isNarrating: Boolean,
    onDismiss: () -> Unit,
    onToggleNarration: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTags.HomeInsightSheet),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(onClick = onDismiss, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.86f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(999.dp),
                        )
                        .fillMaxWidth(0.18f)
                        .height(4.dp),
                )
                Text("Latest insight", style = MaterialTheme.typography.labelLarge)
                InsightCard(
                    insight = insight,
                    isNarrating = isNarrating,
                    onToggleNarration = onToggleNarration,
                    onOpenDetail = onOpenDetail,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsSheet(
    resolved: ResolvedPlaceDebug?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        modifier = Modifier.testTag(UiTags.HomeDiagnosticsSheet),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Diagnostics", style = MaterialTheme.typography.headlineSmall)
            if (resolved == null) {
                Text(
                    "Current resolved place is unavailable right now.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                val placeContext = resolved.placeContext
                val updated = remember(resolved.updatedAt) {
                    timestampFormatter.format(resolved.updatedAt.atZone(ZoneId.systemDefault()))
                }
                MetadataRow(
                    "Coordinates",
                    "${formatCoordinate(placeContext.latitude)}, ${formatCoordinate(placeContext.longitude)}",
                )
                MetadataRow(
                    "Resolved place",
                    listOfNotNull(
                        placeContext.areaName,
                        placeContext.locality,
                        placeContext.countryName,
                    ).joinToString(),
                )
                MetadataRow("Full address", placeContext.fullAddress ?: "Unavailable")
                placeContext.sourceName?.let { MetadataRow("Place source", it) }
                MetadataRow("Last updated", updated)
            }
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun InsightCard(
    insight: InsightRecord,
    isNarrating: Boolean,
    onToggleNarration: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .testTag(UiTags.InsightCard)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(insight.title, style = MaterialTheme.typography.titleLarge)
            Text(insight.placeContext.areaName, color = MaterialTheme.colorScheme.primary)
            Text(insight.summary)
            Text(insight.whyItMatters, style = MaterialTheme.typography.bodyMedium)
            if (insight.usedDemoFallback) {
                Text(
                    "OpenAI was unavailable, so this insight used demo fallback.",
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.testTag(UiTags.InsightOpenDetailButton),
                    onClick = onOpenDetail,
                ) {
                    Text("Open detail")
                }
                NarrationButton(
                    isNarrating = isNarrating,
                    onClick = onToggleNarration,
                )
            }
        }
    }
}

@Composable
private fun NarrationButton(
    isNarrating: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick) {
        if (isNarrating) {
            Icon(
                Icons.Rounded.Stop,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp),
            )
            Text("Stop")
        } else {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp),
            )
            Text("Speak")
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body)
    }
}

@Composable
private fun DetailNarrationAction(
    uiState: DetailUiState,
    enabled: Boolean,
    onToggleNarration: () -> Unit,
) {
    IconButton(
        onClick = onToggleNarration,
        enabled = enabled,
    ) {
        if (uiState.isNarrating) {
            Icon(Icons.Rounded.Stop, contentDescription = "Stop narration")
        } else {
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Speak insight")
        }
    }
}

@Composable
private fun MetadataSection(
    metadata: InsightGenerationMetadata?,
    confidenceNote: String,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Metadata", style = MaterialTheme.typography.titleMedium)
            MetadataRow("Confidence", confidenceNote)
            if (metadata == null) {
                Text(
                    "Generation settings were not captured for this older insight.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                MetadataRow(
                    "Tone",
                    metadata.tone.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase),
                )
                MetadataRow(
                    "Interests",
                    metadata.interests.joinToString { it.label },
                )
                MetadataRow(
                    "Custom prompt",
                    metadata.customPrompt.ifBlank { "None" },
                )
            }
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun <T> OptionChips(
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    FilterChip(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        label = { Text(labelFor(option)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WrapChips(
    topics: List<InterestTopic>,
    selected: Set<InterestTopic>,
    onToggle: (InterestTopic) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        topics.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { topic ->
                    FilterChip(
                        selected = topic in selected,
                        onClick = { onToggle(topic) },
                        label = { Text(topic.label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    title: String,
    body: String,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun android.content.Context.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun formatCoordinate(value: Double): String = "%.5f".format(value)

private val timestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private data class TopLevelDestination<T : Any>(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: T,
)
