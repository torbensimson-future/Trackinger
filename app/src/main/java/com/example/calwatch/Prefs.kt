package com.example.calwatch

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("calwatch", Context.MODE_PRIVATE)

    var icsUrl: String
        get() = sp.getString("icsUrl", "") ?: ""
        set(v) = sp.edit().putString("icsUrl", v).apply()

    var radius: Int
        get() = sp.getInt("radius", 500)
        set(v) = sp.edit().putInt("radius", v).apply()

    /** Minuten vor Terminbeginn, ab denen gewarnt wird. */
    var windowMin: Int
        get() = sp.getInt("windowMin", 90)
        set(v) = sp.edit().putInt("windowMin", v).apply()

    var monitoring: Boolean
        get() = sp.getBoolean("monitoring", false)
        set(v) = sp.edit().putBoolean("monitoring", v).apply()

    var eventsJson: String
        get() = sp.getString("eventsJson", "") ?: ""
        set(v) = sp.edit().putString("eventsJson", v).apply()

    var lastFetch: Long
        get() = sp.getLong("lastFetch", 0L)
        set(v) = sp.edit().putLong("lastFetch", v).apply()

    var lastNotifiedUid: String
        get() = sp.getString("lastNotifiedUid", "") ?: ""
        set(v) = sp.edit().putString("lastNotifiedUid", v).apply()

    var lastNotifiedAt: Long
        get() = sp.getLong("lastNotifiedAt", 0L)
        set(v) = sp.edit().putLong("lastNotifiedAt", v).apply()

    // einfacher Geocode-Cache: Adresse -> "lat,lon"
    fun cachedCoord(addr: String): Pair<Double, Double>? {
        val s = sp.getString("gc_" + addr.hashCode(), null) ?: return null
        val p = s.split(",")
        return if (p.size == 2) p[0].toDoubleOrNull()?.let { la -> p[1].toDoubleOrNull()?.let { lo -> la to lo } } else null
    }

    fun cacheCoord(addr: String, lat: Double, lon: Double) {
        sp.edit().putString("gc_" + addr.hashCode(), "$lat,$lon").apply()
    }
}
