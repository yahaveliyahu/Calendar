package dev.yahaveliyahu.calendar_core

import java.time.DayOfWeek
import java.time.LocalDate

data class HolidayInfo(
    val name: String,
    val hebrewName: String,
    val date: LocalDate,
    val providerId: String,
    val colorHint: Int? = null,
    val isDiasporaOnly: Boolean = false
)

interface HolidayProvider {
    val id: String
    val displayName: String
    fun holidaysInRange(start: LocalDate, end: LocalDate, region: Region = Region.DIASPORA): List<HolidayInfo>
}

enum class Region { ISRAEL, DIASPORA }

class HolidayRegistry {
    private val active = LinkedHashSet<HolidayProvider>()

    fun enable(vararg providers: HolidayProvider) { active.addAll(providers) }
    fun disable(provider: HolidayProvider) { active.remove(provider) }
    fun isEnabled(provider: HolidayProvider): Boolean = provider in active
    fun enabledProviders(): List<HolidayProvider> = active.toList()

    fun holidaysFor(start: LocalDate, end: LocalDate, region: Region = Region.DIASPORA): List<HolidayInfo> =
        active.flatMap { it.holidaysInRange(start, end, region) }.sortedBy { it.date }
}

/**
 * Jewish holidays and fasts, computed from the real Hebrew calendar (not a hardcoded date
 * table) -- including the ones with genuine, non-trivial deferral rules:
 *  - Minor fasts (Tzom Gedaliah, 17 Tammuz, Tisha B'Av, Asara B'Tevet) defer to SUNDAY if
 *    they'd fall on Shabbat.
 *  - Ta'anit Esther uniquely defers BACKWARD to the preceding Thursday if it'd fall on
 *    Shabbat, since it can't be delayed past Purim itself.
 *  - Yom Ha'atzmaut / Yom HaZikaron: 5/4 Iyar can only ever land on Mon/Wed/Fri/Sat (a
 *    mathematical consequence of the Hebrew calendar's own postponement rules) and are
 *    shifted to avoid Shabbat: Mon -> Tue, Fri or Sat -> Thu, Wed is unchanged.
 * All deferral rules verified against Hebcal, Chabad.org, and OU.org, checked 2026-07-13.
 */
class JewishHolidayProvider(private val hebrew: HebrewCalendarSystem = HebrewCalendarSystem()) : HolidayProvider {
    override val id = "jewish"
    override val displayName = "Jewish Holidays"

