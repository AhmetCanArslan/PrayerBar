@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.arslan.prayerbar.ui.settings

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.arslan.prayerbar.ui.Route

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

const val CONTAINER_TRANSFORM_MS = 400

private val containerBounds = BoundsTransform { _, _ ->
    tween(CONTAINER_TRANSFORM_MS, easing = FastOutSlowInEasing)
}

inline fun <reified T : Route> NavGraphBuilder.screen(
    crossinline content: @Composable () -> Unit,
) {
    composable<T> {
        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) { content() }
    }
}

@Composable
fun Modifier.settingsContainer(route: Route, shape: Shape): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(shared) {
        this@settingsContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key = route),
            animatedVisibilityScope = animated,
            boundsTransform = containerBounds,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )

            .skipToLookaheadSize()
    }
}

@Composable
fun SettingsDetail(route: Route, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .settingsContainer(route, MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        content()
    }
}
