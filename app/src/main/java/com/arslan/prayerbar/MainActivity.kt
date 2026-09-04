@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.arslan.prayerbar

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arslan.prayerbar.carrier.ShizukuHelper
import com.arslan.prayerbar.ui.MainViewModel
import com.arslan.prayerbar.ui.home.HomeScreen
import com.arslan.prayerbar.ui.settings.CalculationScreen
import com.arslan.prayerbar.ui.settings.FormatScreen
import com.arslan.prayerbar.ui.settings.LocationScreen
import com.arslan.prayerbar.ui.settings.ShizukuScreen
import com.arslan.prayerbar.ui.settings.TileScreen
import com.arslan.prayerbar.ui.theme.PrayerBarTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import rikka.shizuku.Shizuku

private enum class Destination(val labelRes: Int) {
    Home(R.string.nav_home),
    Calculation(R.string.nav_calculation),
    Location(R.string.nav_location),
    Format(R.string.nav_format),
    Tile(R.string.nav_tile),
    Shizuku(R.string.nav_shizuku),
}

class MainActivity : ComponentActivity() {

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuListener) }

        setContent {
            PrayerBarTheme {
                PrayerBarRoot(activity = this)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuListener) }
    }
}

@Composable
private fun PrayerBarRoot(activity: ComponentActivity) {
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModel.factory(activity.application),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var destination by remember { mutableStateOf(Destination.Home) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val phonePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshEnvironment() }

    // Without READ_PHONE_STATE the SIM list comes back empty and every write looks like "no SIM",
    // so ask once on first launch instead of waiting for the user to find the Shizuku screen.
    LaunchedEffect(Unit) {
        if (!state.shizuku.phonePermission) {
            phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshEnvironment() }

    val requestNotificationPermission = {
        // The permission only exists from Android 13; below that notifications are on by default.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // The foreground service that keeps the countdown fresh must show a notification; without this
    // permission it is silently suppressed and the user has no way to see or mute it.
    LaunchedEffect(Unit) {
        if (!state.shizuku.notificationsAllowed) requestNotificationPermission()
    }

    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) viewModel.useCurrentLocation("")
    }

    // Shizuku may be granted while the app sits in the background; re-read on every resume.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshEnvironment()
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                subtitle = {
                    Text(state.settings.activeLocation?.label ?: stringResource(R.string.nav_location))
                },
                actions = {
                    IconButton(onClick = { destination = Destination.Shizuku }) {
                        Icon(Icons.Rounded.Security, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            ShortNavigationBar {
                listOf(
                    Destination.Home to Icons.Rounded.Schedule,
                    Destination.Calculation to Icons.Rounded.Calculate,
                    Destination.Location to Icons.Rounded.Place,
                    Destination.Format to Icons.Rounded.EditNote,
                    Destination.Tile to Icons.Rounded.Widgets,
                ).forEach { (target, icon) ->
                    ShortNavigationBarItem(
                        selected = destination == target,
                        onClick = { destination = target },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(stringResource(target.labelRes)) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (destination) {
                Destination.Home -> HomeScreen(
                    state = state,
                    labelOf = viewModel::labelOf,
                    onToggleEnabled = { enabled ->
                        if (enabled && !ShizukuHelper.hasPermission()) {
                            ShizukuHelper.requestPermission(activity)
                        }
                        viewModel.setEnabled(enabled)
                    },
                    onApply = viewModel::applyNow,
                    onReset = viewModel::resetNow,
                    onRefresh = viewModel::refreshEnvironment,
                    onOpenLocation = { destination = Destination.Location },
                )

                Destination.Calculation -> CalculationScreen(
                    settings = state.settings,
                    labelOf = viewModel::labelOf,
                    onMethodSelected = viewModel::setMethod,
                    onRestoreDefaults = viewModel::restoreMethodDefaults,
                    onFajrAngle = viewModel::setFajrAngle,
                    onIshaAngle = viewModel::setIshaAngle,
                    onIshaInterval = viewModel::setIshaInterval,
                    onMaghribAngle = viewModel::setMaghribAngle,
                    onAdjustment = viewModel::setAdjustment,
                    onResetAdjustments = viewModel::resetAdjustments,
                    onMadhab = viewModel::setMadhab,
                    onHighLatitude = viewModel::setHighLatitudeRule,
                    onShafaq = viewModel::setShafaq,
                    onPolar = viewModel::setPolar,
                    onRounding = viewModel::setRounding,
                    onMidnight = viewModel::setMidnightMethod,
                    onToggleTracked = viewModel::toggleTracked,
                )

                Destination.Location -> LocationScreen(
                    settings = state.settings,
                    busy = state.busy,
                    onUseGps = { label ->
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ),
                        )
                        viewModel.useCurrentLocation(label)
                    },
                    onAddManual = viewModel::addLocation,
                    onSelect = viewModel::selectLocation,
                    onDelete = viewModel::deleteLocation,
                )

                Destination.Format -> FormatScreen(
                    settings = state.settings,
                    previewText = state.previewText,
                    simSlots = state.simSlots,
                    onTemplate = viewModel::setTemplate,
                    onUse24Hour = viewModel::setUse24Hour,
                    onTargetSubIds = viewModel::setTargetSubIds,
                    onRestartSystemUi = viewModel::restartSystemUi,
                )

                Destination.Tile -> TileScreen(
                    settings = state.settings,
                    preview = state.tilePreview,
                    labelOf = viewModel::labelOf,
                    onTemplate = viewModel::setTileTemplate,
                    onSubtitleTemplate = viewModel::setTileSubtitleTemplate,
                    onTileEnabled = viewModel::setTileEnabled,
                    onShowIcon = viewModel::setTileShowIcon,
                    onHighlightMinutes = viewModel::setTileHighlightMinutes,
                    onAddTile = viewModel::addQuickSettingsTile,
                )

                Destination.Shizuku -> ShizukuScreen(
                    state = state.shizuku,
                    onGrant = {
                        ShizukuHelper.requestPermission(activity)
                        ShizukuHelper.markRequested(activity)
                    },
                    onRequestPhonePermission = {
                        phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                    },
                    onRequestNotificationPermission = requestNotificationPermission,
                )
            }
        }
    }
}
