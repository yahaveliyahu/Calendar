package dev.yahaveliyahu.calendar_core

import java.time.LocalDate

/**
 * Fixed-arithmetic Hebrew calendar calculations: molad (mean lunar conjunction) of Tishrei,
 * the four dechiyot (postponement rules) that fix Rosh Hashanah to a valid weekday, the
 * 19-year (Metonic) leap-year cycle, and month-length tables.
 *
 * every other computed date is independently checked against known reference dates in
 * HebrewCalendarSystemTest.
 */
internal object HebrewMath {
    private const val MONTHS_PER_CYCLE = 235L       // 19-year Metonic cycle = 235 lunar months
    private const val YEARS_PER_CYCLE = 19L

    fun isLeapYear(year: Int): Boolean = (7L * year + 1L) % 19L < 7L

    /** Lunar months elapsed from Tishrei of year 1 up to (not including) Tishrei of [year]. */
    private fun monthsElapsed(year: Int): Long {
        val y = (year - 1).toLong()
        val cycles = y / YEARS_PER_CYCLE
        val remainder = y % YEARS_PER_CYCLE
        return MONTHS_PER_CYCLE * cycles + 12L * remainder + (7L * remainder + 1L) / YEARS_PER_CYCLE
    }

    /**
     * Internal (uncalibrated) day number of 1 Tishrei of [year], after applying the four
     * dechiyot postponement rules to the mean molad.
     */
    private fun tishreiDayInternal(year: Int): Long {
        val months = monthsElapsed(year)
        val partsElapsed = 204L + 793L * (months % 1080L)
        val hoursElapsed = 5L + 12L * months + 793L * (months / 1080L) + partsElapsed / 1080L
        val conjunctionDay = 1L + 29L * months + hoursElapsed / 24L
        val conjunctionParts = 1080L * (hoursElapsed % 24L) + partsElapsed % 1080L

        var altDay = conjunctionDay
        val leapThisYear = isLeapYear(year)
        val leapPrevYear = isLeapYear(year - 1)

        // postpone Rosh Hashanah by a day if the molad falls too late in the day,
        // or on a Tuesday/Monday under conditions that would otherwise misalign following years.
        if (conjunctionParts >= 19440L ||
            (conjunctionDay % 7L == 2L && conjunctionParts >= 9924L && !leapThisYear) ||
            (conjunctionDay % 7L == 1L && conjunctionParts >= 16789L && leapPrevYear)
        ) {
            altDay += 1L
        }
        // Rosh Hashanah may never fall on Sunday, Wednesday or Friday.
        if (altDay % 7L == 0L || altDay % 7L == 3L || altDay % 7L == 5L) {
            altDay += 1L
        }
        return altDay
    }

    // Calibrated once against a verified source (Hebcal): 1 Tishrei 5786 = 2025-09-23.
    // If the algorithm above is correct, this single offset must make every other
    // reference date line up too -- which is what HebrewCalendarSystemTest checks.
    private val CALIBRATION_OFFSET: Long by lazy {
        LocalDate.of(2025, 9, 23).toEpochDay() - tishreiDayInternal(5786)
    }

    fun epochDayOfTishrei1(year: Int): Long = tishreiDayInternal(year) + CALIBRATION_OFFSET

    fun yearLengthDays(year: Int): Int =
        (tishreiDayInternal(year + 1) - tishreiDayInternal(year)).toInt()

    /** Ordered (month, lengthInDays) pairs for a given Hebrew year, civil order starting at Tishrei. */
    fun monthLengths(year: Int): List<Pair<HebrewMonth, Int>> {
        val leap = isLeapYear(year)
        val len = yearLengthDays(year)
        val cheshvanLong = len == 355 || len == 385   // "complete" year
        val kislevShort = len == 353 || len == 383    // "deficient" year

        val months = ArrayList<Pair<HebrewMonth, Int>>(if (leap) 13 else 12)
        months.add(HebrewMonth.TISHREI to 30)
        months.add(HebrewMonth.CHESHVAN to if (cheshvanLong) 30 else 29)
        months.add(HebrewMonth.KISLEV to if (kislevShort) 29 else 30)
        months.add(HebrewMonth.TEVET to 29)
        months.add(HebrewMonth.SHEVAT to 30)
        if (leap) {
            months.add(HebrewMonth.ADAR to 30)     // Adar I
            months.add(HebrewMonth.ADAR_II to 29)  // Adar II (carries Purim etc.)
        } else {
            months.add(HebrewMonth.ADAR to 29)
        }
        months.add(HebrewMonth.NISAN to 30)
        months.add(HebrewMonth.IYAR to 29)
        months.add(HebrewMonth.SIVAN to 30)
        months.add(HebrewMonth.TAMMUZ to 29)
        months.add(HebrewMonth.AV to 30)
        months.add(HebrewMonth.ELUL to 29)
        return months
    }
}
