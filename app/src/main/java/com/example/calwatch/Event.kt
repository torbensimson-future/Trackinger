package com.example.calwatch

import org.json.JSONArray
import org.json.JSONObject

data class Event(
    val uid: String,
    val summary: String,
    val startMillis: Long?,
    val endMillis: Long?,
    val allDay: Boolean,
    val locText: String,
    val lat: Double?,
    val lon: Double?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("uid", uid)
        put("summary", summary)
        put("start", startMillis ?: JSONObject.NULL)
        put("end", endMillis ?: JSONObject.NULL)
        put("allDay", allDay)
        put("loc", locText)
        put("lat", lat ?: JSONObject.NULL)
        put("lon", lon ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(o: JSONObject): Event = Event(
            uid = o.optString("uid"),
            summary = o.optString("summary", "(ohne Titel)"),
            startMillis = if (o.isNull("start")) null else o.getLong("start"),
            endMillis = if (o.isNull("end")) null else o.getLong("end"),
            allDay = o.optBoolean("allDay", false),
            locText = o.optString("loc", ""),
            lat = if (o.isNull("lat")) null else o.getDouble("lat"),
            lon = if (o.isNull("lon")) null else o.getDouble("lon")
        )

        fun listToJson(list: List<Event>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(s: String): List<Event> {
            if (s.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(s)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
