package com.arslan.prayerbar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing that follows the screen width. The regular set is tuned for ~410dp phones; narrow phones
 * (~360dp) get the compact set so the hero, cards and rows stop eating the screen.
 */
@Immutable
data class Dimens(
    val compact: Boolean,
    /** Outer padding of every scrolling screen. */
    val screenPadding: Dp,
    /** Gap between the cards / items of a screen. */
    val sectionGap: Dp,
    /** Inner padding of a section card. */
    val cardPadding: Dp,
    /** Gap between the children of a card. */
    val itemGap: Dp,
    /** Horizontal / vertical padding of a single list row card. */
    val rowPaddingH: Dp,
    val rowPaddingV: Dp,
    /** Share of the width the countdown hero takes. */
    val heroFraction: Float,
)

val RegularDimens = Dimens(
    compact = false,
    screenPadding = 16.dp,
    sectionGap = 16.dp,
    cardPadding = 20.dp,
    itemGap = 12.dp,
    rowPaddingH = 20.dp,
    rowPaddingV = 16.dp,
    heroFraction = 0.72f,
)

val CompactDimens = Dimens(
    compact = true,
    screenPadding = 12.dp,
    sectionGap = 8.dp,
    cardPadding = 16.dp,
    itemGap = 8.dp,
    rowPaddingH = 16.dp,
    rowPaddingV = 11.dp,
    heroFraction = 0.54f,
)

/** Below this width the compact set applies. */
const val COMPACT_WIDTH_DP = 400

val LocalDimens = staticCompositionLocalOf { RegularDimens }

@Suppress("UnusedReceiverParameter")
val MaterialTheme.dimens: Dimens
    @Composable
    @ReadOnlyComposable
    get() = LocalDimens.current
