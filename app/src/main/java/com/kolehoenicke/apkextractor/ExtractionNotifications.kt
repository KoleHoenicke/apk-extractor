package com.kolehoenicke.apkextractor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import com.kolehoenicke.apkextractor.data.ExportedFile
import kotlin.math.roundToInt

internal class ExtractionNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        val channel = NotificationChannel(
            ChannelId,
            context.getString(R.string.extraction_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.extraction_notification_channel_description)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun ongoing(
        snapshot: ExtractionSnapshot,
        requestPromotion: Boolean,
    ): Notification {
        val percent = (snapshot.overallProgress * ProgressMax).roundToInt().coerceIn(0, ProgressMax)
        val count = snapshot.apps.size
        val title = if (count == 1) {
            context.getString(R.string.notification_extracting_app, snapshot.apps.single().label)
        } else {
            context.resources.getQuantityString(
                R.plurals.notification_extracting_apps,
                count,
                count,
            )
        }

        val builder = baseBuilder()
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_percent_complete, percent))
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(openAppIntent())
            .setProgress(ProgressMax, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_close),
                    context.getString(R.string.cancel),
                    cancelIntent(),
                ).build(),
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        if (Build.VERSION.SDK_INT >= 36 &&
            Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1
        ) {
            builder
                .setRequestPromotedOngoing(requestPromotion)
                .setShortCriticalText("$percent%")
            if (requestPromotion) builder.setDeleteIntent(dismissedIntent())
        }

        return builder.build()
    }

    fun finished(files: List<ExportedFile>, requested: Int): Notification {
        val fileCount = files.size
        val text = when {
            fileCount == requested -> context.resources.getQuantityString(
                R.plurals.export_complete_count,
                fileCount,
                fileCount,
            )
            fileCount == 0 -> context.resources.getQuantityString(
                R.plurals.export_failed_count,
                requested,
                requested,
            )
            else -> context.resources.getQuantityString(
                R.plurals.export_partial,
                requested,
                fileCount,
                requested,
            )
        }
        val builder = baseBuilder()
            .setContentTitle(context.getString(R.string.notification_extraction_finished))
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)

        shareIntent(files)?.let { shareIntent ->
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_share),
                    context.getString(R.string.share),
                    shareIntent,
                ).build(),
            )
        }
        return builder.build()
    }

    fun notifyProgress(snapshot: ExtractionSnapshot, requestPromotion: Boolean) {
        manager.notify(NotificationId, ongoing(snapshot, requestPromotion))
    }

    fun notifyFinished(files: List<ExportedFile>, requested: Int) {
        manager.notify(NotificationId, finished(files, requested))
    }

    fun cancel() {
        manager.cancel(NotificationId)
    }

    private fun baseBuilder(): Notification.Builder =
        Notification.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_download)
            .setColor(context.getColor(R.color.notification_accent))

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cancelIntent(): PendingIntent = PendingIntent.getService(
        context,
        1,
        Intent(context, ExtractionService::class.java).setAction(ExtractionService.ActionCancel),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun dismissedIntent(): PendingIntent = PendingIntent.getService(
        context,
        2,
        Intent(context, ExtractionService::class.java).setAction(ExtractionService.ActionDismissed),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun shareIntent(files: List<ExportedFile>): PendingIntent? {
        val chooserIntent = createShareChooserIntent(context, files) ?: return null
        return PendingIntent.getActivity(
            context,
            3,
            chooserIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ChannelId = "apk_extraction"
        const val ProgressMax = 100
        const val NotificationId = 1001
        const val ForegroundNotificationId = NotificationId
    }
}
