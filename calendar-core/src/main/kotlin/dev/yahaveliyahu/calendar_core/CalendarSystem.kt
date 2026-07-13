package dev.yahaveliyahu.calendar_core

import java.time.LocalDate
import java.util.Locale

/** A date expressed in some non-Gregorian calendar system, for display purposes.
 *  [dayLabel] and [yearLabel] are what actually get drawn -- each [CalendarSystem]
 *  formats its own way (Gregorian: "27" / "2026", Hebrew: "כ״ז" / "תשפ״ו"). */
data class SystemDate(val year: Int, val month: Int, val day: Int, val monthName: String, val dayLabel: String, val yearLabel: String)

/**
 * Pluggable calendar system. The view always tracks the *real* date as a [LocalDate]
 * internally (Gregorian/ISO) and only asks the active [CalendarSystem] how to *label*
 * each day. Swapping calendarSystem on the view re-labels the grid without touching
 * any stored event data.
 */
interface CalendarSystem {
    val id: String
    val displayName: String
    fun labelFor(date: LocalDate, locale: Locale = Locale.getDefault()): SystemDate

    /** First and last Gregorian date of the "month" (in THIS system's terms) containing [date].
     *  For Gregorian this is just the calendar month; for Hebrew it's the Hebrew month, which
     *  rarely lines up with Gregorian month boundaries -- that's the whole point of this method. */
    fun monthBounds(date: LocalDate): Pair<LocalDate, LocalDate>

    /** The Gregorian date of day 1 of the month [delta] months away (in this system's terms). */
    fun shiftMonths(date: LocalDate, delta: Int): LocalDate
}

class GregorianCalendarSystem : CalendarSystem {
    override val id = "gregorian"
    override val displayName = "Gregorian"

    override fun labelFor(date: LocalDate, locale: Locale): SystemDate {
        val monthName = date.month.getDisplayName(java.time.format.TextStyle.FULL, locale)
        return SystemDate(date.year, date.monthValue, date.dayOfMonth, monthName, date.dayOfMonth.toString(), date.year.toString())
    }

    override fun monthBounds(date: LocalDate): Pair<LocalDate, LocalDate> {
        val ym = java.time.YearMonth.from(date)
        return ym.atDay(1) to ym.atEndOfMonth()
    }

    override fun shiftMonths(date: LocalDate, delta: Int): LocalDate =
        java.time.YearMonth.from(date).plusMonths(delta.toLong()).atDay(1)
}

class HebrewCalendarSystem : CalendarSystem {
    override val id = "hebrew"
    override val displayName = "Hebrew"

    fun toHebrewDate(date: LocalDate): HebrewDate {
        val ed = date.toEpochDay()
        var year = date.year + 3760
        while (HebrewMath.epochDayOfTishrei1(year + 1) <= ed) year++
        while (HebrewMath.epochDayOfTishrei1(year) > ed) year--

        var remaining = (ed - HebrewMath.epochDayOfTishrei1(year) + 1).toInt()
        for ((month, length) in HebrewMath.monthLengths(year)) {
            if (remaining <= length) return HebrewDate(year, month, remaining)
            remaining -= length
        }
        error("Unreachable: day overflowed Hebrew year $year")
    }

    fun toGregorian(hebrewDate: HebrewDate): LocalDate {
        var daysBefore = 0
        for ((month, length) in HebrewMath.monthLengths(hebrewDate.year)) {
            if (month == hebrewDate.month) break
            daysBefore += length
        }
        val ed = HebrewMath.epochDayOfTishrei1(hebrewDate.year) + daysBefore + (hebrewDate.day - 1)
        return LocalDate.ofEpochDay(ed)
    }

    fun isLeapYear(year: Int): Boolean = HebrewMath.isLeapYear(year)

    override fun labelFor(date: LocalDate, locale: Locale): SystemDate {
        val hd = toHebrewDate(date)
        return SystemDate(hd.year, hd.month.ordinal + 1, hd.day, monthDisplayName(hd.month, locale), hebrewNumeral(hd.day), hebrewYearLabel(hd.year))
    }

