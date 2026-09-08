package com.arslan.prayerbar.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import com.arslan.prayerbar.MainActivity
import com.arslan.prayerbar.PrayerBarApp
import com.arslan.prayerbar.R
import com.arslan.prayerbar.prayer.NextPrayer
import com.arslan.prayerbar.prayer.PrayerSettings
import java.time.Instant
import java.time.ZoneId

/**
 * Builds and posts the one notification [com.arslan.prayerbar.schedule.CarrierService] shows.
 *
 * A foreground service must show a notification whether the user wants one or not, so the prayer
 * status takes that slot over rather than adding a second permanent entry to the shade: with the
 * status surface on, the mandatory notification *is* the status. The two live in separate channels,
 * so silencing or hiding the status never touches the bare service notice, and the other way round.
 *
 * Owning the foreground slot also buys the two things a plain notification cannot have: it is truly
 * undismissable, and [NotificationCompat.Builder.setColorized] is honoured.
 */
object PrayerNotifier {

    /** Shared with the service: updating the foreground notification means reusing its id. */
    const val NOTIFICATION_ID = 42

    const val STATUS_CHANNEL_ID = "prayerbar-status"
    const val SERVICE_CHANNEL_ID = "prayerbar-carrier"

    /** The last notification built, so a restarted service can repost it before its first refresh. */
    @Volatile
    private var last: Notification? = null

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // IMPORTANCE_LOW on both: no sound, no heads-up. Neither notification can be hidden from
        // inside the app — a foreground service must show one — but a channel can be silenced.
        manager.createNotificationChannel(
            NotificationChannel(
                SERVICE_CHANNEL_ID,
                context.getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.service_channel_description)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                STATUS_CHANNEL_ID,
                context.getString(R.string.status_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.status_channel_description)
                setShowBadge(false)
            },
        )
    }

    /** Opens the system page for whichever channel this notification is posted in. */
    fun openChannelSettings(context: Context, settings: PrayerSettings) {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId(settings))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { runCatching { context.startActivity(fallback) } }
    }

    fun channelId(settings: PrayerSettings): String =
        if (settings.notification.enabled) STATUS_CHANNEL_ID else SERVICE_CHANNEL_ID

    /**
     * The status notification when that surface is on, the bare service notice otherwise.
     * [serviceText] is the line the bare notice reports; ignored once the status takes over.
     */
    fun build(
        context: Context,
        settings: PrayerSettings,
        next: NextPrayer?,
        serviceText: String? = null,
        now: Instant = Instant.now(),
    ): Notification {
        val notification = if (settings.notification.enabled) {
            status(context, content(context, settings, next, now))
        } else {
            service(context, serviceText ?: context.getString(R.string.service_notification_text))
        }
        last = notification
        return notification
    }

    /** Updates the already-posted notification in place. Only safe while the channel stays the same. */
    fun post(
        context: Context,
        settings: PrayerSettings,
        next: NextPrayer?,
        serviceText: String? = null,
        now: Instant = Instant.now(),
    ) {
        val notification = build(context, settings, next, serviceText, now)
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, notification)
        }
    }

    /**
     * What the service posts the instant it starts, before settings have been read off disk: the
     * last notification it showed, or the bare notice on a cold start.
     */
    fun placeholder(context: Context): Notification =
        last ?: service(context, context.getString(R.string.service_notification_text))

    fun content(
        context: Context,
        settings: PrayerSettings,
        next: NextPrayer?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): NotificationContent = NotificationRenderer.render(
        context = context,
        settings = settings,
        next = next,
        today = PrayerBarApp.container(context).calculator
            .timesFor(now.atZone(zone).toLocalDate(), settings, zone),
        now = now,
        zone = zone,
    )

    private fun status(context: Context, content: NotificationContent): Notification {
        val builder = base(context, STATUS_CHANNEL_ID)
            .setSmallIcon(content.iconRes)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setSubText(content.meta.takeIf { it.isNotBlank() })
            .setColor(content.accent)
            .setColorized(content.colorized)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (content.configured && content.showChronometer) {
            // The platform ticks this down itself, so a live countdown costs no wake-ups at all.
            builder.setWhen(content.endsAtMillis)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        if (content.showProgress) {
            builder.setProgress(NotificationContent.PROGRESS_MAX, content.progress, false)
        }

        if (content.lines.isEmpty()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
        } else {
            val style = NotificationCompat.InboxStyle().setBigContentTitle(content.title)
            content.meta.takeIf { it.isNotBlank() }?.let(style::setSummaryText)
            content.lines.forEach { style.addLine(line(it)) }
            builder.setStyle(style)
        }
        return builder.build()
    }

    private fun service(context: Context, text: String): Notification =
        base(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setShowWhen(false)
            // Android forbids hiding a foreground-service notification, but a long-press opens the
            // channel settings where it can be silenced or minimised — say so on the notification.
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(text + "\n\n" + context.getString(R.string.service_notification_hint)),
            )
            .setSubText(context.getString(R.string.service_notification_hint))
            .build()

    private fun base(context: Context, channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

    /** Times first, so the column lines up in a proportional font; the next one carries the weight. */
    private fun line(line: NotificationLine): CharSequence {
        val text = SpannableString("${line.time}   ${line.label}")
        if (line.isNext) {
            text.setSpan(
                StyleSpan(android.graphics.Typeface.BOLD),
                0,
                text.length,
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE,
            )
        }
        return text
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
