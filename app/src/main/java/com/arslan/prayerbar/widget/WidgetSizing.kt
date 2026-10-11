package com.arslan.prayerbar.widget

import androidx.annotation.LayoutRes
import com.arslan.prayerbar.R

/** Where a shape finds room to say that a kerahat window is running. */
enum class KerahatSlot {
    /** A line of its own, which also announces the next window ahead of time. */
    Line,

    /** Appended to the prayer name, only while a window is running. */
    Inline,

    /** Takes the prayer name's place while a window is running; the icon still names the prayer. */
    Replace,
}

enum class WidgetShape(
    @param:LayoutRes val layoutRes: Int,
    @param:LayoutRes val itemLayoutRes: Int,
    val kerahatSlot: KerahatSlot,
) {
    Tiny(R.layout.widget_tiny, R.layout.widget_item_row, KerahatSlot.Replace),
    Small(R.layout.widget_small, R.layout.widget_item_row, KerahatSlot.Inline),
    Wide(R.layout.widget_wide, R.layout.widget_item_column, KerahatSlot.Inline),
    Column(R.layout.widget_column, R.layout.widget_item_compact, KerahatSlot.Replace),
    Compact(R.layout.widget_compact, R.layout.widget_item_row, KerahatSlot.Line),
    Medium(R.layout.widget_medium, R.layout.widget_item_row, KerahatSlot.Line),
    Large(R.layout.widget_large, R.layout.widget_item_row, KerahatSlot.Line),
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
    private const val COMPACT_ROW_HEIGHT_DP = 17
    private const val COLUMN_WIDTH_DP = 56
    private const val MEDIUM_CHROME_DP = 118
    private const val LARGE_CHROME_DP = 28
    private const val WIDE_CHROME_DP = 64
    private const val COLUMN_CHROME_DP = 78
    private const val COLUMN_MIN_HEIGHT_DP = 96
    private const val COMPACT_CHROME_DP = 56
    private const val COMPACT_META_DP = 14
    private const val KERAHAT_LINE_DP = 15
    private const val COMPACT_LIST_ROW_DP = 20
    private const val MEDIUM_MIN_ROWS = 5

    /**
     * [hasMeta] is whether a location or date line will be drawn, and [hasKerahat] whether the
     * kerahat line will; each costs the list a row.
     */
    fun resolve(
        widthDp: Int,
        heightDp: Int,
        hasMeta: Boolean = true,
        hasKerahat: Boolean = false,
    ): WidgetLayoutSpec {
        val short = heightDp < SHORT_DP
        val narrow = widthDp < NARROW_DP
        val mediumChrome = MEDIUM_CHROME_DP + if (hasKerahat) KERAHAT_LINE_DP else 0
        return when {
            narrow && heightDp < COLUMN_MIN_HEIGHT_DP -> WidgetLayoutSpec(WidgetShape.Tiny, 0)
            narrow -> WidgetLayoutSpec(
                WidgetShape.Column,
                rows(heightDp - COLUMN_CHROME_DP, COMPACT_ROW_HEIGHT_DP),
            )
            short && widthDp < STRIP_DP -> WidgetLayoutSpec(WidgetShape.Small, 0)
            short -> WidgetLayoutSpec(WidgetShape.Wide, columns(widthDp, heightDp))
            // A square too short for the big countdown and a full list gives the room to the list.
            widthDp < SPLIT_DP && rows(heightDp - mediumChrome) < MEDIUM_MIN_ROWS ->
                WidgetLayoutSpec(
                    WidgetShape.Compact,
                    rows(
                        heightDp - COMPACT_CHROME_DP -
                            (if (hasMeta) COMPACT_META_DP else 0) -
                            (if (hasKerahat) KERAHAT_LINE_DP else 0),
                        COMPACT_LIST_ROW_DP,
                    ),
                )
            widthDp < SPLIT_DP -> WidgetLayoutSpec(
                WidgetShape.Medium,
                rows(heightDp - mediumChrome),
            )
            else -> WidgetLayoutSpec(WidgetShape.Large, rows(heightDp - LARGE_CHROME_DP))
        }
    }

    private fun rows(availableDp: Int, rowHeightDp: Int = ROW_HEIGHT_DP): Int =
        (availableDp / rowHeightDp).coerceIn(0, 8)

    private fun columns(widthDp: Int, heightDp: Int): Int {
        if (heightDp < WIDE_CHROME_DP) return 0
        return (widthDp / COLUMN_WIDTH_DP).coerceIn(0, 8)
    }
}
