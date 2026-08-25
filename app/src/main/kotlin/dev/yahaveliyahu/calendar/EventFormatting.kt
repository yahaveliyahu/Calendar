package dev.yahaveliyahu.calendar

import dev.yahaveliyahu.calendar_core.CalendarSystem
import dev.yahaveliyahu.calendar_core.HebrewCalendarSystem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime


fun eventFormWeekdayLabel(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.SUNDAY -> "יום א׳"
    DayOfWeek.MONDAY -> "יום ב׳"
    DayOfWeek.TUESDAY -> "יום ג׳"
    DayOfWeek.WEDNESDAY -> "יום ד׳"
    DayOfWeek.THURSDAY -> "יום ה׳"
    DayOfWeek.FRIDAY -> "יום ו׳"
    DayOfWeek.SATURDAY -> "שבת"
}

/** Formats [date] the way it's actually written in whichever calendar system is currently
 *  primary -- numeric day.month.2-digit-year for Gregorian (how it's commonly written in
 *  Israel, e.g. "11.8.26"), or Hebrew day-letters + "ב" + Hebrew month name + Hebrew
 *  year-letters for Hebrew (e.g. "כ״ח באב ה׳תשפ״ו") -- rather than always formatting as
 *  Gregorian regardless of which system the app is actually displaying. Reuses
 *  [CalendarSystem.labelFor] for the Hebrew case instead of re-deriving Hebrew letters here,
 *  since that's the exact same logic the calendar grid itself already draws with. */
fun formattedDateLabel(date: LocalDate, primarySystem: CalendarSystem): String {
    val weekday = eventFormWeekdayLabel(date.dayOfWeek)
    val datePart = if (primarySystem is HebrewCalendarSystem) {
        val label = primarySystem.labelFor(date)
        "${label.dayLabel} ב${label.monthName} ${label.yearLabel}"
    } else {
        "${date.dayOfMonth}.${date.monthValue}.${date.year % 100}"
    }
    return "$weekday, $datePart"
}

fun formattedTimeLabel(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)