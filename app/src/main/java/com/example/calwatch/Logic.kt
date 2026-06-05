package com.example.calwatch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

object Logic {

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val res = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, res)
        return res[0].toDouble()
    }

    fun hasLocationPermission(ctx: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun lastKnownLocation(ctx: Context): Pair<Double, Double>? {
        if (!hasLocationPermission(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var best: Location? = null
        try {
            for (p in lm.getProviders(true)) {
                val l = lm.getLastKnownLocation(p) ?: continue
                if (best == null || l.time > best!!.time) best = l
            }
        } catch (e: SecurityException) {
            return null
        }
        return best?.let { it.latitude to it.longitude }
    }

    /** Lädt Termine (Cache oder Netzwerk). MUSS außerhalb des Main-Threads laufen. */
    fun ensureEvents(prefs: Prefs, forceRefresh: Boolean): List<Event> {
        val cached = Event.listFromJson(prefs.eventsJson)
        val age = System.currentTimeMillis() - prefs.lastFetch
        if (!forceRefresh && cached.isNotEmpty() && age < 20 * 60_000L) return cached
        if (prefs.icsUrl.isBlank()) return cached
        return try {
            val raw = GeoCoder.download(prefs.icsUrl)
            val parsed = IcsParser.parse(raw)
            val withCoords = parsed.map { e ->
                if (e.lat != null || e.locText.isBlank()) return@map e
                prefs.cachedCoord(e.locText)?.let { (la, lo) -> return@map e.copy(lat = la, lon = lo) }
                val geo = GeoCoder.geocode(e.locText)
                Thread.sleep(1100) // Nominatim: max ~1 Anfrage/Sekunde
                if (geo != null) {
                    prefs.cacheCoord(e.locText, geo.first, geo.second)
                    e.copy(lat = geo.first, lon = geo.second)
                } else e
            }
            prefs.eventsJson = Event.listToJson(withCoords)
            prefs.lastFetch = System.currentTimeMillis()
            withCoords
        } catch (e: Exception) {
            cached
        }
    }

    /**
     * Nächster relevanter Termin mit Koordinaten.
     * - windowMs = null  -> für die ANZEIGE: nächster Termin, der noch nicht vorbei ist.
     * - windowMs gesetzt -> für die WARNUNG: nur VOR dem Start und innerhalb der Vorlaufzeit
     *                       (also start - windowMs <= jetzt < start). Nach Terminbeginn keine Warnung mehr.
     */
    fun nextEventWithCoords(events: List<Event>, now: Long, windowMs: Long?): Event? {
        return events.asSequence()
            .filter { it.lat != null && it.startMillis != null }
            .filter {
                val start = it.startMillis!!
                if (windowMs == null) {
                    now <= (it.endMillis ?: start)        // Anzeige: noch nicht vorbei
                } else {
                    now < start && now >= start - windowMs // Warnung: nur im Vorlauf vor dem Start
                }
            }
            .minByOrNull { it.startMillis!! }
    }
}
