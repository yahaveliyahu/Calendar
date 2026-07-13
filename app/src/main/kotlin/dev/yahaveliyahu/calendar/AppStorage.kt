package dev.yahaveliyahu.calendar

import android.content.Context
import dev.yahaveliyahu.calendar_core.CalendarEvent
import dev.yahaveliyahu.calendar_core.CalendarTheme
import dev.yahaveliyahu.calendar_core.EventSerializer
import dev.yahaveliyahu.calendar_core.ThemeSerializer

/**
 * Simple SharedPreferences-based persistence -- deliberately not Room. This app's data is
 * one theme object and a modest event list, not something that needs SQL querying, so a
 * single JSON blob per category (reusing calendar-core's existing serializers) is the
 * lightest correct tool. If events grow into the thousands, that's the point to move to Room.
 */
object AppStorage {
    private const val PREFS_NAME = "calendar_theme_studio"
    private const val KEY_THEME = "theme_json"
    private const val KEY_EVENTS = "events_json"
    private const val KEY_USE_HEBREW = "use_hebrew_primary"
    private const val KEY_JEWISH_HOLIDAYS = "jewish_holidays_on"
    private const val KEY_CHRISTIAN_HOLIDAYS = "christian_holidays_on"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveTheme(context: Context, theme: CalendarTheme) {
        prefs(context).edit().putString(KEY_THEME, ThemeSerializer.toJson(theme)).apply()
    }

    fun loadTheme(context: Context): CalendarTheme {
        val json = prefs(context).getString(KEY_THEME, null) ?: return CalendarTheme.LIGHT_DEFAULT
        return runCatching { ThemeSerializer.fromJson(json) }.getOrDefault(CalendarTheme.LIGHT_DEFAULT)
    }

    fun saveEvents(context: Context, events: List<CalendarEvent>) {
        prefs(context).edit().putString(KEY_EVENTS, EventSerializer.toJson(events)).apply()
    }

    fun loadEvents(context: Context): List<CalendarEvent> {
        val json = prefs(context).getString(KEY_EVENTS, null) ?: return emptyList()
        return runCatching { EventSerializer.fromJson(json) }.getOrDefault(emptyList())
    }

    fun saveToggles(context: Context, useHebrewPrimary: Boolean, jewishHolidaysOn: Boolean, christianHolidaysOn: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_USE_HEBREW, useHebrewPrimary)
            .putBoolean(KEY_JEWISH_HOLIDAYS, jewishHolidaysOn)
            .putBoolean(KEY_CHRISTIAN_HOLIDAYS, christianHolidaysOn)
            .apply()
    }

    fun loadUseHebrewPrimary(context: Context): Boolean = prefs(context).getBoolean(KEY_USE_HEBREW, false)
    fun loadJewishHolidaysOn(context: Context): Boolean = prefs(context).getBoolean(KEY_JEWISH_HOLIDAYS, true)
    fun loadChristianHolidaysOn(context: Context): Boolean = prefs(context).getBoolean(KEY_CHRISTIAN_HOLIDAYS, false)
}