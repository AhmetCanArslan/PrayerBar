package com.arslan.prayerbar.ui

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data object Location : Route

    @Serializable data object SettingsGraph : Route
    @Serializable data object Settings : Route
    @Serializable data object Calculation : Route
    @Serializable data object Format : Route
    @Serializable data object Tile : Route
    @Serializable data object Widget : Route
    @Serializable data object Permissions : Route
}
