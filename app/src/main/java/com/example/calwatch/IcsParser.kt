package com.example.calwatch

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

object IcsParser {

    private val DT = Regex("(\\d{4})(\\d{2})(\\d{2})(?:T(\\d{2})(\\d{2})(\\d{2})(Z)?)?")

    fun parse(text: String): List<Event> {
        val unfolded = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\n ", "")
            .replace("\n\t", "")

        val events = mutableListOf<Event>()
        var cur: MutableMap<String, Any?>? = null

        for (rawLine in unfolded.split("\n")) {
            val line = rawLine.trim()
            when {
                line == "BEGIN:VEVENT" -> cur = mutableMapOf()
                line == "END:VEVENT" -> {
                    cur?.let { events.add(build(it)) }
                    cur = null
                }
                cur != null && line.contains(":") -> {
                    val idx = line.indexOf(":")
                    val left = line.substring(0, idx)
                    val value = line.substring(idx + 1)
                    val parts = left.split(";")
                    val name = parts[0].uppercase()
                    val params = parts.drop(1).mapNotNull {
                        val kv = it.split("=", limit = 2)
                        if (kv.size == 2) kv[0].uppercase() to kv[1] else null
                    }.toMap()

                    val m = cur!!
                    when (name) {
                        "UID" -> m["uid"] = value
                        "SUMMARY" -> m["summary"] = unescape(value)
                        "LOCATION" -> m["loc"] = unescape(value)
                        "DTSTART" -> {
                            val (ms, allDay) = parseDate(value, params["TZID"])
                            m["start"] = ms
                            m["allDay"] = allDay
                        }
                        "DTEND" -> {
                            val (ms, _) = parseDate(value, params["TZID"])
                            m["end"] = ms
                        }
                        "GEO" -> {
                            val g = value.split(";")
                            if (g.size == 2) {
                                val la = g[0].toDoubleOrNull()
                                val lo = g[1].toDoubleOrNull()
                                if (la != null && lo != null) {
                                    m["lat"] = la
                                    m["lon"] = lo
                                }
                            }
                        }
                    }
                }
            }
        }
        return events
    }

    private fun build(m: Map<String, Any?>): Event {
        val summary = m["summary"] as? String ?: "(ohne Titel)"
        val start = m["start"] as? Long
        return Event(
            uid = (m["uid"] as? String)?.takeIf { it.isNotBlank() } ?: (summary + (start?.toString() ?: "")),
            summary = summary,
            startMillis = start,
            endMillis = m["end"] as? Long,
            allDay = m["allDay"] as? Boolean ?: false,
            locText = m["loc"] as? String ?: "",
            lat = m["lat"] as? Double,
            lon = m["lon"] as? Double
        )
    }

    private fun parseDate(value: String, tzid: String?): Pair<Long?, Boolean> {
        val match = DT.find(value) ?: return null to false
        val g = match.groupValues // index 1..7
        val y = g[1]; val mo = g[2]; val d = g[3]
        val h = g[4]; val mi = g[5]; val s = g[6]; val z = g[7]
        return try {
            if (h.isEmpty()) {
                val ld = LocalDate.of(y.toInt(), mo.toInt(), d.toInt())
                ld.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() to true
            } else {
                val ldt = LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), s.toInt())
                val instant = when {
                    z == "Z" -> ldt.toInstant(ZoneOffset.UTC)
                    tzid != null -> ldt.atZone(
                        runCatching { ZoneId.of(tzid) }.getOrDefault(ZoneId.systemDefault())
                    ).toInstant()
                    else -> ldt.atZone(ZoneId.systemDefault()).toInstant()
                }
                instant.toEpochMilli() to false
            }
        } catch (e: Exception) {
            null to false
        }
    }

    private fun unescape(v: String): String =
        v.replace("\\,", ",").replace("\\;", ";").replace("\\n", ", ").replace("\\N", ", ")
}
