package com.example.calwatch

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class CheckWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        if (prefs.icsUrl.isBlank()) return Result.success()

        val events = Logic.ensureEvents(prefs, false)
        val now = System.currentTimeMillis()
        val next = Logic.nextEventWithCoords(events, now, prefs.windowMin * 60_000L)
            ?: return Result.success()

        val loc = Logic.lastKnownLocation(ctx) ?: return Result.success()
        val dist = Logic.distanceMeters(loc.first, loc.second, next.lat!!, next.lon!!)

        if (dist > prefs.radius) {
            val recentlyNotified = next.uid == prefs.lastNotifiedUid &&
                now - prefs.lastNotifiedAt < 30 * 60_000L
            if (!recentlyNotified) {
                Notifier.show(ctx, next, dist, prefs.radius)
                prefs.lastNotifiedUid = next.uid
                prefs.lastNotifiedAt = now
            }
        }
        return Result.success()
    }
}
