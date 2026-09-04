package com.arslan.prayerbar.tile

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.arslan.prayerbar.PrayerBarApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * A read-only Quick Settings tile that mirrors the carrier label.
 *
 * There is no toggle behind it: Quick Settings has no "text only" widget, so the tile is the label
 * wearing a button's clothes. [onClick] deliberately does nothing but re-render.
 *
 * What the tile can and cannot control: the label, the subtitle and the icon are ours, but the
 * icon's colour is not — stock SystemUI applies its own tint to whatever drawable a tile hands it,
 * so a per-prayer tint only survives on skins that skip that step. The colour the platform does let
 * us drive is the tile's state, which is why the highlight window flips it to [Tile.STATE_ACTIVE].
 *
 * The tile is not declared ACTIVE_TILE, so the system calls [onStartListening] every time the shade
 * is pulled down — exactly when the label is looked at. [refresh] pushes an update on top of that
 * for the case where the panel is already open when a prayer boundary passes.
 */
class PrayerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        render()
    }

    /** Not a button. Re-render so a curious tap at least refreshes the countdown. */
    override fun onClick() {
        render()
    }

    private fun render() {
        if (qsTile == null) return
        val container = PrayerBarApp.container(this)
        container.launch {
            val content = withContext(Dispatchers.IO) {
                runCatching {
                    val now = Instant.now()
                    val settings = container.settingsRepository.current()
                    val next = container.resolver.resolve(now, settings, ZoneId.systemDefault())
                    TileRenderer.render(this@PrayerTileService, settings, next, now)
                }.getOrNull()
            } ?: return@launch
            // qsTile can be nulled out between the launch and the callback if the panel closed.
            val tile = qsTile ?: return@launch
            tile.label = content.label
            tile.subtitle = content.subtitle.orEmpty()
            tile.contentDescription = listOfNotNull(content.label, content.subtitle).joinToString(", ")
            tile.icon = tintedIcon(this@PrayerTileService, content)
            tile.state = if (content.highlighted) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            runCatching { tile.updateTile() }
                .onFailure { Log.w(TAG, "updateTile failed", it) }
        }
    }

    companion object {
        private const val TAG = "PrayerTileService"

        /** Big enough that the tinted bitmap stays crisp on an xxxhdpi panel. */
        private const val ICON_PX = 96

        /**
         * A bitmap, not a resource: SystemUI tints resource icons unconditionally, but a few OEM
         * skins leave bitmaps alone, so this is the per-prayer colour's only chance to show. Where
         * it is tinted the result is identical to handing over the vector, so there is nothing to
         * lose by trying.
         */
        private fun tintedIcon(context: Context, content: TileContent): Icon? {
            val drawable = ContextCompat.getDrawable(context, content.iconRes)?.mutate()
                ?: return null
            DrawableCompat.setTint(drawable, content.color)
            val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, ICON_PX, ICON_PX)
            drawable.draw(Canvas(bitmap))
            return Icon.createWithBitmap(bitmap)
        }

        /**
         * Asks the system to call [onStartListening] soon. Rate-limited and silently ignored when
         * the tile is not on the user's panel, so it is safe to call after every label write.
         */
        fun refresh(context: Context) {
            runCatching {
                requestListeningState(
                    context,
                    ComponentName(context, PrayerTileService::class.java),
                )
            }.onFailure { Log.d(TAG, "refresh skipped: ${it.message}") }
        }
    }
}
