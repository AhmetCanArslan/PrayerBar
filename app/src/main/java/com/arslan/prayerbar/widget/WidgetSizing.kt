package com.arslan.prayerbar.widget

import androidx.annotation.LayoutRes
import com.arslan.prayerbar.R

enum class WidgetShape(@param:LayoutRes val layoutRes: Int) {
    Tiny(R.layout.widget_tiny),
    Small(R.layout.widget_small),
    Wide(R.layout.widget_wide),
    Medium(R.layout.widget_medium),
    Large(R.layout.widget_large),
}

data class WidgetLayoutSpec(
    val shape: WidgetShape,
    val listCapacity: Int,
) {
    val listIsHorizontal: Boolean get() = shape == WidgetShape.Wide
}

object WidgetSizing {
    private const val NARROW_DP = 130
    private const val STRIP_DP = 210
    private const val SPLIT_DP = 270
    private const val SHORT_DP = 120
    private const val ROW_HEIGHT_DP = 22
    private const val COLUMN_WIDTH_DP = 56
    private const val MEDIUM_CHROME_DP = 118
    private const val LARGE_CHROME_DP = 28
    private const val WIDE_CHROME_DP = 66

    fun resolve(widthDp: Int, heightDp: Int): WidgetLayoutSpec {
        val short = heightDp < SHORT_DP
        val narrow = widthDp < NARROW_DP
        return when {
            short && narrow -> WidgetLayoutSpec(WidgetShape.Tiny, 0)
            short && widthDp < STRIP_DP -> WidgetLayoutSpec(WidgetShape.Small, 0)
            short -> WidgetLayoutSpec(WidgetShape.Wide, columns(widthDp, heightDp))
            widthDp < SPLIT_DP -> WidgetLayoutSpec(
                WidgetShape.Medium,
                rows(heightDp - MEDIUM_CHROME_DP),
            )
            else -> WidgetLayoutSpec(WidgetShape.Large, rows(heightDp - LARGE_CHROME_DP))
        }
    }

    private fun rows(availableDp: Int): Int = (availableDp / ROW_HEIGHT_DP).coerceIn(0, 8)

    private fun columns(widthDp: Int, heightDp: Int): Int {
        if (heightDp < WIDE_CHROME_DP) return 0
        return (widthDp / COLUMN_WIDTH_DP).coerceIn(0, 8)
    }
}
