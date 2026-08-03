package dev.yahaveliyahu.calendar

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.yahaveliyahu.calendar_core.CalendarEvent
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * "שתף" on the event detail screen: writes a real RFC 5545 (.ics) file for the event to a
 * private cache folder, then launches the standard Android share sheet with a content:// URI
 * for it (via the FileProvider declared in AndroidManifest.xml) -- this is what lets an app
 * like WhatsApp recognize the attachment as an actual calendar event rather than plain text.
 */
object EventIcsSharer {
    private val ICS_TIMESTAMP_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    fun share(context: Context, event: CalendarEvent) {
        val file = writeIcsFile(context, event)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "שיתוף אירוע").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private const val SHARE_APP_NAME = "MyCalendar"

    private fun writeIcsFile(context: Context, event: CalendarEvent): File {
        val dir = File(context.cacheDir, "shared_events").apply { mkdirs() }
        // Clear previous shares before writing this one -- since the filename is now based on
        // the event's title rather than its id, leaving old files around would just let stale,
        // differently-named .ics files for the same event (from before a rename) pile up.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "${sanitizeForFilename(event.title)} $SHARE_APP_NAME.ics")
        file.writeText(buildIcsContent(event), Charsets.UTF_8)
        return file
    }

    /** Strips characters that are invalid (or just awkward) in a filename, and keeps it to a
     *  sane length -- an event title is free-form text, so this can't assume it's already
     *  filename-safe. Falls back to a generic label if the title is blank or becomes blank
     *  after stripping. */
    private fun sanitizeForFilename(title: String): String {
        val cleaned = title.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), " ").trim()
        val truncated = cleaned.take(50).trim()
        return truncated.ifBlank { "אירוע" }
    }

    private fun buildIcsContent(event: CalendarEvent): String {
        val dtStamp = ICS_TIMESTAMP_FORMAT.format(Instant.now())
        val dtStart = ICS_TIMESTAMP_FORMAT.format(event.start)
        val dtEndLine = event.end?.let { "DTEND:${ICS_TIMESTAMP_FORMAT.format(it)}\r\n" } ?: ""
        val locationLine = if (event.location.isNotBlank()) "LOCATION:${escapeIcsText(event.location)}\r\n" else ""
        val descriptionLine = if (event.notes.isNotBlank()) "DESCRIPTION:${escapeIcsText(event.notes)}\r\n" else ""

        return buildString {
            append("BEGIN:VCALENDAR\r\n")
            append("VERSION:2.0\r\n")
            append("PRODID:-//dev.yahaveliyahu.calendar//EN\r\n")
            append("BEGIN:VEVENT\r\n")
            append("UID:${event.id}@dev.yahaveliyahu.calendar\r\n")
            append("DTSTAMP:$dtStamp\r\n")
            append("DTSTART:$dtStart\r\n")
            append(dtEndLine)
            append("SUMMARY:${escapeIcsText(event.title)}\r\n")
            append(locationLine)
            append(descriptionLine)
            append("END:VEVENT\r\n")
            append("END:VCALENDAR\r\n")
        }
    }

    /** RFC 5545's TEXT value escaping: backslash, semicolon, comma, and newline all need a
     *  backslash prefix (bare CR is just dropped, since \r\n line endings are handled at the
     *  line level already, not as in-value content). */
    private fun escapeIcsText(text: String): String =
        text
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
            .replace("\r", "")
}