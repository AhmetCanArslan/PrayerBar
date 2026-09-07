package com.arslan.prayerbar.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import com.arslan.prayerbar.MainActivity
import com.arslan.prayerbar.R
import com.arslan.prayerbar.prayer.WidgetSettings

data class WidgetPalette(
    @param:ColorInt val surface: Int,
    @param:ColorInt val onSurface: Int,
    @param:ColorInt val onSurfaceVariant: Int,
) {
    companion object {
        private val DARK = WidgetPalette(
            surface = 0xFF1B1B1F.toInt(),
            onSurface = 0xFFE4E2E6.toInt(),
            onSurfaceVariant = 0xFFC5C6D0.toInt(),
        )

        fun of(context: Context, widget: WidgetSettings): WidgetPalette =
            if (!widget.followSystemTheme) {
                DARK
            } else {
                WidgetPalette(
                    surface = context.getColor(R.color.widget_surface),
                    onSurface = context.getColor(R.color.widget_on_surface),
                    onSurfaceVariant = context.getColor(R.color.widget_on_surface_variant),
                )
            }
    }
}

object WidgetViews {
    fun build(
        context: Context,
        spec: WidgetLayoutSpec,
        widget: WidgetSettings,
        content: WidgetContent,
    ): RemoteViews {
        val palette = WidgetPalette.of(context, widget)
        val views = RemoteViews(context.packageName, spec.shape.layoutRes)

        views.setInt(R.id.widget_background, "setColorFilter", palette.surface)
        views.setInt(R.id.widget_background, "setImageAlpha", widget.backgroundAlpha * 255 / 100)

        views.setImageViewResource(R.id.widget_icon, content.iconRes)
        views.setInt(R.id.widget_icon, "setColorFilter", content.accent)
        views.setViewVisibility(R.id.widget_icon, visibility(widget.showIcon))

        views.setTextViewText(R.id.widget_name, content.headline)
        views.setTextColor(R.id.widget_name, palette.onSurfaceVariant)

        applyEmphasis(views, widget, content, palette.onSurface)

        views.setTextViewText(R.id.widget_time, content.time)
        views.setTextColor(R.id.widget_time, palette.onSurface)
        views.setViewVisibility(R.id.widget_time, visibility(widget.showCountdown))

        views.setTextViewText(R.id.widget_meta, content.meta)
        views.setTextColor(R.id.widget_meta, palette.onSurfaceVariant)
        views.setViewVisibility(R.id.widget_meta, visibility(content.meta.isNotEmpty()))

        applyProgress(views, content)
        applyList(context, views, spec, widget.showIcon, palette, content)
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        return views
    }

    private fun applyEmphasis(
        views: RemoteViews,
        widget: WidgetSettings,
        content: WidgetContent,
        @ColorInt textColor: Int,
    ) {
        val live = content.configured && widget.showCountdown && widget.showSeconds
        views.setViewVisibility(R.id.widget_remaining, visibility(!live))
        views.setViewVisibility(R.id.widget_chronometer, visibility(live))
        if (live) {
            views.setChronometer(
                R.id.widget_chronometer,
                SystemClock.elapsedRealtime() + content.remainingMillis,
                null,
                true,
            )
            views.setChronometerCountDown(R.id.widget_chronometer, true)
            views.setTextColor(R.id.widget_chronometer, textColor)
        } else {
            views.setTextViewText(R.id.widget_remaining, content.emphasis)
            views.setTextColor(R.id.widget_remaining, textColor)
        }
    }

    private fun applyProgress(views: RemoteViews, content: WidgetContent) {
        views.setViewVisibility(R.id.widget_progress, visibility(content.showProgress))
        if (!content.showProgress) return
        views.setProgressBar(R.id.widget_progress, 1000, content.progress, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(
                R.id.widget_progress,
                "setProgressTintList",
                ColorStateList.valueOf(content.accent),
            )
        }
    }

    private fun applyList(
        context: Context,
        views: RemoteViews,
        spec: WidgetLayoutSpec,
        showIcon: Boolean,
        palette: WidgetPalette,
        content: WidgetContent,
    ) {
        views.removeAllViews(R.id.widget_list)
        views.setViewVisibility(R.id.widget_list, visibility(content.rows.isNotEmpty()))
        if (content.rows.isEmpty()) return

        val itemLayout = if (spec.listIsHorizontal) {
            R.layout.widget_item_column
        } else {
            R.layout.widget_item_row
        }
        content.rows.forEach { row ->
            val item = RemoteViews(context.packageName, itemLayout)
            val nameColor = if (row.isNext) content.accent else palette.onSurfaceVariant
            val timeColor = when {
                row.isNext -> content.accent
                row.elapsed -> palette.onSurfaceVariant
                else -> palette.onSurface
            }
            item.setTextViewText(R.id.widget_row_name, row.label)
            item.setTextColor(R.id.widget_row_name, nameColor)
            item.setTextViewText(R.id.widget_row_time, row.time)
            item.setTextColor(R.id.widget_row_time, timeColor)
            item.setViewVisibility(R.id.widget_row_icon, visibility(showIcon))
            item.setImageViewResource(R.id.widget_row_icon, row.iconRes)
            item.setInt(R.id.widget_row_icon, "setColorFilter", row.color)
            item.setInt(R.id.widget_row_icon, "setImageAlpha", if (row.elapsed) 110 else 255)
            views.addView(R.id.widget_list, item)
        }
    }

    private fun visibility(visible: Boolean): Int = if (visible) View.VISIBLE else View.GONE

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            component = ComponentName(context, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
