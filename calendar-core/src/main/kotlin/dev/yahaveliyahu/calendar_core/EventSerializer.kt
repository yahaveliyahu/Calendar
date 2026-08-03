package dev.yahaveliyahu.calendar_core

import java.time.Instant

/**
 * Hand-rolled JSON for a list of CalendarEvent -- same "no external JSON dependency" reasoning
 * as ThemeSerializer. Unlike theme fields (all numbers/enums), event titles are free text
 * (including emoji), so this one needs real string escaping -- a single-pass character
 * scanner, not sequential String.replace() calls, to avoid escaping-order bugs.
 *
 * Note: [CalendarEvent.attachmentUris] and [CalendarEvent.deletedAt] are not round-tripped
 * -- no UI currently sets them, so they'd always serialize as empty/null anyway. If a host
 * app starts using them, extend toJson/fromJson to include them.
 */
object EventSerializer {

    fun toJson(events: List<CalendarEvent>): String = buildString {
        append("[")
        events.forEachIndexed { index, e ->
            if (index > 0) append(",")
            append("{")
            append("\"id\":\"${escape(e.id)}\",")
            append("\"title\":\"${escape(e.title)}\",")
            append("\"start\":${e.start.toEpochMilli()},")
            append("\"end\":${e.end?.toEpochMilli() ?: "null"},")
            append("\"color\":${e.color},")
            append("\"reminderMinutesBefore\":[${e.reminderMinutesBefore.joinToString(",")}],")
            append("\"isDeleted\":${e.isDeleted},")
            append("\"location\":\"${escape(e.location)}\",")
            append("\"notes\":\"${escape(e.notes)}\"")
            append("}")
        }
        append("]")
    }

    fun fromJson(json: String): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        splitObjects(json).forEach { obj ->
            fun field(name: String): String? =
                Regex("\"$name\"\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|\\[[^\\]]*\\]|[^,}]+)").find(obj)?.groupValues?.get(1)

            fun stringField(name: String): String? = field(name)?.let {
                if (it.startsWith("\"") && it.endsWith("\"")) unescape(it.substring(1, it.length - 1)) else null
            }

            val id = stringField("id") ?: return@forEach
            val title = stringField("title") ?: ""
            val startMillis = field("start")?.toLongOrNull() ?: return@forEach
            val endRaw = field("end")
            val endMillis = if (endRaw == null || endRaw == "null") null else endRaw.toLongOrNull()
            val color = field("color")?.toIntOrNull() ?: 0
            val reminders = field("reminderMinutesBefore")
                ?.removeSurrounding("[", "]")
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?: emptyList()
            val isDeleted = field("isDeleted") == "true"
            val location = stringField("location") ?: ""
            val notes = stringField("notes") ?: ""

            events += CalendarEvent(
                id = id,
                title = title,
                start = Instant.ofEpochMilli(startMillis),
                end = endMillis?.let { Instant.ofEpochMilli(it) },
                color = color,
                reminderMinutesBefore = reminders,
                isDeleted = isDeleted,
                location = location,
                notes = notes
            )
        }
        return events
    }

    private fun splitObjects(json: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inEscape = false
        for (i in json.indices) {
            val c = json[i]
            if (inEscape) {
                inEscape = false
                continue
            }
            when (c) {
                '\\' -> inEscape = true
                '{' -> { if (depth == 0) start = i; depth++ }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        objects += json.substring(start, i + 1)
                        start = -1
                    }
                }
            }
        }
        return objects
    }

    private fun escape(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '{' -> sb.append("\\{")
                '}' -> sb.append("\\}")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> { sb.append('\\'); i += 2 }
                    '"' -> { sb.append('"'); i += 2 }
                    '{' -> { sb.append('{'); i += 2 }
                    '}' -> { sb.append('}'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    else -> { sb.append(c); i += 1 }
                }
            } else {
                sb.append(c); i += 1
            }
        }
        return sb.toString()
    }
}