    override fun monthBounds(date: LocalDate): Pair<LocalDate, LocalDate> {
        val hd = toHebrewDate(date)
        val length = HebrewMath.monthLengths(hd.year).first { it.first == hd.month }.second
        return toGregorian(HebrewDate(hd.year, hd.month, 1)) to toGregorian(HebrewDate(hd.year, hd.month, length))
    }

    override fun shiftMonths(date: LocalDate, delta: Int): LocalDate {
        val hd = toHebrewDate(date)
        var year = hd.year
        var months = HebrewMath.monthLengths(year).map { it.first }
        var index = months.indexOf(hd.month)
        var remaining = delta
        while (remaining > 0) {
            index++
            if (index >= months.size) {
                year++; months = HebrewMath.monthLengths(year).map { it.first }; index = 0
            }
            remaining--
        }
        while (remaining < 0) {
            index--
            if (index < 0) {
                year--; months = HebrewMath.monthLengths(year).map { it.first }; index = months.size - 1
            }
            remaining++
        }
        return toGregorian(HebrewDate(year, months[index], 1))
    }

    /** Traditional Hebrew letter-numeral (gematria) for a day-of-month, e.g. 27 -> "כ״ז". */
    private fun hebrewNumeral(day: Int): String = hebrewLetters(day)

    /** Hebrew year as letters, dropping the thousands digit per universal convention --
     *  5786 is written תשפ״ו everywhere (calendars, invitations, gravestones), never with
     *  a "5000" component spelled out. */
    private fun hebrewYearLabel(year: Int): String = hebrewLetters(year % 1000)

    /** Shared gematria formatter for 1-999: hundreds (ק/ר/ש/ת, chained for 500+), then
     *  tens+ones, with the same ט״ו / ט״ז substitution for 15/16 applied to the final
     *  two digits regardless of any hundreds prefix -- the literal letter combinations
     *  spell out a name of God, so every Hebrew numeral avoids them, not just day numbers. */
    private fun hebrewLetters(value: Int): String {
        var n = value
        val letters = StringBuilder()
        val hundredValues = intArrayOf(400, 300, 200, 100)
        val hundredLetters = arrayOf("ת", "ש", "ר", "ק")
        while (n >= 100) {
            for (i in hundredValues.indices) {
                if (n >= hundredValues[i]) {
                    letters.append(hundredLetters[i]); n -= hundredValues[i]; break
                }
            }
        }
        when (n) {
            15 -> letters.append("טו")
            16 -> letters.append("טז")
            else -> {
                val tens = listOf("", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ")
                val ones = listOf("", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט")
                letters.append(tens[n / 10]).append(ones[n % 10])
            }
        }
        val result = letters.toString()
        return if (result.length <= 1) "$result׳" else result.dropLast(1) + "״" + result.takeLast(1)
    }

    private fun monthDisplayName(month: HebrewMonth, locale: Locale): String {
        val hebrew = locale.language == "iw" || locale.language == "he"
        return when (month) {
            HebrewMonth.TISHREI -> if (hebrew) "תשרי" else "Tishrei"
            HebrewMonth.CHESHVAN -> if (hebrew) "חשוון" else "Cheshvan"
            HebrewMonth.KISLEV -> if (hebrew) "כסלו" else "Kislev"
            HebrewMonth.TEVET -> if (hebrew) "טבת" else "Tevet"
            HebrewMonth.SHEVAT -> if (hebrew) "שבט" else "Shevat"
            HebrewMonth.ADAR -> if (hebrew) "אדר" else "Adar"
            HebrewMonth.ADAR_II -> if (hebrew) "אדר ב׳" else "Adar II"
            HebrewMonth.NISAN -> if (hebrew) "ניסן" else "Nisan"
            HebrewMonth.IYAR -> if (hebrew) "אייר" else "Iyar"
            HebrewMonth.SIVAN -> if (hebrew) "סיוון" else "Sivan"
            HebrewMonth.TAMMUZ -> if (hebrew) "תמוז" else "Tammuz"
            HebrewMonth.AV -> if (hebrew) "אב" else "Av"
            HebrewMonth.ELUL -> if (hebrew) "אלול" else "Elul"
        }
    }
}