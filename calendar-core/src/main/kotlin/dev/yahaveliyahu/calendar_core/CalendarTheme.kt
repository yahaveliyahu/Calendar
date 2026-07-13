package dev.yahaveliyahu.calendar_core


enum class CellShape { CIRCLE, ROUNDED_SQUARE }
enum class CalendarViewMode { DAY, WEEK, MONTH, YEAR }
enum class SelectionMode { SINGLE, MULTIPLE, RANGE }

/**
 * Everything about how the calendar LOOKS. This is the object the reference app's
 * Theme Studio edits live and exports -- it never touches event data.
 * All colors are ARGB ints (same format as android.graphics.Color) so this class has
 * zero Android dependency and stays testable on plain JVM.
 */
data class CalendarTheme(
    val primaryColor: Int = 0xFF3F51B5.toInt(),
    val todayIndicatorColor: Int = 0xFFFF4081.toInt(),
    val selectedDayColor: Int = 0xFF3F51B5.toInt(),
    val fridayTextColor: Int = 0xFF1E88E5.toInt(),
    val saturdayTextColor: Int = 0xFFE53935.toInt(),
    val defaultTextColor: Int = 0xFF212121.toInt(),
    val holidayDotColor: Int = 0xFFFFC107.toInt(),
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),
    val dayTextSizeSp: Float = 16f,
    val headerTextSizeSp: Float = 14f,
    val cellShape: CellShape = CellShape.CIRCLE,
    val fontFamily: String? = null,
    val isDarkMode: Boolean = false
) {
    companion object {
        val LIGHT_DEFAULT = CalendarTheme()
        val DARK_DEFAULT = CalendarTheme(
            primaryColor = 0xFF7986CB.toInt(),
            defaultTextColor = 0xFFECECEC.toInt(),
            fridayTextColor = 0xFF90CAF9.toInt(),
            saturdayTextColor = 0xFFEF9A9A.toInt(),
            backgroundColor = 0xFF121212.toInt(),
            isDarkMode = true
        )
    }
}

/** Which calendar system + holiday sources + interaction mode the view should use. */
data class CalendarConfig(
    val primaryCalendarSystem: CalendarSystem = GregorianCalendarSystem(),
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val selectionMode: SelectionMode = SelectionMode.SINGLE,
    val firstDayOfWeek: java.time.DayOfWeek = java.time.DayOfWeek.SUNDAY,
    val region: Region = Region.ISRAEL
)

/** A user event/task on the calendar. Per-event [color] is what makes overlapping
 *  events on the same day render distinctly (point #5). Emoji is just Unicode text
 *  in [title] -- no special rendering logic needed (point #6). */
data class CalendarEvent(
    val id: String,
    val title: String,
    val start: java.time.Instant,
    val end: java.time.Instant? = null,
    val color: Int,
    val reminderMinutesBefore: List<Int> = emptyList(),
    val attachmentUris: List<String> = emptyList(),
    val isDeleted: Boolean = false,
    val deletedAt: java.time.Instant? = null
)
