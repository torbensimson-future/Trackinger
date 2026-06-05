package com.example.calwatch

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object GeoCoder {

    /** Liefert (lat, lon) oder null. Aufrufer sollte zwischen Anfragen ~1 s warten. */
    fun geocode(query: String): Pair<Double, Double>? {
        if (query.isBlank()) return null
        return try {
            val url = URL(
                "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" +
                    URLEncoder.encode(query, "UTF-8")
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "CalWatch-Android/1.0")
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            if (arr.length() > 0) {
                val o = arr.getJSONObject(0)
                o.getString("lat").toDouble() to o.getString("lon").toDouble()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun download(rawUrl: String): String {
        val urlStr = if (rawUrl.startsWith("webcal://")) "https://" + rawUrl.removePrefix("webcal://") else rawUrl
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "CalWatch-Android/1.0")
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
