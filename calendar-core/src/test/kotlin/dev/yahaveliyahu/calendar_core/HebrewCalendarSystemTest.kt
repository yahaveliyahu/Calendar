package dev.yahaveliyahu.calendar_core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Reference dates verified against Hebcal (hebcal.com) and Chabad.org on 2026-07-11.
 * These are the same checks originally run as a plain-main() harness (JUnit wasn't
 * resolvable in the offline sandbox this project was authored in); see
 * docs/ALGORITHM_VALIDATION.md for that original run's output.
 */
class HebrewCalendarSystemTest {

    private val hebrew = HebrewCalendarSystem()

    @Test
    fun `1 Tishrei maps to the correct Gregorian date across several years`() {
        assertEquals(LocalDate.of(2025, 9, 23), hebrew.toGregorian(HebrewDate(5786, HebrewMonth.TISHREI, 1)))
        assertEquals(LocalDate.of(2026, 9, 12), hebrew.toGregorian(HebrewDate(5787, HebrewMonth.TISHREI, 1)))
        assertEquals(LocalDate.of(2027, 10, 2), hebrew.toGregorian(HebrewDate(5788, HebrewMonth.TISHREI, 1)))
        assertEquals(LocalDate.of(2028, 9, 21), hebrew.toGregorian(HebrewDate(5789, HebrewMonth.TISHREI, 1)))
        assertEquals(LocalDate.of(2029, 9, 10), hebrew.toGregorian(HebrewDate(5790, HebrewMonth.TISHREI, 1)))
    }

    @Test
    fun `leap year detection matches the 19-year Metonic cycle`() {
        assertFalse(hebrew.isLeapYear(5786))
        assertTrue(hebrew.isLeapYear(5787))
        assertFalse(hebrew.isLeapYear(5788))
        assertFalse(hebrew.isLeapYear(5789))
        assertTrue(hebrew.isLeapYear(5790))
    }

    @Test
    fun `year lengths match deficient-regular-complete classification`() {
        assertEquals(354, HebrewMath.yearLengthDays(5786)) // common regular
        assertEquals(385, HebrewMath.yearLengthDays(5787)) // leap complete
        assertEquals(355, HebrewMath.yearLengthDays(5788)) // common complete
        assertEquals(354, HebrewMath.yearLengthDays(5789)) // common regular
        assertEquals(383, HebrewMath.yearLengthDays(5790)) // leap deficient
    }

    @Test
    fun `mid-year dates convert correctly in both common and leap years`() {
        assertEquals(LocalDate.of(2026, 3, 2), hebrew.toGregorian(HebrewDate(5786, HebrewMonth.ADAR, 13)))
        assertEquals(LocalDate.of(2026, 6, 5), hebrew.toGregorian(HebrewDate(5786, HebrewMonth.SIVAN, 20)))
        assertEquals(LocalDate.of(2026, 6, 20), hebrew.toGregorian(HebrewDate(5786, HebrewMonth.TAMMUZ, 5)))
        assertEquals(LocalDate.of(2026, 8, 2), hebrew.toGregorian(HebrewDate(5786, HebrewMonth.AV, 19)))
        // 5787 is a leap year, so ADAR here means Adar I
        assertEquals(LocalDate.of(2027, 2, 19), hebrew.toGregorian(HebrewDate(5787, HebrewMonth.ADAR, 12)))
        assertEquals(LocalDate.of(2027, 5, 25), hebrew.toGregorian(HebrewDate(5787, HebrewMonth.IYAR, 18)))
        assertEquals(LocalDate.of(2027, 6, 9), hebrew.toGregorian(HebrewDate(5787, HebrewMonth.SIVAN, 4)))
        assertEquals(LocalDate.of(2027, 7, 22), hebrew.toGregorian(HebrewDate(5787, HebrewMonth.TAMMUZ, 17)))
    }