    override fun holidaysInRange(start: LocalDate, end: LocalDate, region: Region): List<HolidayInfo> {
        val results = mutableListOf<HolidayInfo>()
        val startYear = hebrew.toHebrewDate(start).year - 1
        val endYear = hebrew.toHebrewDate(end).year + 1

        for (year in startYear..endYear) {
            fun add(name: String, hebrewName: String, month: HebrewMonth, day: Int, diasporaOnly: Boolean = false) {
                val date = hebrew.toGregorian(HebrewDate(year, month, day))
                if (!date.isBefore(start) && !date.isAfter(end)) {
                    results += HolidayInfo(name, hebrewName, date, id, isDiasporaOnly = diasporaOnly)
                }
            }
            fun addFixed(name: String, hebrewName: String, date: LocalDate, diasporaOnly: Boolean = false) {
                if (!date.isBefore(start) && !date.isAfter(end)) {
                    results += HolidayInfo(name, hebrewName, date, id, isDiasporaOnly = diasporaOnly)
                }
            }

            val purimMonth = if (hebrew.isLeapYear(year)) HebrewMonth.ADAR_II else HebrewMonth.ADAR

            add("Rosh Hashanah", "ראש השנה", HebrewMonth.TISHREI, 1)
            add("Rosh Hashanah II", "ראש השנה (יום ב')", HebrewMonth.TISHREI, 2)
            add("Yom Kippur", "יום כיפור", HebrewMonth.TISHREI, 10)
            add("Sukkot", "סוכות", HebrewMonth.TISHREI, 15)
            add("Simchat Torah", "שמחת תורה", HebrewMonth.TISHREI, 22)
            add("Chanukah (1st candle)", "חנוכה (נר ראשון)", HebrewMonth.KISLEV, 25)
            add("Tu BiShvat", "ט\"ו בשבט", HebrewMonth.SHEVAT, 15)
            add("Purim", "פורים", purimMonth, 14)
            add("Pesach (Passover)", "פסח", HebrewMonth.NISAN, 15)
            add("Pesach II", "פסח (יום ב')", HebrewMonth.NISAN, 16, diasporaOnly = true)
            add("Shavuot", "שבועות", HebrewMonth.SIVAN, 6)

            addFixed("Yom HaShoah", "יום השואה", yomHaShoahDate(year))
            val atzmaut = yomHaatzmautDate(year)
            addFixed("Yom HaZikaron", "יום הזיכרון", atzmaut.minusDays(1))
            addFixed("Yom Ha'atzmaut", "יום העצמאות", atzmaut)
            add("Yom Yerushalayim", "יום ירושלים", HebrewMonth.IYAR, 28)

            addFixed("Tzom Gedaliah", "צום גדליה", deferMinorFast(hebrew.toGregorian(HebrewDate(year, HebrewMonth.TISHREI, 3))))
            addFixed("17th of Tammuz (fast)", "צום י\"ז בתמוז", deferMinorFast(hebrew.toGregorian(HebrewDate(year, HebrewMonth.TAMMUZ, 17))))
            addFixed("Tisha B'Av", "תשעה באב", deferMinorFast(hebrew.toGregorian(HebrewDate(year, HebrewMonth.AV, 9))))
            addFixed("Asara B'Tevet", "עשרה בטבת", deferMinorFast(hebrew.toGregorian(HebrewDate(year, HebrewMonth.TEVET, 10))))
            addFixed("Ta'anit Esther", "תענית אסתר", deferTaanitEsther(hebrew.toGregorian(HebrewDate(year, purimMonth, 13))))
        }
        return if (region == Region.ISRAEL) results.filterNot { it.isDiasporaOnly } else results
    }

    private fun deferMinorFast(normative: LocalDate): LocalDate =
        if (normative.dayOfWeek == DayOfWeek.SATURDAY) normative.plusDays(1) else normative

    private fun deferTaanitEsther(normative: LocalDate): LocalDate =
        if (normative.dayOfWeek == DayOfWeek.SATURDAY) normative.minusDays(2) else normative

    private fun yomHaShoahDate(year: Int): LocalDate {
        val normative = hebrew.toGregorian(HebrewDate(year, HebrewMonth.NISAN, 27))
        return when (normative.dayOfWeek) {
            DayOfWeek.FRIDAY -> normative.minusDays(1)
            DayOfWeek.SUNDAY -> normative.plusDays(1)
            else -> normative
        }
    }

    private fun yomHaatzmautDate(year: Int): LocalDate {
        val normative = hebrew.toGregorian(HebrewDate(year, HebrewMonth.IYAR, 5))
        return when (normative.dayOfWeek) {
            DayOfWeek.MONDAY -> normative.plusDays(1)
            DayOfWeek.FRIDAY -> normative.minusDays(1)
            DayOfWeek.SATURDAY -> normative.minusDays(2)
            else -> normative
        }
    }
}

class ChristianHolidayProvider : HolidayProvider {
    override val id = "christian"
    override val displayName = "Christian Holidays"

    override fun holidaysInRange(start: LocalDate, end: LocalDate, region: Region): List<HolidayInfo> {
        val results = mutableListOf<HolidayInfo>()
        for (year in start.year..end.year) {
            fun add(name: String, hebrewName: String, date: LocalDate) {
                if (!date.isBefore(start) && !date.isAfter(end)) results += HolidayInfo(name, hebrewName, date, id)
            }
            add("Christmas", "חג המולד", LocalDate.of(year, 12, 25))
            add("Epiphany", "חג ההתגלות", LocalDate.of(year, 1, 6))

            val easter = westernEaster(year)
            add("Ash Wednesday", "יום רביעי האפר", easter.minusDays(46))
            add("Palm Sunday", "יום ראשון של הדקל", easter.minusDays(7))
            add("Good Friday", "יום שישי הטוב", easter.minusDays(2))
            add("Easter Sunday", "חג הפסחא", easter)
            add("Pentecost", "פנטקוסט (חג השבועות הנוצרי)", easter.plusDays(49))
        }
        return results
    }

    private fun westernEaster(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }
}