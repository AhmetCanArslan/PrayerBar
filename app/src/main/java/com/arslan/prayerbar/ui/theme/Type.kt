package com.arslan.prayerbar.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Roboto is the correct Material typeface, so the default scale stands. Emphasized styles come from
 * the expressive type scale and are used directly at the call site.
 */
val PrayerBarTypography = Typography()

/**
 * Narrow phones: the large end of the scale steps down so headlines and titles keep their hierarchy
 * without wrapping. Body and label sizes stay, they are already at the readable minimum.
 */
val PrayerBarCompactTypography: Typography = PrayerBarTypography.run {
    copy(
        displayLarge = displayLarge.shrink(48.sp, 56.sp),
        displayMedium = displayMedium.shrink(38.sp, 46.sp),
        displaySmall = displaySmall.shrink(32.sp, 40.sp),
        headlineLarge = headlineLarge.shrink(28.sp, 36.sp),
        headlineMedium = headlineMedium.shrink(24.sp, 32.sp),
        headlineSmall = headlineSmall.shrink(21.sp, 28.sp),
        titleLarge = titleLarge.shrink(20.sp, 26.sp),
        bodyLarge = bodyLarge.shrink(15.sp, 22.sp),
    )
}

private fun TextStyle.shrink(size: TextUnit, lineHeight: TextUnit): TextStyle =
    copy(fontSize = size, lineHeight = lineHeight)