    @Test
    fun `round trip through Gregorian and back is stable across six years`() {
        var d = LocalDate.of(2024, 1, 1)
        val end = LocalDate.of(2030, 1, 1)
        while (d.isBefore(end)) {
            val hebrewDate = hebrew.toHebrewDate(d)
            assertEquals(d, hebrew.toGregorian(hebrewDate), "round-trip failed for $d")
            d = d.plusDays(37)
        }
    }

    @Test
    fun `Chanukah first candle for 5787 falls on December 5, 2026`() {
        val jewish = JewishHolidayProvider(hebrew)
        val chanukah = jewish
            .holidaysInRange(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 10))
            .first { it.name.startsWith("Chanukah") }
        assertEquals(LocalDate.of(2026, 12, 5), chanukah.date)
    }

    @Test
    fun `Hebrew day-of-month renders as a letter-numeral, not a digit`() {
        // 27 Tammuz 5786 -- the example the user gave directly.
        assertEquals("כ״ז", hebrew.labelFor(LocalDate.of(2026, 7, 12)).dayLabel)
        // 15 and 16 must use the special-case substitution (avoids spelling a name of God).
        assertEquals("ט״ו", hebrew.labelFor(HebrewCalendarSystem().toGregorian(HebrewDate(5786, HebrewMonth.TISHREI, 15))).dayLabel)
        assertEquals("ט״ז", hebrew.labelFor(HebrewCalendarSystem().toGregorian(HebrewDate(5786, HebrewMonth.TISHREI, 16))).dayLabel)
        // Single-letter day numbers get a geresh, not gershayim.
        assertEquals("א׳", hebrew.labelFor(HebrewCalendarSystem().toGregorian(HebrewDate(5786, HebrewMonth.TISHREI, 1))).dayLabel)
        assertEquals("ל׳", hebrew.labelFor(HebrewCalendarSystem().toGregorian(HebrewDate(5786, HebrewMonth.TISHREI, 30))).dayLabel)
    }

    @Test
    fun `Hebrew monthBounds and shiftMonths correctly track real Hebrew month boundaries`() {
        // Tammuz 5786 is a 29-day month running June 16 - July 14, 2026.
        val (start, end) = hebrew.monthBounds(LocalDate.of(2026, 7, 12))
        assertEquals(LocalDate.of(2026, 6, 16), start)
        assertEquals(LocalDate.of(2026, 7, 14), end)

        // Shifting forward from Tammuz lands on Av 1.
        val avStart = hebrew.shiftMonths(LocalDate.of(2026, 7, 12), 1)
        assertEquals(LocalDate.of(2026, 7, 15), avStart)

        // Shifting across the Hebrew NEW YEAR boundary (Elul 5786 -> Tishrei 5787) must work.
        val elul5786First = hebrew.toGregorian(HebrewDate(5786, HebrewMonth.ELUL, 1))
        val tishrei5787First = hebrew.shiftMonths(elul5786First, 1)
        assertEquals(LocalDate.of(2026, 9, 12), tishrei5787First) // matches the verified 1 Tishrei 5787 date

        // And backward across that same boundary must return to exactly where we started.
        assertEquals(elul5786First, hebrew.shiftMonths(tishrei5787First, -1))

        // 5787 is a leap year (13 months) -- walking through Shevat -> Adar I -> Adar II -> Nisan
        // must actually pass through BOTH Adars, not skip one.
        val shevat5787First = hebrew.toGregorian(HebrewDate(5787, HebrewMonth.SHEVAT, 1))
        assertEquals("Adar", hebrew.labelFor(hebrew.shiftMonths(shevat5787First, 1)).monthName)
        assertEquals("Adar II", hebrew.labelFor(hebrew.shiftMonths(shevat5787First, 2)).monthName)
        assertEquals("Nisan", hebrew.labelFor(hebrew.shiftMonths(shevat5787First, 3)).monthName)
    }

    @Test
    fun `theme JSON round trips exactly`() {
        val theme = CalendarTheme(primaryColor = -0x9A6314, isDarkMode = true, dayTextSizeSp = 15.5f)
        val restored = ThemeSerializer.fromJson(ThemeSerializer.toJson(theme))
        assertEquals(theme, restored)
    }
}