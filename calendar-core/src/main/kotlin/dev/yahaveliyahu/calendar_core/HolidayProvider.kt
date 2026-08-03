package dev.yahaveliyahu.calendar_core

import java.time.DayOfWeek
import java.time.LocalDate

data class HolidayInfo(
    val name: String,
    val hebrewName: String,
    val date: LocalDate,
    val providerId: String,
    val colorHint: Int? = null
)

interface HolidayProvider {
    val id: String
    val displayName: String
    fun holidaysInRange(start: LocalDate, end: LocalDate): List<HolidayInfo>
}

/** One canonical, year-independent holiday type (e.g. "Purim" with no date attached to it) --
 *  lets a host UI offer a "pick a holiday" list without re-deriving names from a resolved date
 *  range. Use [HolidayProvider.holidaysInRange] over a wide-enough window to resolve an actual
 *  occurrence of a given definition. */
data class HolidayDefinition(val name: String, val hebrewName: String, val providerId: String)

class HolidayRegistry {
    private val active = LinkedHashSet<HolidayProvider>()

    fun enable(vararg providers: HolidayProvider) { active.addAll(providers) }
    fun disable(provider: HolidayProvider) { active.remove(provider) }
    fun isEnabled(provider: HolidayProvider): Boolean = provider in active
    fun enabledProviders(): List<HolidayProvider> = active.toList()

    fun holidaysFor(start: LocalDate, end: LocalDate): List<HolidayInfo> =
        active.flatMap { it.holidaysInRange(start, end) }.sortedBy { it.date }
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
 * Only Israel practice is modeled here -- e.g. no diaspora-only "second day of Yom Tov" for
 * Pesach/Sukkot/Shavuot, since this app has no concept of a non-Israel region to show it for.
 */
class JewishHolidayProvider(private val hebrew: HebrewCalendarSystem = HebrewCalendarSystem()) : HolidayProvider {
    override val id = "jewish"
    override val displayName = "Jewish Holidays"

    /** Canonical list of this provider's distinct holiday types, independent of any particular
     *  year -- same names emitted by [holidaysInRange], just without a resolved date. */
    fun definitions(): List<HolidayDefinition> = listOf(
        HolidayDefinition("Rosh Hashanah", "ראש השנה", id),
        HolidayDefinition("Rosh Hashanah II", "ראש השנה (יום ב')", id),
        HolidayDefinition("Yom Kippur", "יום כיפור", id),
        HolidayDefinition("Sukkot", "סוכות", id),
        HolidayDefinition("Simchat Torah", "שמחת תורה", id),
        HolidayDefinition("Chanukah (1st candle)", "חנוכה (נר ראשון)", id),
        HolidayDefinition("Tu BiShvat", "ט\"ו בשבט", id),
        HolidayDefinition("Purim", "פורים", id),
        HolidayDefinition("Pesach (Passover)", "פסח", id),
        HolidayDefinition("Seventh Day of Pesach", "שביעי של פסח", id),
        HolidayDefinition("Shavuot", "שבועות", id),
        HolidayDefinition("Yom HaShoah", "יום השואה", id),
        HolidayDefinition("Yom HaZikaron", "יום הזיכרון", id),
        HolidayDefinition("Yom Ha'atzmaut", "יום העצמאות", id),
        HolidayDefinition("Yom Yerushalayim", "יום ירושלים", id),
        HolidayDefinition("Tzom Gedaliah", "צום גדליה", id),
        HolidayDefinition("17th of Tammuz (fast)", "צום י\"ז בתמוז", id),
        HolidayDefinition("Tisha B'Av", "תשעה באב", id),
        HolidayDefinition("Tu B'Av", "ט\"ו באב", id),
        HolidayDefinition("Asara B'Tevet", "עשרה בטבת", id),
        HolidayDefinition("Ta'anit Esther", "תענית אסתר", id)
    )

    override fun holidaysInRange(start: LocalDate, end: LocalDate): List<HolidayInfo> {
        val results = mutableListOf<HolidayInfo>()
        val startYear = hebrew.toHebrewDate(start).year - 1
        val endYear = hebrew.toHebrewDate(end).year + 1

        for (year in startYear..endYear) {
            fun add(name: String, hebrewName: String, month: HebrewMonth, day: Int) {
                val date = hebrew.toGregorian(HebrewDate(year, month, day))
                if (!date.isBefore(start) && !date.isAfter(end)) {
                    results += HolidayInfo(name, hebrewName, date, id)
                }
            }
            fun addFixed(name: String, hebrewName: String, date: LocalDate) {
                if (!date.isBefore(start) && !date.isAfter(end)) {
                    results += HolidayInfo(name, hebrewName, date, id)
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
            add("Seventh Day of Pesach", "שביעי של פסח", HebrewMonth.NISAN, 21)
            add("Shavuot", "שבועות", HebrewMonth.SIVAN, 6)

            addFixed("Yom HaShoah", "יום השואה", yomHaShoahDate(year))
            val atzmaut = yomHaatzmautDate(year)
            addFixed("Yom HaZikaron", "יום הזיכרון", atzmaut.minusDays(1))
            addFixed("Yom Ha'atzmaut", "יום העצמאות", atzmaut)
            add("Yom Yerushalayim", "יום ירושלים", HebrewMonth.IYAR, 28)

            addFixed("Tzom Gedaliah", "צום גדליה", deferMinorFast(hebrew.toGregorian(HebrewDate(year, HebrewMonth.TISHREI, 3))))
            addFixed("17th of Tammuz (fast)", "צום י\"ז בתמוז", deferMinorFast(hebrew.toGregorian(HebrewDate(year, HebrewMonth.TAMMUZ, 17))))
            addFixed("Tisha B'Av", "תשעה באב", deferMinorFast(hebrew.toGregorian(HebrewDate(year, HebrewMonth.AV, 9))))
            add("Tu B'Av", "ט\"ו באב", HebrewMonth.AV, 15)
            addFixed("Asara B'Tevet", "עשרה בטבת", deferMinorFast(hebrew.toGregorian(HebrewDate(year, HebrewMonth.TEVET, 10))))
            addFixed("Ta'anit Esther", "תענית אסתר", deferTaanitEsther(hebrew.toGregorian(HebrewDate(year, purimMonth, 13))))
        }
        return results
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

    /** Canonical list of this provider's distinct holiday types, independent of any particular
     *  year -- same names emitted by [holidaysInRange], just without a resolved date. */
    fun definitions(): List<HolidayDefinition> = listOf(
        HolidayDefinition("Christmas", "חג המולד", id),
        HolidayDefinition("Epiphany", "חג ההתגלות", id),
        HolidayDefinition("Ash Wednesday", "יום רביעי האפר", id),
        HolidayDefinition("Palm Sunday", "יום ראשון של הדקל", id),
        HolidayDefinition("Good Friday", "יום שישי הטוב", id),
        HolidayDefinition("Easter Sunday", "חג הפסחא", id),
        HolidayDefinition("Pentecost", "פנטקוסט (חג השבועות הנוצרי)", id)
    )

    override fun holidaysInRange(start: LocalDate, end: LocalDate): List<HolidayInfo> {
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