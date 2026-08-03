package dev.yahaveliyahu.calendar

import android.content.Context
import androidx.core.content.edit
import dev.yahaveliyahu.calendar_core.CalendarEvent
import dev.yahaveliyahu.calendar_core.CalendarTheme
import dev.yahaveliyahu.calendar_core.EventSerializer
import dev.yahaveliyahu.calendar_core.ThemeSerializer

/**
 * Simple SharedPreferences-based persistence - deliberately not Room. This app's data is
 * one theme object and a modest event list, not something that needs SQL querying, so a
 * single JSON blob per category (reusing calendar-core's existing serializers) is the
 * lightest correct tool.
 */
object AppStorage {
    private const val PREFS_NAME = "calendar_theme_studio"
    private const val KEY_THEME = "theme_json"
    private const val KEY_EVENTS = "events_json"
    private const val KEY_USE_HEBREW = "use_hebrew_primary"
    private const val KEY_JEWISH_HOLIDAYS = "jewish_holidays_on"
    private const val KEY_CHRISTIAN_HOLIDAYS = "christian_holidays_on"
    private const val KEY_LAST_HOLIDAY_NOTIFICATION_DATE = "last_holiday_notification_date"
    private const val KEY_NOTIFIED_HOLIDAY_NAMES_TODAY = "notified_holiday_names_today"
    private const val KEY_LAST_JUMP_GREGORIAN_MONTH_INDEX = "last_jump_gregorian_month_index"
    private const val KEY_LAST_JUMP_GREGORIAN_YEAR_TEXT = "last_jump_gregorian_year_text"
    private const val KEY_LAST_JUMP_HEBREW_MONTH_INDEX = "last_jump_hebrew_month_index"
    private const val KEY_LAST_JUMP_HEBREW_YEAR_TEXT = "last_jump_hebrew_year_text"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveTheme(context: Context, theme: CalendarTheme) {
        prefs(context).edit { putString(KEY_THEME, ThemeSerializer.toJson(theme)) }
    }

    fun loadTheme(context: Context): CalendarTheme {
        val json = prefs(context).getString(KEY_THEME, null) ?: return CalendarTheme.LIGHT_DEFAULT
        return runCatching { ThemeSerializer.fromJson(json) }.getOrDefault(CalendarTheme.LIGHT_DEFAULT)
    }

    fun saveEvents(context: Context, events: List<CalendarEvent>) {
        prefs(context).edit { putString(KEY_EVENTS, EventSerializer.toJson(events)) }
    }

    fun loadEvents(context: Context): List<CalendarEvent> {
        val json = prefs(context).getString(KEY_EVENTS, null) ?: return emptyList()
        return runCatching { EventSerializer.fromJson(json) }.getOrDefault(emptyList())
    }

    fun saveToggles(context: Context, useHebrewPrimary: Boolean, jewishHolidaysOn: Boolean, christianHolidaysOn: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_USE_HEBREW, useHebrewPrimary)
            putBoolean(KEY_JEWISH_HOLIDAYS, jewishHolidaysOn)
            putBoolean(KEY_CHRISTIAN_HOLIDAYS, christianHolidaysOn)
        }
    }

    fun loadUseHebrewPrimary(context: Context): Boolean = prefs(context).getBoolean(KEY_USE_HEBREW, false)
    fun loadJewishHolidaysOn(context: Context): Boolean = prefs(context).getBoolean(KEY_JEWISH_HOLIDAYS, true)
    fun loadChristianHolidaysOn(context: Context): Boolean = prefs(context).getBoolean(KEY_CHRISTIAN_HOLIDAYS, false)

    /** Which holiday names (by their internal, non-localized [name][dev.yahaveliyahu.calendar_core.HolidayInfo.name])
     *  have already gotten a "chag sameach" notification today -- reset whenever the stored
     *  date stops being today, so re-opening the app later the same day doesn't repeat one. */
    fun loadLastHolidayNotificationDate(context: Context): String? =
        prefs(context).getString(KEY_LAST_HOLIDAY_NOTIFICATION_DATE, null)

    fun loadNotifiedHolidayNamesToday(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_NOTIFIED_HOLIDAY_NAMES_TODAY, emptySet()) ?: emptySet()

    fun saveHolidayNotificationState(context: Context, isoDate: String, notifiedNames: Set<String>) {
        prefs(context).edit {
            putString(KEY_LAST_HOLIDAY_NOTIFICATION_DATE, isoDate)
            putStringSet(KEY_NOTIFIED_HOLIDAY_NAMES_TODAY, notifiedNames)
        }
    }

    /** Remembers the month/year last entered in the Time Jump screen's "Gregorian" section
     *  (as soon as "הצג" is pressed there), so reopening the screen later shows the same
     *  selection instead of resetting to today. [yearText] is stored verbatim (already
     *  validated as a real year by the time this is called) rather than re-parsed. */
    fun saveLastGregorianJump(context: Context, monthIndex: Int, yearText: String) {
        prefs(context).edit {
            putInt(KEY_LAST_JUMP_GREGORIAN_MONTH_INDEX, monthIndex)
            putString(KEY_LAST_JUMP_GREGORIAN_YEAR_TEXT, yearText)
        }
    }

    /** Returns (monthIndex, yearText), or null if nothing's been jumped to yet this way. */
    fun loadLastGregorianJump(context: Context): Pair<Int, String>? {
        val monthIndex = prefs(context).getInt(KEY_LAST_JUMP_GREGORIAN_MONTH_INDEX, -1)
        val yearText = prefs(context).getString(KEY_LAST_JUMP_GREGORIAN_YEAR_TEXT, null)
        return if (monthIndex >= 0 && yearText != null) monthIndex to yearText else null
    }

    /** Same idea as [saveLastGregorianJump], for the "Hebrew" section. */
    fun saveLastHebrewJump(context: Context, monthIndex: Int, yearText: String) {
        prefs(context).edit {
            putInt(KEY_LAST_JUMP_HEBREW_MONTH_INDEX, monthIndex)
            putString(KEY_LAST_JUMP_HEBREW_YEAR_TEXT, yearText)
        }
    }

    /** Returns (monthIndex, yearText), or null if nothing's been jumped to yet this way. */
    fun loadLastHebrewJump(context: Context): Pair<Int, String>? {
        val monthIndex = prefs(context).getInt(KEY_LAST_JUMP_HEBREW_MONTH_INDEX, -1)
        val yearText = prefs(context).getString(KEY_LAST_JUMP_HEBREW_YEAR_TEXT, null)
        return if (monthIndex >= 0 && yearText != null) monthIndex to yearText else null
    }
}