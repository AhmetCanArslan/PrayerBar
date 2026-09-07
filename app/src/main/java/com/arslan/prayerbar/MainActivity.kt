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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.arslan.prayerbar.carrier.ShizukuHelper
import com.arslan.prayerbar.ui.MainUiState
import com.arslan.prayerbar.ui.MainViewModel
import com.arslan.prayerbar.ui.Route
import com.arslan.prayerbar.ui.home.HomeScreen
import com.arslan.prayerbar.ui.settings.CalculationScreen
import com.arslan.prayerbar.ui.settings.FormatScreen
import com.arslan.prayerbar.ui.settings.LocationScreen
import com.arslan.prayerbar.ui.settings.SettingsScreen
import com.arslan.prayerbar.ui.settings.ShizukuScreen
import com.arslan.prayerbar.ui.settings.TileScreen
import com.arslan.prayerbar.ui.settings.WIDGET_PREVIEW_CAPACITY
import com.arslan.prayerbar.ui.settings.WidgetScreen
import com.arslan.prayerbar.ui.theme.PrayerBarTheme
import rikka.shizuku.Shizuku

private enum class Tab(val root: Route, val labelRes: Int, val icon: ImageVector) {
    Home(Route.Home, R.string.nav_home, Icons.Rounded.Schedule),
    Location(Route.Location, R.string.nav_location, Icons.Rounded.Place),
    Settings(Route.SettingsGraph, R.string.nav_settings, Icons.Rounded.Settings),
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
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val phonePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshEnvironment() }

    LaunchedEffect(Unit) {
        if (!state.shizuku.phonePermission) {
            phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshEnvironment() }

    val requestNotificationPermission = {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        if (!state.shizuku.notificationsAllowed) requestNotificationPermission()
    }

    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) viewModel.useCurrentLocation("")
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshEnvironment()
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val isTopLevel = destination?.isTopLevel() ?: true

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    AnimatedContent(
                        targetState = destination.titleRes(),
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                        label = "title",
                    ) { titleRes -> Text(stringResource(titleRes)) }
                },
                subtitle = {
                    Text(state.settings.activeLocation?.label ?: stringResource(R.string.nav_location))
                },
                navigationIcon = {
                    if (!isTopLevel) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.nav_back),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = isTopLevel, enter = barEnter, exit = barExit) {
                ShortNavigationBar {
                    Tab.entries.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(tab.root::class) } == true
                        ShortNavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (selected) navController.popToTabRoot(tab)
                                else navController.switchTab(tab)
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            PrayerBarNavHost(
                navController = navController,
                state = state,
                viewModel = viewModel,
                activity = activity,
                requestNotificationPermission = requestNotificationPermission,
                onRequestPhonePermission = {
                    phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                },
                onRequestLocationPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun PrayerBarNavHost(
    navController: NavHostController,
    state: MainUiState,
    viewModel: MainViewModel,
    activity: ComponentActivity,
    requestNotificationPermission: () -> Unit,
    onRequestPhonePermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = Route.Home,

        enterTransition = { if (targetState.isTopLevel()) fadeThroughEnter else detailFadeIn },
        exitTransition = { if (targetState.isTopLevel()) fadeThroughExit else detailFadeOut },
        popEnterTransition = { if (initialState.isTopLevel()) fadeThroughEnter else detailFadeIn },
        popExitTransition = { if (initialState.isTopLevel()) fadeThroughExit else detailFadeOut },
    ) {
        composable<Route.Home> {
            HomeScreen(
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
                onOpenLocation = { navController.switchTab(Tab.Location) },
            )
        }

        composable<Route.Location> {
            LocationScreen(
                settings = state.settings,
                busy = state.busy,
                onUseGps = { label ->
                    onRequestLocationPermission()
                    viewModel.useCurrentLocation(label)
                },
                onAddManual = viewModel::addLocation,
                onSelect = viewModel::selectLocation,
                onDelete = viewModel::deleteLocation,
            )
        }

        navigation<Route.SettingsGraph>(startDestination = Route.Settings) {
            composable<Route.Settings> {
                SettingsScreen(onOpen = navController::navigate)
            }

            composable<Route.Calculation> {
                CalculationScreen(
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
            }

            composable<Route.Format> {
                FormatScreen(
                    settings = state.settings,
                    previewText = state.previewText,
                    simSlots = state.simSlots,
                    onTemplate = viewModel::setTemplate,
                    onUse24Hour = viewModel::setUse24Hour,
                    onTargetSubIds = viewModel::setTargetSubIds,
                    onRestartSystemUi = viewModel::restartSystemUi,
                )
            }

            composable<Route.Tile> {
                TileScreen(
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
            }

            composable<Route.Widget> {
                WidgetScreen(
                    widgetIds = state.widgetIds,
                    widgetOf = state.settings::widget,
                    previewOf = { id -> viewModel.widgetPreview(id, WIDGET_PREVIEW_CAPACITY) },
                    labelOf = viewModel::labelOf,
                    onEdit = viewModel::editWidget,
                    onTogglePrayer = viewModel::toggleWidgetPrayer,
                    onAddWidget = viewModel::pinWidget,
                )
            }

            composable<Route.Permissions> {
                ShizukuScreen(
                    state = state.shizuku,
                    onGrant = {
                        ShizukuHelper.requestPermission(activity)
                        ShizukuHelper.markRequested(activity)
                    },
                    onRequestPhonePermission = onRequestPhonePermission,
                    onRequestNotificationPermission = requestNotificationPermission,
                )
            }
        }
    }
}

private fun NavHostController.switchTab(tab: Tab) {
    navigate(tab.root) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.popToTabRoot(tab: Tab) {
    val root: Route = if (tab == Tab.Settings) Route.Settings else tab.root
    popBackStack(root, inclusive = false)
}

private fun NavDestination.isTopLevel(): Boolean =
    hasRoute<Route.Home>() || hasRoute<Route.Location>() || hasRoute<Route.Settings>()

private fun NavBackStackEntry.isTopLevel(): Boolean = destination.isTopLevel()

private fun NavDestination?.titleRes(): Int = when {
    this == null -> R.string.home_title
    hasRoute<Route.Location>() -> R.string.nav_location
    hasRoute<Route.Settings>() -> R.string.nav_settings
    hasRoute<Route.Calculation>() -> R.string.nav_calculation
    hasRoute<Route.Format>() -> R.string.nav_format
    hasRoute<Route.Tile>() -> R.string.nav_tile
    hasRoute<Route.Widget>() -> R.string.nav_widget
    hasRoute<Route.Permissions>() -> R.string.nav_permissions
    else -> R.string.home_title
}

private const val FADE_THROUGH_MS = 210
private const val FADE_THROUGH_DELAY_MS = 90

private val fadeThroughEnter: EnterTransition =
    fadeIn(tween(FADE_THROUGH_MS, delayMillis = FADE_THROUGH_DELAY_MS)) +
        scaleIn(tween(FADE_THROUGH_MS, delayMillis = FADE_THROUGH_DELAY_MS), initialScale = 0.92f)

private val fadeThroughExit: ExitTransition = fadeOut(tween(FADE_THROUGH_DELAY_MS))

private const val DETAIL_MS = 400

private val detailFadeIn: EnterTransition = fadeIn(tween(DETAIL_MS))
private val detailFadeOut: ExitTransition = fadeOut(tween(DETAIL_MS))

private val barEnter: EnterTransition =
    slideInVertically(tween(DETAIL_MS)) { it } + detailFadeIn
private val barExit: ExitTransition =
    slideOutVertically(tween(DETAIL_MS)) { it } + detailFadeOut
