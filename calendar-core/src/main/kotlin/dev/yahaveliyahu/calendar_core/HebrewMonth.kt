package dev.yahaveliyahu.calendar_core


/**
 * Hebrew calendar months in civil-year order (starting from Tishrei, the month of Rosh Hashanah).
 * ADAR represents "Adar I" in leap years and the sole "Adar" in common years.
 * ADAR_II only ever appears in leap years.
 */
enum class HebrewMonth {
    TISHREI, CHESHVAN, KISLEV, TEVET, SHEVAT, ADAR, ADAR_II,
    NISAN, IYAR, SIVAN, TAMMUZ, AV, ELUL
}

data class HebrewDate(val year: Int, val month: HebrewMonth, val day: Int)
