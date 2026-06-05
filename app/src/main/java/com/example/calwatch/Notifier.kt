package com.example.calwatch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

object Notifier {
    private const val CHANNEL_ID = "calwatch_warnings"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Termin-Warnungen",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Warnt, wenn du zu weit von einem Termin entfernt bist."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
            }
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun distanceText(distMeters: Double): String =
        if (distMeters < 1000) "${distMeters.roundToInt()} m"
        else String.format("%.1f km", distMeters / 1000)

    fun show(ctx: Context, event: Event, distMeters: Double, radius: Int) {
        ensureChannel(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val distStr = distanceText(distMeters)
        val bigText = "Du bist $distStr vom Ort entfernt" +
            (if (event.locText.isNotBlank()) " (${event.locText})" else "") +
            ".\nWarn-Radius: $radius m."

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setContentTitle("⚠ Zu weit vom Termin: ${event.summary}")
            .setContentText("Du bist $distStr entfernt (Radius $radius m).")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(ctx).notify(event.uid.hashCode(), notification)
        } catch (e: SecurityException) {
            // Benachrichtigungsrecht fehlt – ignorieren
        }
    }
}
