package com.arslan.prayerbar.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import com.arslan.prayerbar.AppContainer
import com.arslan.prayerbar.PrayerBarApp
import com.arslan.prayerbar.prayer.PrayerSettings
import com.arslan.prayerbar.schedule.CarrierService
import com.arslan.prayerbar.schedule.SurfaceRefresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

class PrayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        update(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        update(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = async(context) { container ->
        val gone = appWidgetIds.toSet()
        container.settingsRepository.update { settings ->
            settings.copy(widgets = settings.widgets - gone)
        }
    }

    override fun onEnabled(context: Context) = async(context) { container ->
        container.scheduleSafetyNet()
        val outcome = withContext(Dispatchers.IO) {
            SurfaceRefresh.run(context, force = true)
        }
        container.alarmScheduler.schedule(outcome.next)
        CarrierService.start(context)
    }

    override fun onDisabled(context: Context) = async(context) { container ->
        if (container.settingsRepository.current().hasActiveSurface) return@async
        container.cancelSafetyNet()
        CarrierService.stop(context)
        container.alarmScheduler.cancel()
    }

    private fun update(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (appWidgetIds.isEmpty()) return
        async(context) { container ->
            val settings = container.settingsRepository.current()
            draw(context, appWidgetManager, settings, appWidgetIds)

            val next = container.resolver
                .resolve(Instant.now(), settings, ZoneId.systemDefault())
            container.alarmScheduler.schedule(next)
            CarrierService.start(context)
        }
    }

    private fun async(context: Context, block: suspend (AppContainer) -> Unit) {
        val container = PrayerBarApp.container(context)
        val pending = goAsync()
        container.launch {
            try {
                block(container)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "PrayerWidgetProvider"

        fun hasWidgets(context: Context): Boolean = ids(context).isNotEmpty()

        fun ids(context: Context): IntArray = runCatching {
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, PrayerWidgetProvider::class.java))
        }.getOrElse { IntArray(0) }

        suspend fun refresh(context: Context, settings: PrayerSettings) {
            draw(context, AppWidgetManager.getInstance(context), settings, ids(context))
        }

        private suspend fun draw(
            context: Context,
            manager: AppWidgetManager,
            settings: PrayerSettings,
            appWidgetIds: IntArray,
        ) {
            if (appWidgetIds.isEmpty()) return
            withContext(Dispatchers.IO) {
                appWidgetIds.forEach { id ->
                    runCatching {
                        manager.updateAppWidget(id, viewsFor(context, manager, settings, id))
                    }.onFailure { Log.w(TAG, "draw $id failed", it) }
                }
            }
        }

        private fun viewsFor(
            context: Context,
            manager: AppWidgetManager,
            settings: PrayerSettings,
            appWidgetId: Int,
        ): RemoteViews {
            val container = PrayerBarApp.container(context)
            val widget = settings.widget(appWidgetId)
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            val next = container.resolver.resolve(now, settings, zone)
            val today = container.calculator
                .timesFor(now.atZone(zone).toLocalDate(), settings, zone)

            val render = { spec: WidgetLayoutSpec ->
                WidgetViews.build(
                    context,
                    spec,
                    widget,
                    WidgetRenderer.render(
                        context = context,
                        settings = settings,
                        widget = widget,
                        next = next,
                        today = today,
                        capacity = spec.listCapacity,
                        now = now,
                        zone = zone,
                    ),
                )
            }

            val options = runCatching { manager.getAppWidgetOptions(appWidgetId) }.getOrNull()
            val sizes = declaredSizes(options)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sizes.size > 1) {
                return RemoteViews(
                    sizes.take(MAX_MAPPED_SIZES).associateWith { size ->
                        render(WidgetSizing.resolve(size.width.toInt(), size.height.toInt()))
                    },
                )
            }
            val single = sizes.firstOrNull() ?: fallbackSize(options)
            return render(WidgetSizing.resolve(single.width.toInt(), single.height.toInt()))
        }

        private fun declaredSizes(options: Bundle?): List<SizeF> {
            if (options == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()
            @Suppress("DEPRECATION")
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            return sizes.orEmpty().filter { it.width > 0f && it.height > 0f }
        }

        private fun fallbackSize(options: Bundle?): SizeF {
            val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
            return SizeF(
                width.takeIf { it > 0 }?.toFloat() ?: DEFAULT_WIDTH_DP,
                height.takeIf { it > 0 }?.toFloat() ?: DEFAULT_HEIGHT_DP,
            )
        }

        private const val DEFAULT_WIDTH_DP = 250f
        private const val DEFAULT_HEIGHT_DP = 70f
        private const val MAX_MAPPED_SIZES = 16
    }
}
